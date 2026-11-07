package dev.labforge.app.service

import android.content.Context
import android.os.PowerManager

/**
 * Independent, bounded partial wake locks for user-requested CPU residency and
 * foreground jobs. Keeping separate leases prevents service shutdown from
 * accidentally cancelling a manual user lock.
 */
object CpuWakeLock {
    private const val TAG = "Girassol:CpuWork"
    private const val MAX_HOLD_MS = 4L * 60L * 60L * 1000L
    private var manualLock: PowerManager.WakeLock? = null
    private var jobLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context, timeoutMs: Long = MAX_HOLD_MS): Boolean =
        acquireLock(context, timeoutMs, manual = true)

    @Synchronized
    fun acquireForJob(context: Context, timeoutMs: Long = MAX_HOLD_MS): Boolean =
        acquireLock(context, timeoutMs, manual = false)

    @Synchronized
    private fun acquireLock(context: Context, timeoutMs: Long, manual: Boolean): Boolean =
        runCatching {
            val current = if (manual) manualLock else jobLock
            val bounded = timeoutMs.coerceIn(1_000L, MAX_HOLD_MS)
            if (current?.isHeld == true) {
                current.acquire(bounded)
            } else {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:${if (manual) "manual" else "job"}")
                lock.setReferenceCounted(false)
                lock.acquire(bounded)
                if (manual) manualLock = lock else jobLock = lock
            }
            true
        }.getOrDefault(false)

    @Synchronized
    fun release() = releaseManual()

    @Synchronized
    fun releaseManual() {
        val lock = manualLock
        manualLock = null
        runCatching { if (lock?.isHeld == true) lock.release() }
    }

    @Synchronized
    fun releaseJob() {
        val lock = jobLock
        jobLock = null
        runCatching { if (lock?.isHeld == true) lock.release() }
    }

    @Synchronized
    fun isHeld(): Boolean =
        (manualLock?.let { runCatching { it.isHeld }.getOrDefault(false) } == true) ||
            (jobLock?.let { runCatching { it.isHeld }.getOrDefault(false) } == true)
}
