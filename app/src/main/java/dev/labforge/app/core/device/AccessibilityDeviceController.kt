package dev.labforge.app.core.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import dev.labforge.app.service.GirassolAccessibilityService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Accessibility-backed implementation of [DeviceController]. Every method is defensive:
 * OEM accessibility implementations differ, so failures return false/null instead of
 * taking the process down.
 */
class AccessibilityDeviceController(
    private val service: GirassolAccessibilityService,
) : DeviceController {

    override fun snapshot(maxNodes: Int): UiSnapshot? {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        val limit = maxNodes.coerceIn(20, 1500)
        val out = ArrayList<UiNode>(minOf(limit, 256))
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var truncated = false

        while (queue.isNotEmpty()) {
            if (out.size >= limit) {
                truncated = true
                break
            }
            val (node, depth) = queue.removeFirst()
            if (node.isVisibleToUser) out.add(node.toUiNode(depth))
            val children = runCatching { node.childCount }.getOrDefault(0)
            for (index in 0 until children.coerceAtMost(80)) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                if (depth < 48) queue.addLast(child to depth + 1)
            }
        }

        return UiSnapshot(
            packageName = runCatching { root.packageName?.toString().orEmpty() }.getOrDefault(""),
            capturedAt = System.currentTimeMillis(),
            nodes = out,
            truncated = truncated,
        )
    }

    override fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo((x + 1).toFloat(), (y + 1).toFloat())
        }
        return gesture(path, 0L, 48L)
    }

    override fun longPress(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo((x + 1).toFloat(), (y + 1).toFloat())
        }
        return gesture(path, 0L, 650L)
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return gesture(path, 0L, durationMs.coerceIn(80L, 3000L))
    }

    override fun setText(selector: UiSelector, text: String): Boolean {
        if (selector.isEmpty) return false
        val node = findPlatformNode(selector) ?: return false
        if (!node.isEditable && !node.isFocusable) return false
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
        if (!ok) {
            // Some WebViews only accept text after a click.
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            Thread.sleep(120)
            return runCatching {
                findPlatformNode(selector)?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ?: false
            }.getOrDefault(false)
        }
        return true
    }

    override fun setTextFocused(text: String): Boolean {
        val focused = runCatching {
            service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return runCatching {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    override fun global(action: GlobalAction): Boolean = runCatching {
        service.performGlobalAction(
            when (action) {
                GlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
                GlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
                GlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
                GlobalAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            },
        )
    }.getOrDefault(false)

    override fun openApp(packageName: String): Boolean = runCatching {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        service.startActivity(intent)
        true
    }.getOrDefault(false)

    override fun screenshot(): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching { captureR() }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureR(): File? {
        val latch = CountDownLatch(1)
        var saved: File? = null
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        saved = persist(result)
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            },
        )
        latch.await(5, TimeUnit.SECONDS)
        return saved
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun persist(result: AccessibilityService.ScreenshotResult): File? {
        val hardware = result.hardwareBuffer
        return try {
            val bitmap = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            val dir = File(service.cacheDir, "device-shots").apply { mkdirs() }
            val file = File(dir, "shot-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
            bitmap.recycle()
            file
        } finally {
            hardware.close()
        }
    }

    private fun gesture(path: Path, startDelay: Long, duration: Long): Boolean = runCatching {
        val stroke = GestureDescription.StrokeDescription(path, startDelay, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }.getOrDefault(false)

    private fun findPlatformNode(selector: UiSelector): AccessibilityNodeInfo? {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 1500) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && selector.matches(node.toUiNode(0))) return node
            val children = runCatching { node.childCount }.getOrDefault(0)
            for (index in 0 until children.coerceAtMost(80)) {
                runCatching { node.getChild(index) }.getOrNull()?.let { queue.addLast(it) }
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.toUiNode(depth: Int): UiNode {
        val rect = Rect().also { getBoundsInScreen(it) }
        val label = text?.toString().orEmpty()
        val hint = hintText?.toString().orEmpty()
        return UiNode(
            id = viewIdResourceName.orEmpty(),
            role = className?.toString().orEmpty(),
            text = label.take(200),
            desc = (contentDescription?.toString().orEmpty().ifBlank { hint }).take(200),
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            clickable = isClickable,
            editable = isEditable,
            scrollable = isScrollable,
            enabled = isEnabled,
            focused = isFocused,
            depth = depth,
        )
    }
}
