package dev.labforge.app.core.autonomy

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Registers the night cycle. WorkManager (not AlarmManager) is the correct primitive:
 * it survives reboots, respects Doze and lets us express "idle + charging" as constraints.
 * The trade-off is timing: it is best-effort, not a 3 a.m. cron.
 */
object SleepScheduler {

    private const val TAG = "girassol-autonomy"
    private const val INTERVAL_HOURS = 6L

    @SuppressLint("IdleBatteryChargingConstraints")
    fun schedule(context: Context) {
        // Idle + charging is exactly the contract: the cycle must not compete with the user.
        // On OEMs that never report idle while charging the UI shows "aguardando condição";
        // "Executar agora" remains available as the manual escape hatch.
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<NightWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NightWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NightWorker.UNIQUE_PERIODIC)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NightWorker>()
            .setInputData(workDataOf(NightWorker.KEY_MANUAL to true))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NightWorker.UNIQUE_NOW,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun status(context: Context): WorkInfo? = withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(NightWorker.UNIQUE_PERIODIC)
                .get()
                .firstOrNull()
        }.getOrNull()
    }

    fun stateLabel(info: WorkInfo?): String = when (info?.state) {
        WorkInfo.State.ENQUEUED -> if (info.runAttemptCount > 0) "aguardando condição" else "agendado"
        WorkInfo.State.RUNNING -> "executando"
        WorkInfo.State.SUCCEEDED -> "concluído"
        WorkInfo.State.FAILED -> "falhou"
        WorkInfo.State.CANCELLED -> "desativado"
        WorkInfo.State.BLOCKED -> "bloqueado"
        null -> "não agendado"
    }
}
