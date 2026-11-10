package app.girassol.core.learning

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.girassol.GirassolApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SleepWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            (applicationContext as GirassolApplication).container.memory.consolidate()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

object SleepScheduler {
    private const val WORK = "girassol-deep-sleep"
    fun schedule(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(WORK); return }
        val constraints = Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresDeviceIdle(true).build()
        val request = PeriodicWorkRequestBuilder<SleepWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
