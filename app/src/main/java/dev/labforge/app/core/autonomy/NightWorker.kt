package dev.labforge.app.core.autonomy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.labforge.app.core.learning.LearningContinuity
import dev.labforge.app.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Consolidation cycle that runs while the device is idle and charging. It deliberately
 * does not train LoRA: training needs a model/dataset selection and a foreground session.
 * What it does is real and bounded — review recent experiences, produce dream tiles and
 * leave an auditable result.
 */
class NightWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val settings = runCatching { SettingsRepository(context).settings.first() }.getOrNull()

        if (!manual && settings?.nightCycleEnabled != true) {
            return@withContext Result.success()
        }

        val plan = CycleBudget.evaluate(context)
        if (!manual && !plan.run) {
            AutonomyStore.record(context, true, "ciclo pulado — ${plan.reason} · ${plan.describe()}")
            return@withContext Result.success()
        }

        val outcome = runCatching {
            val learning = LearningContinuity(
                File(context.filesDir, "labforge/research/learning-continuity.json"),
            )
            val before = learning.snapshot()
            learning.record(
                kind = "cycle",
                topic = "sono",
                detail = "ciclo noturno: ${plan.describe()}",
                importance = 0.4f,
            )
            val after = learning.dream()
            val newTiles = (after.dreams.size - before.dreams.size).coerceAtLeast(0)
            val due = after.skills.count { it.dueAt in 1..System.currentTimeMillis() }
            AutonomyStore.record(
                context,
                true,
                "consolidação concluída · +$newTiles padrões · $due revisões vencidas · ${plan.describe()}",
            )
        }
        outcome.onFailure { error ->
            AutonomyStore.record(context, false, "falha: ${error.message ?: error.javaClass.simpleName}")
        }

        when {
            outcome.isSuccess -> Result.success()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_MANUAL = "manual"
        const val UNIQUE_PERIODIC = "girassol-night-cycle"
        const val UNIQUE_NOW = "girassol-night-cycle-now"
    }
}
