package dev.labforge.app.vm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AgentSessionExportTest {
    @Test
    fun exportsVisibleThoughtsToolsAndRealMetrics() {
        val messages = listOf(
            AgentMessage(1, AgentMessage.Role.USER, "Analise o projeto"),
            AgentMessage(2, AgentMessage.Role.THOUGHT, "Vou inspecionar os arquivos"),
            AgentMessage(3, AgentMessage.Role.TOOL, "2 arquivos", toolName = "read_file", toolArgs = "{\"path\":\"src\"}"),
            AgentMessage(4, AgentMessage.Role.FINAL, "Concluído"),
        )
        val json = JSONObject(AgentSessionExport.create(
            messages = messages,
            running = false,
            status = "pronto",
            startedAt = 1_000L,
            elapsedMs = 2_500L,
            promptTokens = 20,
            completionTokens = 10,
            localPromptTokens = 18,
            tokensPerSecond = 4.0,
            exportedAt = Instant.parse("2026-01-02T03:04:05Z"),
        ))

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("2026-01-02T03:04:05Z", json.getString("exportedAt"))
        assertEquals(4, json.getJSONArray("messages").length())
        assertEquals(1, json.getJSONArray("thoughtLog").length())
        assertEquals("Vou inspecionar os arquivos", json.getJSONArray("thoughtLog").getJSONObject(0).getString("text"))
        assertEquals("read_file", json.getJSONArray("messages").getJSONObject(2).getJSONObject("tool").getString("name"))
        assertEquals(4.0, json.getJSONObject("metrics").getDouble("tokensPerSecond"), 0.0)
        assertFalse(json.has("settings"))
        assertFalse(json.has("apiKey"))
    }

    @Test
    fun redactsCommonCredentialsWithoutDroppingVisibleEvents() {
        val json = AgentSessionExport.create(
            messages = listOf(
                AgentMessage(1, AgentMessage.Role.USER, "Use Authorization: Bearer abc.def-123"),
                AgentMessage(2, AgentMessage.Role.TOOL, "ok", toolName = "request", toolArgs = "{\"apiKey\":\"secret-value\",\"path\":\"README.md\"}"),
            ),
            running = false,
            status = "pronto",
            startedAt = 0L,
            elapsedMs = 0L,
            promptTokens = 0,
            completionTokens = 0,
            localPromptTokens = 0,
            tokensPerSecond = Double.NaN,
            exportedAt = Instant.EPOCH,
        )

        assertFalse(json.contains("abc.def-123"))
        assertFalse(json.contains("secret-value"))
        assertTrue(json.contains("[REDACTED]"))
        assertTrue(json.contains("README.md"))
        assertEquals(0.0, JSONObject(json).getJSONObject("metrics").getDouble("tokensPerSecond"), 0.0)
    }
}
