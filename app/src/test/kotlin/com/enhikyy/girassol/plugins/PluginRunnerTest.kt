package com.enhikyy.girassol.plugins

import com.enhikyy.girassol.core.AutonomyController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRunnerTest {
    @Test
    fun luaRunsWithReadOnlyWorldApiAndNoIoOrOs() = runBlocking {
        AutonomyController.resume("teste Lua")
        val result = PluginRunner.run(
            name = "sandbox_lua",
            extension = "lua",
            source = """
                if io == nil and os == nil and package == nil and luajava == nil then
                    girassol.log("sandbox-ok")
                end
                girassol.log(girassol.worldLine())
                girassol.emit("proposta: observar o mundo")
            """.trimIndent(),
        )
        assertTrue(result.ok)
        assertTrue(result.lines.any { it.contains("sandbox-ok") })
        assertTrue(result.lines.any { it.startsWith("AÇÃO:") })
    }

    @Test
    fun javascriptRunsWithoutJavaClassExposure() = runBlocking {
        AutonomyController.resume("teste JavaScript")
        val result = PluginRunner.run(
            name = "sandbox_js",
            extension = "js",
            source = """
                girassol.log("java=" + typeof Packages);
                girassol.log(girassol.worldLine());
                girassol.emit("proposta: leitura");
            """.trimIndent(),
        )
        assertTrue(result.ok)
        assertTrue(result.lines.any { it.contains("java=undefined") })
        assertTrue(result.lines.any { it.startsWith("AÇÃO:") })
    }

    @Test
    fun unknownExtensionIsRejected() = runBlocking {
        val result = PluginRunner.run("bad", "kt", "println(1)")
        assertFalse(result.ok)
    }
}
