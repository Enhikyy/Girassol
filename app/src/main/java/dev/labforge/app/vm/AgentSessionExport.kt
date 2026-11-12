package dev.labforge.app.vm

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Structured export of the current user-visible session, never hidden runtime state. */
object AgentSessionExport {
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+")
    private val keyedSecret = Regex(
        "(?i)(\\\"?(?:api[_-]?key|authorization|access[_-]?token|refresh[_-]?token|password|secret|token)\\\"?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}&]+)",
    )

    fun create(
        messages: List<AgentMessage>,
        running: Boolean,
        status: String,
        startedAt: Long,
        elapsedMs: Long,
        promptTokens: Int,
        completionTokens: Int,
        localPromptTokens: Int,
        tokensPerSecond: Double,
        exportedAt: Instant = Instant.now(),
    ): String {
        val messageArray = JSONArray()
        messages.forEachIndexed { index, message ->
            messageArray.put(JSONObject().apply {
                put("sequence", index + 1)
                put("id", message.id)
                put("role", message.role.name)
                put("text", redact(message.text))
                put("pending", message.pending)
                if (message.role == AgentMessage.Role.TOOL) {
                    put("tool", JSONObject().apply {
                        put("name", message.toolName)
                        put("arguments", redact(message.toolArgs))
                        put("ok", message.ok)
                    })
                }
            })
        }
        val thoughts = JSONArray()
        messages.filter { it.role == AgentMessage.Role.THOUGHT }.forEach { message ->
            thoughts.put(JSONObject().apply {
                put("messageId", message.id)
                put("text", redact(message.text))
            })
        }
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("kind", "girassol.agent-session")
            put("exportedAt", exportedAt.toString())
            put("traceScope", "user-visible messages only")
            put("redaction", "known credential fields and bearer tokens")
            put("session", JSONObject().apply {
                put("running", running)
                put("status", status)
                put("startedAt", if (startedAt > 0) Instant.ofEpochMilli(startedAt).toString() else JSONObject.NULL)
                put("elapsedMs", elapsedMs)
                put("messageCount", messages.size)
                put("thoughtCount", thoughts.length())
            })
            put("metrics", JSONObject().apply {
                put("promptTokens", promptTokens)
                put("completionTokens", completionTokens)
                put("localPromptTokens", localPromptTokens)
                put("tokensPerSecond", tokensPerSecond.takeIf { it.isFinite() && it >= 0 } ?: 0.0)
            })
            put("messages", messageArray)
            put("thoughtLog", thoughts)
        }.toString(2)
    }

    internal fun redact(text: String): String = text
        .replace(bearer, "Bearer [REDACTED]")
        .replace(keyedSecret) { match -> "${match.groupValues[1]}\"[REDACTED]\"" }
}
