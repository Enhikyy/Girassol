package dev.labforge.app.service

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort memory recovery for this process only.
 *
 * Android does not grant ordinary apps a reliable way to terminate other apps.
 * Deleting cache directories is also unsafe because they may contain active
 * downloads or application-managed temporary data. Keep this operation
 * non-destructive and report the measured system-memory delta honestly.
 */
object MemoryBooster {

    data class Result(
        val beforeMb: Long,
        val afterMb: Long,
        val freedMb: Long,
        val appsTouched: Int,
        val cachesMb: Long,
        val note: String,
    )

    suspend fun boost(context: Context): Result = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = availableMb(am)

        // Ask the current process to reclaim unused managed objects only.
        runCatching { Runtime.getRuntime().gc() }
        runCatching { System.runFinalization() }

        val after = availableMb(am)
        Result(
            beforeMb = before,
            afterMb = after,
            freedMb = (after - before).coerceAtLeast(0),
            appsTouched = 0,
            cachesMb = 0,
            note = "Somente limpeza de memória do próprio processo. O Android controla a memória dos demais aplicativos.",
        )
    }

    private fun availableMb(am: ActivityManager): Long {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }
}
