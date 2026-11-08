package dev.labforge.app

import dev.labforge.app.core.agent.LocalChatPromptBuilder
import dev.labforge.app.core.agent.LocalChatTurn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalChatPromptTest {
    @Test
    fun formatsConversationRolesForNativeTemplate() {
        val result = LocalChatPromptBuilder.transcript(
            listOf(LocalChatTurn("user", "olá"), LocalChatTurn("assistant", "oi")),
        )
        assertTrue(result.contains("USUÁRIO:\nolá"))
        assertTrue(result.contains("ASSISTENTE:\noi"))
        assertFalse(result.endsWith("ASSISTENTE:"))
    }

    @Test
    fun keepsOnlyNewestTurns() {
        val turns = (1..20).map { LocalChatTurn(if (it % 2 == 0) "assistant" else "user", "mensagem-" + it) }
        val result = LocalChatPromptBuilder.transcript(turns, maxTurns = 4)
        assertTrue(result.contains("mensagem-17"))
        assertTrue(result.contains("mensagem-20"))
        assertFalse(result.contains("mensagem-16"))
    }

    @Test
    fun appliesCharacterBudgetAndDropsUnsupportedRoles() {
        val result = LocalChatPromptBuilder.transcript(
            listOf(
                LocalChatTurn("tool", "não entra"),
                LocalChatTurn("user", "x".repeat(600)),
                LocalChatTurn("assistant", "resposta"),
            ),
            maxChars = 180,
        )
        assertTrue(result.length <= 180)
        assertFalse(result.contains("não entra"))
        assertTrue(result.contains("resposta"))
    }
}
