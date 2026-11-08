package dev.labforge.app

import dev.labforge.app.core.device.Bounds
import dev.labforge.app.core.device.DeviceController
import dev.labforge.app.core.device.DeviceScriptParser
import dev.labforge.app.core.device.DeviceScriptRunner
import dev.labforge.app.core.device.GlobalAction
import dev.labforge.app.core.device.UiNode
import dev.labforge.app.core.device.UiSelector
import dev.labforge.app.core.device.UiSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceScriptTest {

    private class FakeController(
        var screen: UiSnapshot,
        var onTap: () -> Unit = {},
    ) : DeviceController {
        val taps = mutableListOf<Pair<Int, Int>>()
        var opened: String? = null

        override fun snapshot(maxNodes: Int): UiSnapshot = screen
        override fun tap(x: Int, y: Int): Boolean {
            taps.add(x to y)
            onTap()
            return true
        }
        override fun longPress(x: Int, y: Int) = true
        override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = true
        override fun setText(selector: UiSelector, text: String) = true
        override fun setTextFocused(text: String) = true
        override fun global(action: GlobalAction) = true
        override fun openApp(packageName: String): Boolean {
            opened = packageName
            return true
        }
        override fun screenshot(): File? = null
    }

    private fun screen(vararg labels: String) = UiSnapshot(
        packageName = "com.android.settings",
        capturedAt = 0L,
        nodes = labels.mapIndexed { index, label ->
            UiNode(
                id = "", role = "android.widget.TextView", text = label, desc = "",
                bounds = Bounds(0, index * 100, 400, index * 100 + 80),
            )
        },
    )

    @Test
    fun `script opens waits taps and asserts`() = runBlocking {
        lateinit var controller: FakeController
        controller = FakeController(screen("Rede e internet", "Wi-Fi")) {
            controller.screen = screen("Wi-Fi", "Wi-Fi conectado")
        }
        val script = DeviceScriptParser.parse(
            """
            {"name":"wifi","steps":[
              {"action":"open","package":"com.android.settings"},
              {"action":"wait","text":"Rede e internet","timeout_ms":1000},
              {"action":"tap","text":"Rede e internet"},
              {"action":"assert","text":"Wi-Fi conectado"}
            ]}
            """.trimIndent(),
        )

        val report = DeviceScriptRunner(controller).run(script) { }

        assertTrue(report.render(), report.ok)
        assertEquals("com.android.settings", controller.opened)
        assertEquals(1, controller.taps.size)
        assertEquals(200, controller.taps.first().first)
        assertEquals(40, controller.taps.first().second)
    }

    @Test
    fun `failed assert stops the script and marks it failed`() = runBlocking {
        val controller = FakeController(screen("Rede e internet"))
        val script = DeviceScriptParser.parse(
            """
            {"name":"falha","steps":[
              {"action":"open","package":"com.android.settings"},
              {"action":"assert","text":"Bluetooth"},
              {"action":"tap","text":"Rede e internet"}
            ]}
            """.trimIndent(),
        )

        val report = DeviceScriptRunner(controller).run(script) { }

        assertFalse(report.ok)
        assertEquals(2, report.steps.size)
        assertTrue(report.steps.last().detail.contains("não encontrado"))
        assertTrue(controller.taps.isEmpty())
    }

    @Test
    fun `assert absent passes when node is missing`() = runBlocking {
        val controller = FakeController(screen("Rede e internet"))
        val script = DeviceScriptParser.parse(
            """{"name":"ausente","steps":[{"action":"assert","text":"Bluetooth","absent":true}]}""",
        )
        assertTrue(DeviceScriptRunner(controller).run(script) { }.ok)
    }

    @Test
    fun `parser rejects unknown actions and empty scripts`() {
        val unknown = runCatching {
            DeviceScriptParser.parse("""{"steps":[{"action":"hack"}]}""")
        }
        assertTrue(unknown.isFailure)

        val empty = runCatching { DeviceScriptParser.parse("""{"steps":[]}""") }
        assertTrue(empty.isFailure)
    }
}
