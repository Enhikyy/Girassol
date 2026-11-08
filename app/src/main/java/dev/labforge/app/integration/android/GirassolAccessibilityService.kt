package dev.labforge.app.integration.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Xml
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.StringWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GirassolAccessibilityService : AccessibilityService() {
    companion object {
        private val connection = MutableStateFlow<GirassolAccessibilityService?>(null)
        val connectionState = connection.asStateFlow()
        val connected: GirassolAccessibilityService? get() = connection.value
    }
    override fun onServiceConnected() { connection.value = this }
    override fun onDestroy() { if (connected === this) connection.value = null; super.onDestroy() }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (connected === this) connection.value = null
        return super.onUnbind(intent)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun hierarchy(): String {
        val root = rootInActiveWindow ?: error("Nenhuma janela acessível ativa")
        val output = StringWriter()
        val xml = Xml.newSerializer().apply { setOutput(output); startDocument("UTF-8", true) }
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count++ >= 1500 || depth > 60) return
            val rect = Rect().also { node.getBoundsInScreen(it) }
            xml.startTag(null, "node")
            xml.attribute(null, "text", node.text?.toString().orEmpty().take(2000))
            xml.attribute(null, "description", node.contentDescription?.toString().orEmpty().take(2000))
            xml.attribute(null, "view-id", node.viewIdResourceName.orEmpty())
            xml.attribute(null, "bounds", rect.toShortString())
            xml.attribute(null, "clickable", node.isClickable.toString())
            xml.attribute(null, "editable", node.isEditable.toString())
            for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                try { visit(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            }
            xml.endTag(null, "node")
        }
        try { visit(root, 0); xml.endDocument() }
        finally { @Suppress("DEPRECATION") root.recycle() }
        return output.toString()
    }

    fun setText(text: String, viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val candidates = if (viewId.isNotBlank()) root.findAccessibilityNodeInfosByViewId(viewId) else emptyList()
            val node = if (viewId.isNotBlank()) candidates.firstOrNull() else root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            candidates.drop(1).forEach { @Suppress("DEPRECATION") it.recycle() }
            if (node == null) return false
            try {
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                })
            } finally { @Suppress("DEPRECATION") node.recycle() }
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }

    suspend fun gesture(x: Float, y: Float, x2: Float, y2: Float, duration: Long): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val path = Path().apply { moveTo(x, y); if (x != x2 || y != y2) lineTo(x2, y2) }
                val gesture = GestureDescription.Builder().addStroke(
                    GestureDescription.StrokeDescription(path, 0, duration.coerceIn(1, 5000))).build()
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }, null)
                if (!accepted && continuation.isActive) continuation.resume(false)
            }
        }

    suspend fun screenshot(destination: File): File {
        if (Build.VERSION.SDK_INT < 30) error("Captura por acessibilidade exige Android 11 ou superior")
        return screenshotApi30(destination)
    }

    @RequiresApi(30)
    private suspend fun screenshotApi30(destination: File): File = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            takeScreenshot(Display.DEFAULT_DISPLAY, Dispatchers.IO.asExecutor(), object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        if (!continuation.isActive) return
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) ?: error("Imagem indisponível")
                        try {
                            val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: error("Falha ao copiar captura")
                            try {
                                destination.parentFile?.mkdirs()
                                destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            } finally { bitmap.recycle() }
                        } finally { hardware.recycle() }
                        if (continuation.isActive) continuation.resume(destination)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally { buffer.close() }
                }
                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Captura recusada pelo Android ($errorCode)"))
                }
            })
        }
    }
}
