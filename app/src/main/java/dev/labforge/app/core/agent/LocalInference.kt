package dev.labforge.app.core.agent

import dev.labforge.app.core.native.GenerationCallback
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.settings.AppSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer

internal object LocalInference {
    suspend fun chat(cfg: AppSettings, messages: JSONArray, tools: JSONArray?, delta: ((String, Boolean) -> Unit)?): LlmReply {
        val context = currentCoroutineContext()
        context.ensureActive()
        val conversation = JSONArray()
        if (tools != null && tools.length() > 0) conversation.put(JSONObject().put("role", "system").put("content",
            "You can use these tools: $tools\nFor a tool call, reply ONLY with JSON: " +
                "{\"tool_calls\":[{\"name\":\"tool_name\",\"arguments\":{}}]}. Otherwise reply normally."))
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role")
            val content = if (m.has("tool_calls")) m.getJSONArray("tool_calls").toString()
                else if (m.isNull("content")) "" else m.optString("content")
            conversation.put(JSONObject().put("role", if (role == "tool") "user" else role)
                .put("content", if (role == "tool") "Tool result: $content" else content))
        }
        val output = ByteArrayOutputStream()
        var emitted = 0
        val result = LlamaBridge.generate(cfg.localModelPath, conversation.toString(), cfg.localAdapterPath,
            cfg.localContext, cfg.temperature.toFloat(), object : GenerationCallback {
                override fun onToken(bytes: ByteArray): Boolean {
                    if (!context.isActive) return false
                    output.write(bytes)
                    // Decode only complete UTF-8 sequences; tokens can split a code point.
                    if (tools == null || tools.length() == 0) {
                        val chars = CharBuffer.allocate(output.size() + 1)
                        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray()), chars, false)
                        chars.flip()
                        val text = chars.toString()
                        if (text.length > emitted) delta?.invoke(text.substring(emitted), false)
                        emitted = text.length
                    }
                    return true
                }
            }, threads = cfg.localThreads.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors())
        context.ensureActive()
        val root = JSONObject(result)
        check(!root.has("error")) { root.optString("error") }
        val reply = parse(root.optString("content"))
        if (tools != null && tools.length() > 0 && reply.content.isNotBlank()) delta?.invoke(reply.content, false)
        return reply.copy(promptTokens = root.optInt("prompt_tokens"), completionTokens = root.optInt("completion_tokens"))
    }

    internal fun parse(raw: String): LlmReply {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = runCatching { JSONObject(trimmed) }.getOrNull()
        val calls = obj?.optJSONArray("tool_calls") ?: return LlmReply(raw, emptyList())
        return LlmReply(obj.optString("content"), (0 until calls.length()).map { index ->
            val c = calls.getJSONObject(index)
            val fn = c.optJSONObject("function") ?: c
            val args = fn.opt("arguments")
            LlmToolCall(c.optString("id", "local_$index"), fn.getString("name"), args?.toString() ?: "{}")
        })
    }
}
