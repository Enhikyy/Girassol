package dev.labforge.app.core.sleep

import dev.labforge.app.core.agent.LocalInference
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.memory.MemoryConsolidator
import dev.labforge.app.core.term.ShellSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class NativeAgentTest {
    @Test fun parsesNativeToolCallsAndPreservesUnicode() {
        val reply = LocalInference.parse("""{"tool_calls":[{"name":"android_control","arguments":{"action":"text","text":"ação 🌻"}}]}""")
        assertEquals("android_control", reply.toolCalls.single().name)
        assertEquals("ação 🌻", JSONObject(reply.toolCalls.single().arguments).getString("text"))
        assertTrue(reply.content.isEmpty())
        assertEquals("Olá 🌻", LocalInference.parse("Olá 🌻").content)
    }

    @Test fun consolidationExcludesFailuresAndKeepsReplayStable() {
        val dir = Files.createTempDirectory("girassol-memory").toFile()
        try {
            val lessons = listOf(
                LessonStore.Lesson("a", "Como compilar?", "Use Gradle.", listOf("build"), 1),
                LessonStore.Lesson("b", "Falha em build", "Erro observado.", listOf("failure"), 2),
            )
            val first = MemoryConsolidator.consolidate(lessons, dir)
            val second = MemoryConsolidator.consolidate(lessons, dir)
            assertEquals(1, first.lessons)
            assertEquals(1, first.failures)
            assertEquals(first.replay, second.replay)
            assertFalse(first.replay!!.readText().contains("Erro observado"))
            assertTrue(first.questions.single().contains("build"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun dedicatedDeviceToolsAcceptAbsolutePaths() = kotlinx.coroutines.runBlocking {
        val dir = Files.createTempDirectory("girassol-root-path").toFile()
        try {
            val ws = java.io.File(dir, "workspace").apply { mkdirs() }
            val target = java.io.File(dir, "outside.txt")
            val tool = dev.labforge.app.core.agent.WriteFileTool(ws).apply { deviceAccess = true }
            assertTrue(tool.execute(JSONObject().put("path", target.absolutePath).put("content", "ok")).ok)
            assertEquals("ok", target.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun concurrentLessonStoresDoNotLoseRecords() {
        val dir = Files.createTempDirectory("girassol-lessons").toFile()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first = LessonStore(dir)
            val second = LessonStore(dir)
            val tasks = (0 until 20).map { i -> pool.submit { (if (i % 2 == 0) first else second).save("Lesson $i", "Body $i") } }
            tasks.forEach { it.get() }
            assertEquals(20, first.all().size)
        } finally { pool.shutdownNow(); dir.deleteRecursively() }
    }

    @Test fun shellQuoteKeepsMetacharactersLiteral() {
        assertEquals("'a'\"'\"'b $(id)'", ShellSession.quote("a'b \$(id)"))
    }
}
