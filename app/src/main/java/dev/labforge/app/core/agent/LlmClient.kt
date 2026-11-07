package dev.labforge.app.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LlmToolCall(val id: String, val name: String, val arguments: String)

data class LlmReply(
    val content: String,
    val toolCalls: List<LlmToolCall>,
    val reasoning: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

/**
 * OpenAI-compatible client. Works with OpenAI, OpenRouter, Groq, Ollama, LM Studio,
 * llama-server, vLLM etc.
 */
class LlmClient(
    endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val temperature: Double = 0.2,
) {
    private val url: String = normalize(endpoint)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        messages: JSONArray,
        tools: JSONArray?,
        onDelta: ((text: String, reasoning: Boolean) -> Unit)? = null,
    ): LlmReply = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            put("stream", onDelta != null)
            if (onDelta != null) put("stream_options", JSONObject().put("include_usage", true))
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")

        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException(httpError(resp.code, resp.body?.string().orEmpty()))
            }
            if (onDelta != null) {
                val body = resp.body ?: throw IllegalStateException("resposta vazia")
                parseStream(body.source(), onDelta)
            } else {
                parseReply(resp.body?.string().orEmpty())
            }
        }
    }

    private fun parseStream(source: okio.BufferedSource, onDelta: (String, Boolean) -> Unit): LlmReply {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val calls = sortedMapOf<Int, MutableToolCall>()
        var promptTokens = 0
        var completionTokens = 0

        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            if (data.isEmpty()) continue
            val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue

            obj.optJSONObject("usage")?.let {
                promptTokens = it.optInt("prompt_tokens", promptTokens)
                completionTokens = it.optInt("completion_tokens", completionTokens)
            }

            val delta = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
            val r = delta.optString("reasoning_content").ifBlank { delta.optString("reasoning") }
            if (r.isNotEmpty()) {
                reasoning.append(r)
                onDelta(r, true)
            }
            val c = delta.optString("content")
            if (c.isNotEmpty()) {
                content.append(c)
                onDelta(c, false)
            }
            val tc = delta.optJSONArray("tool_calls")
            if (tc != null) {
                for (i in 0 until tc.length()) {
                    val part = tc.optJSONObject(i) ?: continue
                    val index = part.optInt("index", i)
                    val slot = calls.getOrPut(index) { MutableToolCall() }
                    if (part.optString("id").isNotBlank()) slot.id = part.optString("id")
                    val fn = part.optJSONObject("function") ?: continue
                    if (fn.optString("name").isNotBlank()) slot.name = fn.optString("name")
                    slot.args.append(fn.optString("arguments"))
                }
            }
        }

        return LlmReply(
            content = content.toString(),
            toolCalls = calls.values.map {
                LlmToolCall(it.id.ifBlank { "call_${it.name}" }, it.name, it.args.toString().ifBlank { "{}" })
            },
            reasoning = reasoning.toString(),
            promptTokens = promptTokens,
            completionTokens = completionTokens,
        )
    }

    private class MutableToolCall {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val modelsUrl = url.removeSuffix("/chat/completions") + "/models"
        val builder = Request.Builder().url(modelsUrl).get()
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException(httpError(resp.code, body))
            val arr = JSONObject(body).optJSONArray("data")
            val names = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.optString("id") }
            if (names.isEmpty()) "conectado" else "conectado · ${names.take(6).joinToString(", ")}"
        }
    }

    private fun parseReply(body: String): LlmReply {
        val root = JSONObject(body)
        val msg = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("resposta sem choices: ${body.take(300)}")
        val calls = ArrayList<LlmToolCall>()
        val tc = msg.optJSONArray("tool_calls")
        if (tc != null) {
            for (i in 0 until tc.length()) {
                val c = tc.optJSONObject(i) ?: continue
                val fn = c.optJSONObject("function") ?: continue
                calls += LlmToolCall(
                    id = c.optString("id", "call_$i"),
                    name = fn.optString("name"),
                    arguments = fn.optString("arguments", "{}"),
                )
            }
        }
        val usage = root.optJSONObject("usage")
        val raw = msg.optString("content", "")
        val thinkRegex = Regex("(?s) thinking(.*?) response")
        val embedded = thinkRegex.findAll(raw).joinToString("\n") { it.groupValues[1].trim() }
        val clean = raw.replace(thinkRegex, "").trim()
        val explicit = msg.optString("reasoning_content").ifBlank {
            msg.optString("reasoning").ifBlank { msg.optString("thinking") }
        }
        return LlmReply(
            content = clean,
            toolCalls = calls,
            reasoning = listOf(explicit, embedded).filter { it.isNotBlank() }.joinToString("\n"),
            promptTokens = usage?.optInt("prompt_tokens") ?: 0,
            completionTokens = usage?.optInt("completion_tokens") ?: 0,
        )
    }

    private fun httpError(code: Int, body: String): String = when (code) {
        401, 403 -> "API key inválida ou sem permissão ($code). Verifique em Ajustes."
        404 -> "Endpoint/modelo não encontrado (404): $url · ${body.take(200)}"
        429 -> "Limite de requisições atingido (429). Tente novamente em instantes."
        else -> "HTTP $code: ${body.take(400)}"
    }

    companion object {
        fun normalize(raw: String): String {
            var base = raw.trim()
            if (base.isEmpty()) base = "https://api.openai.com/v1"
            base = base.trimEnd('/')
            return when {
                base.endsWith("/chat/completions") -> base
                base.endsWith("/v1") -> "$base/chat/completions"
                base.endsWith("/v3") -> "$base/chat/completions"
                else -> "$base/v1/chat/completions"
            }
        }

        fun describeNormalized(raw: String): String = runCatching { normalize(raw) }.getOrDefault(raw)
    }
}
