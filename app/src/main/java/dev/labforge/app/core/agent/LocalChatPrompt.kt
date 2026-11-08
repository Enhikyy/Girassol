package dev.labforge.app.core.agent

data class LocalChatTurn(val role: String, val text: String)

/** Keeps a bounded, readable transcript while the GGUF's own chat template stays native. */
object LocalChatPromptBuilder {
    fun transcript(
        turns: List<LocalChatTurn>,
        maxTurns: Int = 12,
        maxChars: Int = 3_000,
    ): String {
        val bounded = turns
            .filter { (it.role == "user" || it.role == "assistant") && it.text.isNotBlank() }
            .takeLast(maxTurns.coerceIn(2, 40))
        val transcript = bounded.joinToString("\n\n") { turn ->
            val role = if (turn.role == "user") "USUÁRIO" else "ASSISTENTE"
            role + ":\n" + turn.text.takeLast(8_000)
        }
        return transcript.takeLast(maxChars.coerceAtLeast(128)) + "\n\nASSISTENTE:"
    }
}
