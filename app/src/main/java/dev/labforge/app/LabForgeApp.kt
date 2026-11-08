package dev.labforge.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import java.io.File

class LabForgeApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        dev.labforge.app.integration.android.DeviceRuntime.init(this)
        installCrashLogger()
        runCatching { dev.labforge.app.integration.llamatik.LlamatikRuntime.init(this) }
        // Touch the native bridge once so failures surface in logcat early.
        runCatching { dev.labforge.app.core.native.LlamaBridge.version() }
            .onFailure { Log.w("Girassol", "native engine unavailable: ${it.message}") }
    }

    /** Last-resort breadcrumb so a launch crash can be diagnosed offline. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(filesDir, "last-crash.txt").writeText(
                    buildString {
                        appendLine("time: ${System.currentTimeMillis()}")
                        appendLine("thread: ${thread.name}")
                        appendLine(Log.getStackTraceString(error))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
