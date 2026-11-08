package dev.labforge.app.core.device

import android.content.Context
import dev.labforge.app.service.GirassolAccessibilityService

/**
 * Single entry point used by the agent tools and the UI. [testController] exists so the
 * tool layer can be exercised on the JVM without an accessibility service.
 */
object DeviceBridge {

    @Volatile
    var testController: DeviceController? = null

    fun controller(): DeviceController? =
        testController ?: GirassolAccessibilityService.controller()

    fun isReady(): Boolean = controller() != null

    fun enabledInSettings(context: Context): Boolean =
        GirassolAccessibilityService.isEnabled(context)

    fun openAccessibilitySettings(context: Context) =
        GirassolAccessibilityService.openSettings(context)
}
