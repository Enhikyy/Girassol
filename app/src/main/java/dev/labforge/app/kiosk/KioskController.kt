package dev.labforge.app.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import dev.labforge.app.admin.GirassolDeviceAdminReceiver

/** LockTask helpers. Full kiosk requires this app to be provisioned as Device Owner. */
object KioskController {
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isLockTaskActive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun enter(activity: Activity): Result {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(activity.packageName)) {
            return Result(false, "Este aparelho ainda não tem o Girassol configurado como Device Owner.")
        }
        return runCatching {
            dpm.setLockTaskPackages(admin(activity), arrayOf(activity.packageName))
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                dpm.setLockTaskFeatures(
                    admin(activity),
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS,
                )
            }
            activity.startLockTask()
            Result(true, "Modo Kiosk ativado.")
        }.getOrElse { Result(false, "Não foi possível ativar o Kiosk: ${it.message}") }
    }

    fun exit(activity: Activity): Result = runCatching {
        activity.stopLockTask()
        Result(true, "Modo Kiosk encerrado.")
    }.getOrElse { Result(false, "Não foi possível sair do Kiosk: ${it.message}") }

    fun provisioningCommand(packageName: String): String =
        "adb shell dpm set-device-owner ${packageName}/dev.labforge.app.admin.GirassolDeviceAdminReceiver"

    private fun admin(context: Context) =
        ComponentName(context, GirassolDeviceAdminReceiver::class.java)

    data class Result(val success: Boolean, val message: String)
}
