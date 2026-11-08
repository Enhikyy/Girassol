package dev.labforge.app.core.agent

import android.util.Log
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.native.NativeChatCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * llama.cpp-backed implementation of the same tool-calling protocol as [LlmClient].
 * Tool calls are emitted as JSON by the local model and executed by the shared ReAct loop.
 */
class NativeLlmClient(
    private val modelPath: String,
    private val nCtx: Int,
    private val threads: Int,
    private val maxTokens: Int = 384,
    private val temperature: Float = 0.25f,
) : AgentLlm {

    override suspend fun chat(
        messages: JSONArray,
        tools: JSONArray?,
        onDelta: ((text: String, reasoning: Boolean) -> Unit)?,
    ): LlmReply = withContext(Dispatchers.IO) {
        val system = messages.optJSONObject(0)?.optString("content").orEmpty()
        val toolCatalog = tools?.let(::compactToolCatalog).orEmpty()
        val promptCharBudget = ((nCtx - maxTokens - 96).coerceAtLeast(96) * 2.5).toInt()
        val systemBudget = (promptCharBudget * 0.58).toInt().coerceAtLeast(160)
        val userBudget = (promptCharBudget - systemBudget).coerceAtLeast(128)
        val systemBase = system.take((systemBudget * 0.62).toInt())
        val systemWithTools = buildString {
            append(systemBase)
            if (toolCatalog.isNotBlank()) {
                append("\n\nFERRAMENTAS (nome(args): descrição):\n")
                append(toolCatalog.take((systemBudget - systemBase.length - 48).coerceAtLeast(0)))
                append("\nUse ferramenta respondendo com JSON: ")
                append("{\"tool_calls\":[{\"name\":\"nome_exato\",\"arguments\":{}}],\"content\":\"\"}")
                append(". Arguments deve ser objeto JSON; aguarde resultados antes de continuar.")
            }
        }

        val transcript = buildString {
            for (i in 1 until messages.length()) {
                val m = messages.optJSONObject(i) ?: continue
                val role = m.optString("role")
                val body = m.optString("content").takeIf { it != "null" }.orEmpty()
                when (role) {
                    "user" -> append("\nUSUÁRIO:\n").append(body).append('\n')
                    "assistant" -> {
                        if (body.isNotBlank()) append("\nASSISTENTE:\n").append(body).append('\n')
                        m.optJSONArray("tool_calls")?.let {
                            append("CHAMADAS SOLICITADAS:\n").append(it.toString()).append('\n')
                        }
                    }
                    "tool" -> append("\nRESULTADO DA FERRAMENTA:\n").append(body).append('\n')
                }
            }
            append("\nASSISTENTE:")
        }.takeLast(userBudget)


        val output = StringBuilder()
        val result = LlamaBridge.generateLocal(
            model = modelPath,
            systemPrompt = systemWithTools,
            userPrompt = transcript,
            nCtx = nCtx,
            threads = threads,
            maxTokens = maxTokens,
            temperature = temperature,
            callback = object : NativeChatCallback {
                override fun onToken(piece: String) {
                    output.append(piece)
                    onDelta?.invoke(piece, false)
                }

                override fun onLog(line: String) {
                    Log.i("GirassolNativeChat", line)
                }

                override fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double) = Unit
            },
        )
        if (!result.ok) throw IllegalStateException(result.error ?: "inferência local falhou")
        val raw = output.toString().trim()
        val parsed = parseToolResponse(raw)
        LlmReply(
            content = parsed.content,
            toolCalls = parsed.calls,
            reasoning = parsed.reasoning,
            promptTokens = result.promptTokens,
            completionTokens = result.completionTokens,
        )
    }

    private fun compactToolCatalog(tools: JSONArray): String = buildString {
        for (i in 0 until tools.length()) {
            val spec = tools.optJSONObject(i) ?: continue
            val fn = spec.optJSONObject("function") ?: spec
            val name = fn.optString("name")
            if (name.isBlank()) continue
            val description = fn.optString("description").replace(Regex("\\s+"), " ").take(150)
            val properties = fn.optJSONObject("parameters")?.optJSONObject("properties")
            val arguments = properties?.keys()?.asSequence()?.toList()?.joinToString(",").orEmpty()
            append(name).append('(').append(arguments).append("): ").append(description).append('\n')
        }
    }

    private data class ParsedToolResponse(
        val content: String,
        val calls: List<LlmToolCall>,
        val reasoning: String,
    )

    private fun parseToolResponse(raw: String): ParsedToolResponse {
        val thought = Regex("(?s)<think>(.*?)</think>").findAll(raw)
            .joinToString("\n") { it.groupValues[1].trim() }
        val noThought = Regex("(?s)<think>.*?</think>").replace(raw, "").trim()
        val candidate = noThought
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val jsonText = if (candidate.startsWith("{")) candidate else {
            val first = candidate.indexOf('{')
            val last = candidate.lastIndexOf('}')
            if (first >= 0 && last > first) candidate.substring(first, last + 1) else ""
        }
        val root = runCatching { JSONObject(jsonText) }.getOrNull()
            ?: return ParsedToolResponse(noThought, emptyList(), thought)

        val array = root.optJSONArray("tool_calls")
        val calls = ArrayList<LlmToolCall>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val fn = item.optJSONObject("function")
                val name = item.optString("name").ifBlank { fn?.optString("name").orEmpty() }
                if (name.isBlank()) continue
                val rawArgs = item.opt("arguments") ?: item.opt("parameters") ?: fn?.opt("arguments")
                val args = when (rawArgs) {
                    is JSONObject -> rawArgs.toString()
                    is String -> rawArgs.ifBlank { "{}" }
                    else -> "{}"
                }
                calls += LlmToolCall(item.optString("id").ifBlank { "local_call_$i" }, name, args)
            }
        }
        val content = root.optString("content")
            .ifBlank { root.optString("response") }
            .ifBlank { if (calls.isEmpty()) noThought else "" }
        return ParsedToolResponse(content, calls, thought)
    }
}
