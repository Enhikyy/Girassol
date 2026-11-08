package dev.labforge.app.core.train

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ChatFormat(val label: String, val hint: String) {
    CHATML("ChatML", "Qwen / Yi / DeepSeek"),
    LLAMA3("Llama 3", "Llama 3.x / 3.2"),
    ALPACA("Alpaca", "Instrução clássica"),
    RAW("Texto puro", "Continuação de texto"),
}

data class DatasetStats(
    val documents: Int,
    val characters: Long,
    val approxTokens: Long,
    val skippedLines: Int,
    val detectedFormat: String,
)

object DatasetBuilder {

    /** Upper bound on the generated corpus so a huge import cannot exhaust the heap. */
    private const val MAX_OUTPUT_CHARS = 64_000_000

    /** Converts JSONL / JSON / plain text into a single training text file. */
    fun build(inputs: List<File>, format: ChatFormat, output: File, maxOutputChars: Int = MAX_OUTPUT_CHARS): Pair<File, DatasetStats> {
        require(maxOutputChars in 1..MAX_OUTPUT_CHARS)
        val sb = StringBuilder()
        var docs = 0
        var chars = 0L
        var skipped = 0
        var detected = "texto"
        var budget = maxOutputChars

        fun appendObject(obj: JSONObject) {
            val rendered = when {
                obj.has("messages") -> { detected = "messages"; renderMessages(obj.getJSONArray("messages"), format) }
                obj.has("conversations") -> { detected = "conversations"; renderConversations(obj.getJSONArray("conversations"), format) }
                obj.has("instruction") -> {
                    detected = "instruction"
                    renderInstruction(obj.optString("instruction"), obj.optString("input"),
                        obj.optString("output").ifBlank { obj.optString("response") }, format)
                }
                obj.has("prompt") -> { detected = "prompt/completion"; renderInstruction(obj.optString("prompt"), "", obj.optString("completion"), format) }
                obj.has("question") -> { detected = "q&a"; renderInstruction(obj.optString("question"), "", obj.optString("answer"), format) }
                else -> { skipped++; return }
            }
            if (rendered.isNotBlank() && budget > 0) {
                // Keep an entire example or omit it; never train on a truncated answer.
                val complete = if (rendered.endsWith("\n")) rendered else "$rendered\n"
                if (complete.length > budget) { skipped++; return }
                sb.append(complete)
                budget -= complete.length
                chars += complete.length
                docs++
            }
        }
        for (input in inputs) {
            if (budget <= 0) break
            val ext = input.extension.lowercase()
            if (ext !in setOf("json", "jsonl")) {
                val text = input.reader().use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8192)
                    while (out.length < budget) {
                        val n = reader.read(buffer, 0, minOf(buffer.size, budget - out.length))
                        if (n < 0) break
                        out.append(buffer, 0, n)
                    }
                    out.toString()
                }
                budget -= text.length
                sb.append(text)
                if (!text.endsWith("\n") && budget > 0) { sb.append('\n'); budget-- }
                chars += text.length
                docs++
                continue
            }
            // LlamaFactory/Alpaca/ShareGPT commonly export pretty-printed JSON arrays.
            val whole = if (ext == "json") {
                require(input.length() <= MAX_OUTPUT_CHARS) { "JSON maior que 64 MB; importe em JSONL" }
                runCatching {
                    val parser = org.json.JSONTokener(input.readText())
                    val value = parser.nextValue()
                    require(parser.nextClean() == '\u0000')
                    value
                }.getOrNull()
            } else null
            when (whole) {
                is JSONArray -> for (i in 0 until whole.length()) {
                    try { appendObject(whole.getJSONObject(i)) } catch (_: org.json.JSONException) { skipped++ }
                }
                is JSONObject -> try { appendObject(whole) } catch (_: org.json.JSONException) { skipped++ }
                else -> input.forEachLine { raw ->
                    if (raw.isNotBlank() && budget > 0) {
                        try { appendObject(JSONObject(raw)) } catch (_: org.json.JSONException) { skipped++ }
                    }
                }
            }
        }

        output.parentFile?.mkdirs()
        output.writeText(sb.toString())
        return output to DatasetStats(
            documents = docs,
            characters = chars,
            approxTokens = (chars / 3.6).toLong(),
            skippedLines = skipped,
            detectedFormat = detected,
        )
    }

    private fun renderMessages(arr: JSONArray, format: ChatFormat): String {
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val role = m.optString("role", "user")
            val content = m.optString("content")
            if (content.isBlank()) continue
            sb.append(turn(format, role, content))
        }
        return if (format == ChatFormat.RAW) sb.toString() else sb.append(eos(format)).toString()
    }

    private fun renderConversations(arr: JSONArray, format: ChatFormat): String {
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val from = m.optString("from", m.optString("role", "user"))
            val role = when (from.lowercase()) {
                "human", "user" -> "user"
                "gpt", "assistant", "bot" -> "assistant"
                else -> "system"
            }
            sb.append(turn(format, role, m.optString("value", m.optString("content"))))
        }
        return if (format == ChatFormat.RAW) sb.toString() else sb.append(eos(format)).toString()
    }

    private fun renderInstruction(instruction: String, input: String, output: String, format: ChatFormat): String {
        if (output.isBlank()) return ""
        return when (format) {
            ChatFormat.CHATML -> buildString {
                append("<|im_start|>user\n"); append(instruction)
                if (input.isNotBlank()) append("\n\n").append(input)
                append("<|im_end|>\n<|im_start|>assistant\n").append(output).append("<|im_end|>\n")
            }
            ChatFormat.LLAMA3 -> buildString {
                append("<|start_header_id|>user<|end_header_id|>\n\n").append(instruction)
                if (input.isNotBlank()) append("\n\n").append(input)
                append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
                append(output).append("<|eot_id|>\n")
            }
            ChatFormat.ALPACA -> buildString {
                append("### Instruction:\n").append(instruction).append("\n\n")
                if (input.isNotBlank()) append("### Input:\n").append(input).append("\n\n")
                append("### Response:\n").append(output).append("\n\n")
            }
            ChatFormat.RAW -> buildString {
                append(instruction)
                if (input.isNotBlank()) append("\n\n").append(input)
                append("\n\n").append(output).append("\n\n")
            }
        }
    }

    private fun turn(format: ChatFormat, role: String, content: String): String = when (format) {
        ChatFormat.CHATML -> "<|im_start|>$role\n$content<|im_end|>\n"
        ChatFormat.LLAMA3 -> "<|start_header_id|>$role<|end_header_id|>\n\n$content<|eot_id|>"
        ChatFormat.ALPACA -> when (role) {
            "system" -> "### System:\n$content\n\n"
            "assistant" -> "### Response:\n$content\n\n"
            else -> "### Instruction:\n$content\n\n"
        }
        ChatFormat.RAW -> "$role: $content\n"
    }

    private fun eos(format: ChatFormat) = when (format) {
        ChatFormat.CHATML, ChatFormat.LLAMA3 -> "\n"
        else -> "\n"
    }
}
