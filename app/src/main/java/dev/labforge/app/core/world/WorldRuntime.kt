package dev.labforge.app.core.world

import android.app.Application
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.job.JobEngine
import dev.labforge.app.core.model.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class WorldRuntimeStatus(
    val loading: Boolean = true,
    val source: String = "loading",
    val lastSavedAt: Long = 0L,
    val recoveredFromBackup: Boolean = false,
    val compensatedMinutes: Long = 0L,
    val warning: String? = null,
)

/** Process-wide owner of the world. All mutations are serialized through [mutex]. */
class WorldRuntime(app: Application) {
    private val directory = File(app.filesDir, "world")
    private val snapshots = WorldSnapshotStore(directory)
    private val lessons = LessonStore(ModelStore(app).agentDir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var engine = WorldEngine(WorldFactory.create(nowWallClockMs = System.currentTimeMillis()))
    private var ticksSinceSave = 0
    private var ticksSinceKnowledgeSync = 0

    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<WorldState> = _state.asStateFlow()
    private val _status = MutableStateFlow(WorldRuntimeStatus())
    val status: StateFlow<WorldRuntimeStatus> = _status.asStateFlow()
    private val _selectedAgentId = MutableStateFlow<String?>(null)
    private val _visibleAgentIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        scope.launch {
            val now = System.currentTimeMillis()
            val loaded = withContext(Dispatchers.IO) { snapshots.load(seed = 7_241_153L, nowWallClockMs = now) }
            mutex.withLock {
                engine = WorldEngine(loaded.state)
                val compensated = engine.catchUp(now)
                _state.value = engine.state
                _status.value = WorldRuntimeStatus(
                    loading = false,
                    source = loaded.source,
                    recoveredFromBackup = loaded.recoveredFromBackup,
                    compensatedMinutes = compensated,
                    warning = loaded.warning,
                )
                persistLocked(now)
            }
        }
        scope.launch {
            JobEngine.state.collect { job ->
                val current = _state.value.integration
                val same = current.activeJobKind == (if (job.running) job.kind else "") &&
                    current.activeJobTitle == (if (job.running) job.title else "") &&
                    current.activeJobProgress == (if (job.running) job.progress else 0f) &&
                    current.lastJobFinishedAt == job.finishedAt &&
                    (job.finishedAt == 0L || current.lastJobSummary == job.summary)
                if (!same) {
                    mutate { it.syncJob(job.kind, job.title, job.progress, job.running, job.summary, job.finishedAt) }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1_000L)
                if (_status.value.loading) continue
                mutex.withLock {
                    if (!engine.state.paused) {
                        engine.step(
                            minutes = 10 * engine.state.timeScale,
                            wallClockMs = System.currentTimeMillis(),
                            detailedIds = setOfNotNull(_selectedAgentId.value),
                            visibleIds = _visibleAgentIds.value,
                        )
                        _state.value = engine.state
                        ticksSinceSave++
                        ticksSinceKnowledgeSync++
                    }
                }
                if (ticksSinceKnowledgeSync >= 15) {
                    val all = withContext(Dispatchers.IO) { lessons.all() }
                    mutate { it.syncKnowledge(all.size, all.firstOrNull()?.title.orEmpty()) }
                    ticksSinceKnowledgeSync = 0
                }
                if (ticksSinceSave >= 12) save()
            }
        }
    }

    fun setVisibleAgents(ids: Set<String>) {
        _visibleAgentIds.value = ids.take(160).toSet()
    }

    fun setSelectedAgent(id: String?) {
        _selectedAgentId.value = id
    }

    suspend fun setPlayback(paused: Boolean, speed: Int) = mutate { it.setPlayback(paused, speed) }

    suspend fun setParameters(demand: Float, research: Float, catchUp: Boolean) = mutate {
        it.setParameters(demand, research, catchUp)
    }

    suspend fun applyHappening(type: HappeningType, description: String): Result<WorldState> = runCatching {
        mutate { it.applyHappening(type, description) }
    }

    suspend fun foundCompany(name: String, strategy: CompanyStrategy): Result<WorldState> = runCatching {
        mutate { it.foundCompany(name, strategy) }
    }

    suspend fun addDialogueMemory(agentId: String, speaker: String, text: String, isUser: Boolean) = mutate {
        it.addDialogueMemory(agentId, speaker, text, isUser)
    }

    suspend fun save() {
        if (_status.value.loading) return
        mutex.withLock { persistLocked(System.currentTimeMillis()) }
    }

    fun requestSave() {
        scope.launch { save() }
    }

    private suspend fun persistLocked(now: Long) {
        val snapshot = engine.state
        runCatching {
            withContext(Dispatchers.IO) { snapshots.save(snapshot) }
        }.onSuccess {
            ticksSinceSave = 0
            _status.value = _status.value.copy(lastSavedAt = now, warning = null)
        }.onFailure { error ->
            _status.value = _status.value.copy(warning = "Falha ao salvar o mundo: ${error.message}")
        }
    }

    private suspend fun mutate(transform: (WorldEngine) -> WorldState): WorldState = mutex.withLock {
        val next = transform(engine)
        _state.value = next
        ticksSinceSave++
        next
    }
}
