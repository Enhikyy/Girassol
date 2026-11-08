package dev.labforge.app.core.device

import java.io.File
import java.security.MessageDigest

/** Integer rectangle in screen pixels. Kept platform-free so the matcher is unit-testable. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    val isEmpty: Boolean get() = width == 0 || height == 0
}

/**
 * One node of the accessibility tree, flattened. `depth` preserves the hierarchy for
 * the text rendering without keeping platform objects (and their recycling rules) alive.
 */
data class UiNode(
    val id: String,
    val role: String,
    val text: String,
    val desc: String,
    val bounds: Bounds,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val depth: Int = 0,
) {
    /** Stable label used by journals and loop detection. */
    fun signature(): String = "$role|$id|${text.take(60)}|${bounds.left},${bounds.top},${bounds.width},${bounds.height}"
}

data class UiSnapshot(
    val packageName: String,
    val capturedAt: Long,
    val nodes: List<UiNode>,
    val truncated: Boolean = false,
) {
    fun find(selector: UiSelector): UiNode? = selector.findIn(nodes)

    /**
     * Hash of the visible structure. Two snapshots with the same signature mean the
     * screen did not change — the basis for the anti-loop guard.
     */
    fun signature(): String {
        val material = buildString {
            append(packageName).append('\n')
            nodes.take(80).forEach { append(it.signature()).append('\n') }
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(material.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** Compact representation sent to the model. Bounded by [maxNodes]. */
    fun describe(maxNodes: Int = 80): String = buildString {
        append("pacote=").append(packageName.ifBlank { "?" })
        append(" nós=").append(nodes.size)
        if (truncated) append(" (truncado)")
        append(" sig=").append(signature())
        append('\n')
        nodes.take(maxNodes).forEach { node ->
            repeat(node.depth.coerceAtMost(6)) { append("  ") }
            append("- ").append(node.role.substringAfterLast('.'))
            if (node.id.isNotBlank()) append(" id=").append(node.id.substringAfterLast('/'))
            if (node.text.isNotBlank()) append(" text=\"").append(node.text.take(80)).append('"')
            if (node.desc.isNotBlank()) append(" desc=\"").append(node.desc.take(80)).append('"')
            append(" @").append(node.bounds.centerX).append(',').append(node.bounds.centerY)
            if (node.clickable) append(" [clicável]")
            if (node.editable) append(" [editável]")
            if (node.scrollable) append(" [rolável]")
            if (node.focused) append(" [foco]")
            append('\n')
        }
    }

    /** Screen area covered by the tree; used to derive swipe coordinates. */
    fun screenBounds(): Bounds {
        if (nodes.isEmpty()) return Bounds(0, 0, 1080, 1920)
        val right = nodes.maxOf { it.bounds.right }
        val bottom = nodes.maxOf { it.bounds.bottom }
        return Bounds(0, 0, right.coerceAtLeast(1), bottom.coerceAtLeast(1))
    }
}

/**
 * Declarative selector. All provided fields must match. Text/desc/id comparisons are
 * case-insensitive substrings by default; [exact] switches to equality.
 */
data class UiSelector(
    val id: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val role: String? = null,
    val exact: Boolean = false,
    val index: Int = 0,
) {
    val isEmpty: Boolean get() = id == null && text == null && desc == null && role == null

    private fun matchesValue(actual: String, expected: String?): Boolean {
        if (expected == null) return true
        return if (exact) actual.equals(expected, ignoreCase = true)
        else actual.contains(expected, ignoreCase = true)
    }

    fun matches(node: UiNode): Boolean =
        matchesValue(node.id, id) &&
            matchesValue(node.text, text) &&
            matchesValue(node.desc, desc) &&
            (role == null || node.role.endsWith(role, ignoreCase = true))

    fun findIn(nodes: List<UiNode>): UiNode? {
        if (isEmpty) return null
        val matches = nodes.filter { matches(it) }
        if (matches.isEmpty()) return null
        val enabled = matches.filter { it.enabled }
        return (if (enabled.isNotEmpty()) enabled else matches).getOrNull(index)
    }

    fun describe(): String = buildList {
        id?.let { add("id=$it") }
        text?.let { add("text=$it") }
        desc?.let { add("desc=$it") }
        role?.let { add("role=$it") }
        if (index > 0) add("index=$index")
    }.joinToString(", ").ifBlank { "(vazio)" }
}

enum class GlobalAction { BACK, HOME, RECENTS, NOTIFICATIONS }

/** Platform boundary. Implementations live in [AccessibilityDeviceController]. */
interface DeviceController {
    fun snapshot(maxNodes: Int = 400): UiSnapshot?
    fun tap(x: Int, y: Int): Boolean
    fun longPress(x: Int, y: Int): Boolean
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 280): Boolean
    fun setText(selector: UiSelector, text: String): Boolean
    fun setTextFocused(text: String): Boolean
    fun global(action: GlobalAction): Boolean
    fun openApp(packageName: String): Boolean
    fun screenshot(): File?
}

data class DeviceActionRecord(
    val at: Long,
    val action: String,
    val detail: String,
    val ok: Boolean,
    val before: String,
    val after: String,
    val note: String = "",
)
