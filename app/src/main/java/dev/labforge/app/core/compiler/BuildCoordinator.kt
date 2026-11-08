package dev.labforge.app.core.compiler

import android.content.Context
import dev.labforge.app.integration.android.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/** One build across agent and UI. Each completed stage is checkpointed in private app storage. */
object BuildCoordinator {
    private val lock = Mutex()
    private val mutableReport = MutableStateFlow<BuildReport?>(null)
    val report = mutableReport.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    fun file(context: Context) = File(context.filesDir, "labforge/runs/android-build.json")
    suspend fun restore(context: Context) = withContext(Dispatchers.IO) {
        if (!lock.tryLock()) return@withContext
        try { if (mutableReport.value == null && file(context).isFile) mutableReport.value = BuildReport.read(file(context)) }
        finally { lock.unlock() }
    }
    suspend fun run(context: Context, request: BuildRequest): BuildReport = withContext(Dispatchers.IO) {
        check(lock.tryLock()) { "Já existe uma compilação em andamento" }
        mutableRunning.value = true
        try {
            BuildPipeline.run(request, { command -> TermuxBridge.run(context, request.project, command) }) { report ->
                BuildPipeline.save(file(context), report)
                mutableReport.value = report
            }
        } finally { mutableRunning.value = false; lock.unlock() }
    }
}
