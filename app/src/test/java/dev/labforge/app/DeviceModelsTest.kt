package dev.labforge.app

import dev.labforge.app.core.device.Bounds
import dev.labforge.app.core.device.UiNode
import dev.labforge.app.core.device.UiSelector
import dev.labforge.app.core.device.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceModelsTest {

    private fun node(
        text: String = "",
        desc: String = "",
        id: String = "",
        role: String = "android.widget.Button",
        left: Int = 0,
        top: Int = 0,
        enabled: Boolean = true,
    ) = UiNode(
        id = id,
        role = role,
        text = text,
        desc = desc,
        bounds = Bounds(left, top, left + 100, top + 50),
        clickable = true,
        enabled = enabled,
        depth = 1,
    )

    @Test
    fun `selector matches text case-insensitively by substring`() {
        val target = node(text = "Wi-Fi e rede")
        assertTrue(UiSelector(text = "wi-fi").matches(target))
        assertFalse(UiSelector(text = "Bluetooth").matches(target))
    }

    @Test
    fun `exact selector rejects partial text`() {
        val target = node(text = "Wi-Fi e rede")
        assertTrue(UiSelector(text = "wi-fi e rede", exact = true).matches(target))
        assertFalse(UiSelector(text = "wi-fi", exact = true).matches(target))
    }

    @Test
    fun `empty selector never matches`() {
        val nodes = listOf(node(text = "qualquer"))
        assertNull(UiSelector().findIn(nodes))
    }

    @Test
    fun `index picks the second enabled match`() {
        val nodes = listOf(
            node(text = "Repetido", top = 10),
            node(text = "Repetido", top = 100),
        )
        val found = UiSelector(text = "repetido", index = 1).findIn(nodes)
        assertEquals(100, found?.bounds?.top)
    }

    @Test
    fun `disabled matches are skipped when an enabled one exists`() {
        val nodes = listOf(
            node(text = "Salvar", enabled = false),
            node(text = "Salvar", top = 80, enabled = true),
        )
        assertEquals(80, UiSelector(text = "salvar").findIn(nodes)?.bounds?.top)
    }

    @Test
    fun `signature is stable for identical trees and changes with content`() {
        val a = UiSnapshot("com.example", 1L, listOf(node(text = "A")))
        val b = UiSnapshot("com.example", 2L, listOf(node(text = "A")))
        val c = UiSnapshot("com.example", 3L, listOf(node(text = "B")))
        assertEquals(a.signature(), b.signature())
        assertNotEquals(a.signature(), c.signature())
    }

    @Test
    fun `describe includes package actions and ids`() {
        val snapshot = UiSnapshot(
            "com.android.settings",
            0L,
            listOf(
                node(text = "Wi-Fi", id = "com.android.settings:id/wifi"),
                node(text = "Salvar", role = "android.widget.EditText", enabled = true),
            ),
        )
        val text = snapshot.describe()
        assertTrue(text.contains("com.android.settings"))
        assertTrue(text.contains("Wi-Fi"))
        assertTrue(text.contains("id=wifi"))
        assertTrue(text.contains("[clicável]"))
    }

    @Test
    fun `screen bounds come from the widest node`() {
        val snapshot = UiSnapshot(
            "pkg", 0L, listOf(
                node(left = 0, top = 0),
                node(left = 900, top = 1800),
            ),
        )
        assertEquals(1000, snapshot.screenBounds().right)
        assertEquals(1850, snapshot.screenBounds().bottom)
    }
}
