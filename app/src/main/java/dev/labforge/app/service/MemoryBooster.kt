package dev.labforge.app.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Best-effort RAM recovery. Android does not allow an app to force-stop others
 * reliably; `killBackgroundProcesses` is the only public surface and the system
 * may ignore it. We combine it with cache cleanup and a GC, then report the real
 * delta measured before/after.
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
        val before = availMb(am)
        var touched = 0

        // launchable, non-system apps — the ones a user would normally swipe away
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = context.packageManager.queryIntentActivities(intent, 0)
            apps.map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .forEach { ai ->
                    if (ai.packageName == context.packageName) return@forEach
                    if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@forEach
                    runCatching {
                        am.killBackgroundProcesses(ai.packageName)
                        touched++
                    }
                }
        }

        val caches = purgeCaches(context)
        Runtime.getRuntime().gc()
        delay(650)
        val after = availMb(am)
        Result(
            beforeMb = before,
            afterMb = after,
            freedMb = (after - before).coerceAtLeast(0),
            appsTouched = touched,
            cachesMb = caches,
            note = "Android pode ignorar o encerramento de apps em segundo plano; o ganho real é medido abaixo.",
        )
    }

    private fun availMb(am: ActivityManager): Long {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    private fun purgeCaches(context: Context): Long {
        var freed = 0L
        runCatching {
            context.cacheDir.listFiles()?.forEach { f ->
                freed += sizeOf(f)
                f.deleteRecursively()
            }
            context.externalCacheDir?.listFiles()?.forEach { f ->
                freed += sizeOf(f)
                f.deleteRecursively()
            }
        }
        return freed / (1024 * 1024)
    }

    private fun sizeOf(f: File): Long =
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { sizeOf(it) } ?: 0L)
}
