package dev.labforge.app.core.world

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorldWorkScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WorldCatchUpWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "girassol-world-catch-up", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
