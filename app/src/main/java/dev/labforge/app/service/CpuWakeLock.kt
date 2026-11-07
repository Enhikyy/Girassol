package dev.labforge.app.service

import android.content.Context
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns a single partial CPU wake lock. It is always time-bounded as a final
 * safeguard; callers should release it as soon as their work completes.
 */
object CpuWakeLock {
    private const val TAG = "Girassol:CpuWork"
    private const val MAX_HOLD_MS = 4L * 60L * 60L * 1000L
    private val held = AtomicReference<PowerManager.WakeLock?>()

    @Synchronized
    fun acquire(context: Context, timeoutMs: Long = MAX_HOLD_MS): Boolean {
        return runCatching {
            val current = held.get()
            if (current?.isHeld == true) {
                current.acquire(timeoutMs.coerceIn(1_000L, MAX_HOLD_MS))
                true
            } else {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                    setReferenceCounted(false)
                    acquire(timeoutMs.coerceIn(1_000L, MAX_HOLD_MS))
                }
                held.set(lock)
                true
            }
        }.getOrDefault(false)
    }

    @Synchronized
    fun release() {
        val lock = held.getAndSet(null) ?: return
        runCatching { if (lock.isHeld) lock.release() }
    }

    fun isHeld(): Boolean = held.get()?.let { runCatching { it.isHeld }.getOrDefault(false) } ?: false
}
