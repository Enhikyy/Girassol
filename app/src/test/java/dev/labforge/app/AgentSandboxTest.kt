package dev.labforge.app

import dev.labforge.app.core.agent.ReadFileTool
import dev.labforge.app.core.agent.WriteFileTool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression tests for the agent sandbox. The agent is driven by an LLM, so a
 * prompt-injected or simply confused model will happily ask to write outside the
 * workspace or into `.git/hooks`; these tests pin the guard rails that stop it.
 */
class AgentSandboxTest {

    private fun workspace(): File =
        File.createTempFile("labforge-ws", "").let { f ->
            f.delete()
            f.mkdirs()
            f
        }

    private fun write(ws: File, path: String, content: String = "x"): Boolean = runBlocking {
        WriteFileTool(ws).execute(JSONObject().put("path", path).put("content", content)).ok
    }

    @Test
    fun `writes inside the workspace`() {
        val ws = workspace()
        assertTrue(write(ws, "notes/todo.txt", "olá"))
        assertTrue(File(ws, "notes/todo.txt").isFile)
    }

    @Test
    fun `rejects parent directory traversal`() {
        val ws = workspace()
        assertFalse(write(ws, "../escape.txt"))
        assertFalse(File(ws.parentFile, "escape.txt").exists())
    }

    @Test
    fun `rejects absolute path outside the workspace`() {
        val ws = workspace()
        assertFalse(write(ws, "/data/local/tmp/pwned.txt"))
    }

    @Test
    fun `rejects writes into git internals`() {
        val ws = workspace()
        File(ws, ".git/hooks").mkdirs()
        // A hook is executed by git: writing one would turn "edit a file" into RCE.
        assertFalse(write(ws, ".git/hooks/pre-commit", "#!/bin/sh\nrm -rf /"))
        assertFalse(write(ws, ".git/config", "[core]\n"))
    }

    @Test
    fun `still allows reading git internals`() {
        val ws = workspace()
        File(ws, ".git").mkdirs()
        File(ws, ".git/HEAD").writeText("ref: refs/heads/main\n")
        val ok = runBlocking {
            ReadFileTool(ws).execute(JSONObject().put("path", ".git/HEAD")).ok
        }
        assertTrue(ok)
    }

    @Test
    fun `rejects null byte in path`() {
        val ws = workspace()
        assertFalse(write(ws, "safe.txt\u0000.png"))
    }
}
