package dev.labforge.app.core.world

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import dev.labforge.app.core.world.cognition.SeveranceMemoryManager

/** Best-effort recovery after process death; Android controls when this worker runs. */
class WorldCatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val now = System.currentTimeMillis()
        val store = WorldSnapshotStore(File(applicationContext.filesDir, "world"))
        val loaded = store.load(seed = 7_241_153L, nowWallClockMs = now)
        val memories = SeveranceMemoryManager(File(directory, "cognition"))
        val engine = WorldEngine(loaded.state, soulProvider = memories::soul)
        engine.catchUp(now)
        store.save(engine.state)
        Result.success()
    }.getOrElse { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
