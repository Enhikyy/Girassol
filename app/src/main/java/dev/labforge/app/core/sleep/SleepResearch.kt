package dev.labforge.app.core.sleep

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.core.research.ResearchDownloader
import dev.labforge.app.core.research.ResearchEngine
import dev.labforge.app.core.research.ResearchRepository
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.settings.researchKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object SleepResearch {
    const val WORK_NAME = "girassol-sleep-research"
    internal const val THEME = "theme"
    private const val INTERVAL_HOURS = 12L

    fun request(theme: String): PeriodicWorkRequest {
        val clean = theme.trim()
        require(clean.length >= 3) { "Informe um tema com pelo menos 3 caracteres" }
        return PeriodicWorkRequest.Builder(SleepResearchWorker::class.java, INTERVAL_HOURS, TimeUnit.HOURS)
            .setInputData(workDataOf(THEME to clean))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(true)
                    .build(),
            )
            .build()
    }

    fun schedule(context: Context, theme: String) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request(theme),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun repository(context: Context): ResearchRepository =
        ResearchRepository(File(ModelStore(context).researchDir, "sleep/results"))
}

class SleepResearchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val theme = inputData.getString(SleepResearch.THEME)?.trim().orEmpty()
        if (theme.length < 3) return Result.failure()
        val root = File(ModelStore(applicationContext).researchDir, "sleep")
        val scratch = File(root, "scratch-$id")
        return try {
            val store = ModelStore(applicationContext)
            val memory = withContext(Dispatchers.IO) {
                dev.labforge.app.core.memory.MemoryConsolidator.consolidate(
                    dev.labforge.app.core.agent.LessonStore(store.agentDir).all(), store.datasetsDir)
            }
            withContext(Dispatchers.IO) {
                File(root, "consolidation.json").apply { parentFile?.mkdirs() }.writeText(
                    org.json.JSONObject().put("lessons", memory.lessons).put("failures", memory.failures)
                        .put("replay", memory.replay?.absolutePath.orEmpty())
                        .put("questions", org.json.JSONArray(memory.questions)).toString())
            }
            val connectivity = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
            val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            if (network?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) return Result.success()
            val cfg = SettingsRepository(applicationContext).settings.first()
            val engine = ResearchEngine(ResearchRepository(scratch), cfg.researchKeys())
            val downloader = ResearchDownloader(File(root, "downloads"))
            runSleepCycle(
                research = {
                    val primary = engine.research(theme)
                    val practice = withContext(Dispatchers.IO) {
                        dev.labforge.app.core.evolution.SkillTree.next(dev.labforge.app.core.evolution.EvidenceJournal(store.agentDir).all())?.skill?.title
                    }
                    val question = memory.questions.firstOrNull() ?: practice?.let { "$theme: como praticar $it no Android sem root?" }
                    if (question == null) primary else {
                        val followUp = engine.research(question)
                        primary.copy(queries = primary.queries + question,
                            docs = (primary.docs + followUp.docs).distinctBy { it.url }.take(36))
                    }
                },
                enrich = { found -> found.copy(docs = downloader.enrich(found.docs, maxDocuments = 8).documents) },
                publish = { found -> withContext(Dispatchers.IO) { SleepResearch.repository(applicationContext).save(found) } },
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { scratch.deleteRecursively() }
        }
    }
}
