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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.labforge.app.MainActivity
import dev.labforge.app.R

/**
 * Keeps the process alive (and visible) while a native job runs. Quantization and
 * fine-tuning are CPU-bound and can take tens of minutes; without a foreground
 * service Android may reclaim the app when it is backgrounded.
 */
class GirassolService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onDestroy() {
        CpuWakeLock.releaseJob()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Trabalhando no seu modelo"
        val detail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Você pode sair do app; o processo continua."
        return try {
            CpuWakeLock.acquireForJob(applicationContext)
            startForeground(NOTIFICATION_ID, buildNotification(title, detail))
            START_STICKY
        } catch (t: Throwable) {
            // Some OEMs deny dataSync FGS even in foreground; never take the app down for this.
            android.util.Log.w("GirassolService", "startForeground failed: ${t.message}")
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        private const val CHANNEL_ID = "girassol_jobs"
        private const val NOTIFICATION_ID = 4407
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"

        fun start(context: Context, title: String, detail: String = "Você pode sair do app; o processo continua.") {
            val intent = Intent(context, GirassolService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DETAIL, detail)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GirassolService::class.java)) }
        }
    }
}
