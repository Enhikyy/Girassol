package dev.labforge.app.core.train

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    /** Hard limit for the generated UTF-8 text, enforced while streaming to disk. */
    private const val MAX_OUTPUT_CHARS = 64_000_000L

    /** A malformed/hostile JSONL record must not allocate an unbounded String. */
    private const val MAX_JSONL_LINE_CHARS = 1_048_576

    private const val IO_BUFFER_CHARS = 64 * 1024

    private data class BoundedLine(val text: String, val tooLong: Boolean)

    /**
     * Converts JSONL / line-delimited JSON / plain text into the selected training format.
     *
     * Input and output are streamed. The previous implementation called readText() for each
     * plain file and retained the complete 64 MB corpus in a StringBuilder (roughly 128 MB
     * just for UTF-16 characters, before parser/JNI allocations). This implementation keeps
     * only a fixed I/O buffer plus one bounded JSONL record in memory.
     *
     * The final file is written beside the destination and atomically renamed only after
     * the writer closes successfully, so an interrupted import never replaces a good dataset
     * with a half-written one.
     */
    fun build(inputs: List<File>, format: ChatFormat, output: File): Pair<File, DatasetStats> {
        require(inputs.isNotEmpty()) { "Selecione ao menos um arquivo de dataset." }
        val destination = output.canonicalFile
        require(inputs.none { it.canonicalFile == destination }) {
            "O arquivo de saída não pode ser também um dos arquivos de entrada."
        }

        val parent = destination.parentFile
            ?: throw IOException("Diretório de saída inválido.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Não foi possível criar o diretório de saída: $parent")
        }

        val temporary = File(parent, destination.name + ".tmp")
        var documents = 0
        var characters = 0L
        var skipped = 0
        var detected = "texto"
        var remaining = MAX_OUTPUT_CHARS

        try {
            temporary.bufferedWriter(Charsets.UTF_8, IO_BUFFER_CHARS).use { writer ->
                for (input in inputs) {
                    if (remaining <= 0L) break
                    require(input.isFile) { "Arquivo de dataset inexistente: \${input.name}" }

                    val lineDelimited = input.extension.equals("jsonl", true) ||
                        input.extension.equals("json", true)

                    if (!lineDelimited) {
                        val copied = copyPlainText(input, writer, remaining)
                        if (copied.charactersWritten > 0L) {
                            characters += copied.charactersWritten
                            documents++
                            remaining -= copied.charactersWritten
                            if (copied.lastCharacter != '\n' && remaining > 0L) {
                                writer.write('\n'.code)
                                remaining--
                            }
                        }
                        continue
                    }

                    input.bufferedReader(Charsets.UTF_8, IO_BUFFER_CHARS).use { reader ->
                        while (remaining > 0L) {
                            val bounded = readBoundedLine(reader) ?: break
                            if (bounded.tooLong) {
                                skipped++
                                continue
                            }

                            val line = bounded.text.trim()
                            if (line.isEmpty()) continue

                            val rendered = try {
                                val obj = JSONObject(line)
                                when {
                                    obj.has("messages") -> {
                                        detected = "messages"
                                        renderMessages(obj.getJSONArray("messages"), format)
                                    }
                                    obj.has("conversations") -> {
                                        detected = "conversations"
                                        renderConversations(obj.getJSONArray("conversations"), format)
                                    }
                                    obj.has("instruction") -> {
                                        detected = "instruction"
                                        renderInstruction(
                                            obj.optString("instruction"),
                                            obj.optString("input"),
                                            obj.optString("output").ifBlank { obj.optString("response") },
                                            format,
                                        )
                                    }
                                    obj.has("prompt") -> {
                                        detected = "prompt/completion"
                                        renderInstruction(obj.optString("prompt"), "", obj.optString("completion"), format)
                                    }
                                    obj.has("question") -> {
                                        detected = "q&a"
                                        renderInstruction(obj.optString("question"), "", obj.optString("answer"), format)
                                    }
                                    else -> {
                                        skipped++
                                        ""
                                    }
                                }
                            } catch (_: Exception) {
                                skipped++
                                ""
                            }

                            if (rendered.isBlank()) continue

                            val writeLength = minOf(rendered.length.toLong(), remaining).toInt()
                            writer.write(rendered, 0, writeLength)
                            characters += writeLength
                            remaining -= writeLength
                            documents++

                            if (writeLength == rendered.length && rendered.lastOrNull() != '\n' && remaining > 0L) {
                                writer.write('\n'.code)
                                remaining--
                            }
                        }
                    }
                }
            }

            moveIntoPlace(temporary, destination)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }

        return destination to DatasetStats(
            documents = documents,
            characters = characters,
            approxTokens = (characters / 3.6).toLong(),
            skippedLines = skipped,
            detectedFormat = detected,
        )
    }

    private data class CopyResult(val charactersWritten: Long, val lastCharacter: Char?)

    private fun copyPlainText(input: File, writer: BufferedWriter, limit: Long): CopyResult {
        var written = 0L
        var last: Char? = null
        val buffer = CharArray(IO_BUFFER_CHARS)
        input.reader(Charsets.UTF_8).buffered(IO_BUFFER_CHARS).use { reader ->
            while (written < limit) {
                val wanted = minOf(buffer.size.toLong(), limit - written).toInt()
                val count = reader.read(buffer, 0, wanted)
                if (count < 0) break
                if (count == 0) continue
                writer.write(buffer, 0, count)
                written += count
                last = buffer[count - 1]
            }
        }
        return CopyResult(written, last)
    }

    /**
     * Reads at most [MAX_JSONL_LINE_CHARS] characters. If a line exceeds the bound, the rest
     * is discarded up to its newline and the record is reported as tooLong.
     */
    private fun readBoundedLine(reader: BufferedReader): BoundedLine? {
        val result = StringBuilder(minOf(MAX_JSONL_LINE_CHARS, 4096))
        var sawCharacter = false
        var tooLong = false

        while (true) {
            val value = reader.read()
            if (value < 0) {
                if (!sawCharacter) return null
                break
            }
            sawCharacter = true
            val character = value.toChar()
            if (character == '\n') break
            if (character == '\r') {
                reader.mark(1)
                if (reader.read() != '\n'.code) reader.reset()
                break
            }
            if (result.length < MAX_JSONL_LINE_CHARS) result.append(character)
            else tooLong = true
        }

        return BoundedLine(result.toString(), tooLong)
    }

    private fun moveIntoPlace(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
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
