package dev.labforge.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.labforge.app.MainActivity
import dev.labforge.app.R

/**
 * Keeps the process visible while a native job runs and prevents the CPU from
 * suspending during a foreground quantization/training operation.
 *
 * The wake lock is deliberately partial (screen remains off), time-bounded,
 * and released in onDestroy. It is not a way to bypass Android process limits:
 * Android may still terminate the process under severe memory pressure.
 */
class GirassolService : Service() {

    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Trabalhando no seu modelo"
        val detail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Você pode sair do app; o processo continua."
        return try {
            startForeground(NOTIFICATION_ID, buildNotification(title, detail))
            acquireCpuWakeLock()
            // The job itself lives in JobEngine's process scope. Returning STICKY
            // would resurrect only this notification after process death, not the job.
            START_NOT_STICKY
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível iniciar o serviço em primeiro plano: \${t.message}", t)
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseCpuWakeLock()
        super.onDestroy()
    }

    private fun acquireCpuWakeLock() {
        try {
            val manager = getSystemService(PowerManager::class.java) ?: return
            val lock = cpuWakeLock ?: manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:GirassolNativeJob",
            ).also {
                it.setReferenceCounted(false)
                cpuWakeLock = it
            }
            if (!lock.isHeld) {
                lock.acquire(CPU_WAKE_LOCK_TIMEOUT_MS)
                Log.i(TAG, "WakeLock parcial adquirido (limite de 6 horas).")
            }
        } catch (t: Throwable) {
            // A wake lock is an optimization for a running job; failure must not crash the app.
            Log.w(TAG, "WakeLock não disponível: \${t.message}", t)
        }
    }

    private fun releaseCpuWakeLock() {
        val lock = cpuWakeLock ?: return
        try {
            if (lock.isHeld) lock.release()
        } catch (t: RuntimeException) {
            Log.w(TAG, "Falha ao liberar WakeLock: \${t.message}", t)
        } finally {
            cpuWakeLock = null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jobs do Girassol",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Treino e quantização em andamento"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, detail: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(detail)
            .setSmallIcon(R.drawable.ic_stat_girassol)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "GirassolService"
        private const val CHANNEL_ID = "girassol_jobs"
        private const val NOTIFICATION_ID = 4407
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"
        private const val CPU_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L

        fun start(context: Context, title: String, detail: String = "Você pode sair do app; o processo continua.") {
            val intent = Intent(context, GirassolService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DETAIL, detail)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao solicitar serviço em primeiro plano: \${t.message}", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, GirassolService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao solicitar parada do serviço: \${t.message}", t)
            }
        }
    }
}
