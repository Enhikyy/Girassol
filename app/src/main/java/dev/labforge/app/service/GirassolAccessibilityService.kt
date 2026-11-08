package dev.labforge.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import dev.labforge.app.core.device.AccessibilityDeviceController
import dev.labforge.app.core.device.DeviceController
import java.lang.ref.WeakReference

/**
 * The app's "hands". Gives the agent a read-only view of the active window plus the
 * ability to tap, type, swipe and launch apps. The user must enable it manually in
 * Settings; Android has no API to enable an accessibility service from an app.
 */
class GirassolAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastPackage: String = ""

    @Volatile
    private var lastEventAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> {
                event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { lastPackage = it }
                lastEventAt = System.currentTimeMillis()
            }
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun activePackage(): String = lastPackage

    fun lastEventAt(): Long = lastEventAt

    companion object {
        private var instance: WeakReference<GirassolAccessibilityService>? = null

        fun current(): GirassolAccessibilityService? = instance?.get()

        fun controller(): DeviceController? = current()?.let { AccessibilityDeviceController(it) }

        /**
         * Reads the secure setting instead of trusting a cached flag: the user can revoke
         * access at any moment and the UI must reflect it without a restart.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, GirassolAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { flat ->
                ComponentName.unflattenFromString(flat) == expected
            }
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
