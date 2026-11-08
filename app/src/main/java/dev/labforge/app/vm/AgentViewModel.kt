package dev.labforge.app.vm

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.AppSelection
import dev.labforge.app.core.Perf
import dev.labforge.app.core.agent.AgentEvent
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.agent.ReActAgent
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.term.GitRepo
import dev.labforge.app.core.term.ShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AgentMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val toolName: String = "",
    val toolArgs: String = "",
    val ok: Boolean = true,
    val pending: Boolean = false,
) {
    enum class Role { USER, THOUGHT, TOOL, FINAL, ERROR, SYSTEM }
}

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ModelStore(app)
    private val lessons = LessonStore(store.agentDir)
    private val settingsRepo = SettingsRepository(app)
    private val shell = ShellSession(store.workspace())
    private val git = GitRepo(store.workspace())

    var messages by mutableStateOf<List<AgentMessage>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("pronto")
        private set
    var lessonList by mutableStateOf<List<LessonStore.Lesson>>(emptyList())
        private set
    var terminalOut by mutableStateOf("")
        private set
    var gitOut by mutableStateOf("")
        private set
    var busyTerminal by mutableStateOf(false)
        private set

    // native token accounting
    var localPromptTokens by mutableStateOf(0)
        private set
    var promptTokens by mutableStateOf(0)
        private set
    var completionTokens by mutableStateOf(0)
        private set
    var estimatedSeconds by mutableStateOf(0.0)
        private set
    var elapsedMs by mutableStateOf(0L)
        private set
    var tokensPerSecond by mutableStateOf(0.0)
        private set

    private var job: Job? = null
    private var seq = 0L
    private var startedAt = 0L

    private var thinkId: Long? = null
    private var draftId: Long? = null
    private val thinkBuf = StringBuilder()
    private val contentBuf = StringBuilder()
    private var bufStamp = 0L

    init {
        viewModelScope.launch {
            lessonList = withContext(Dispatchers.IO) { lessons.all() }
        }
        viewModelScope.launch {
            while (true) {
                delay(110)
                if (bufStamp != 0L) flushStream()
            }
        }
    }

    fun send(task: String) {
        if (running || task.isBlank()) return
        running = true
        status = "pensando…"
        startedAt = System.currentTimeMillis()
        thinkId = null
        draftId = null
        thinkBuf.setLength(0)
        contentBuf.setLength(0)
        bufStamp = System.currentTimeMillis()
        add(AgentMessage(++seq, AgentMessage.Role.USER, task))

        // native token count + ETA (local model, if any)
        viewModelScope.launch(Dispatchers.IO) {
            val path = AppSelection.modelPath
            if (path != null && !dev.labforge.app.core.job.JobEngine.isRunning) {
                val entry = runCatching { store.readModel(File(path)) }.getOrNull()
                val n = LlamaBridge.countTokens(path, task)
                val params = entry?.meta?.parameterCount ?: 0L
                val bits = entry?.meta?.quantLabel?.let { quantBits(it) } ?: 4.5
                withContext(Dispatchers.Main) {
                    localPromptTokens = n
                    if (params > 0) {
                        estimatedSeconds = Perf.responseSeconds(
                            n.coerceAtLeast(task.length / 4), 450, params, bits,
                            Runtime.getRuntime().availableProcessors(),
                        )
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    localPromptTokens = 0
                    estimatedSeconds = 0.0
                }
            }
        }

        job = viewModelScope.launch {
            val cfg = settingsRepo.settings.first()
            val agent = ReActAgent(
                workspace = store.workspace(),
                lessons = lessons,
                llm = LlmClient(cfg.endpoint, cfg.apiKey, cfg.model, cfg.temperature),
                maxSteps = if (cfg.maxSteps <= 0) 0 else cfg.maxSteps,
                settings = cfg,
                onEvent = { event -> viewModelScope.launch(Dispatchers.Main) { handle(event) } },
            )
            agent.run(task)
            flushStream()
            running = false
            status = "pronto"
            elapsedMs = System.currentTimeMillis() - startedAt
            if (completionTokens > 0 && elapsedMs > 0) {
                tokensPerSecond = completionTokens / (elapsedMs / 1000.0)
            }
            lessonList = withContext(Dispatchers.IO) { lessons.all() }
        }
    }

    private suspend fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.Step -> {
                flushStream()
                status = if (event.max <= 0) "passo ${event.index} · autônomo" else "passo ${event.index}/${event.max}"
            }
            is AgentEvent.Token -> {
                if (event.reasoning) {
                    thinkBuf.append(event.text)
                    if (thinkId == null) thinkId = ++seq
                } else {
                    contentBuf.append(event.text)
                    if (draftId == null) draftId = ++seq
                }
                bufStamp = System.currentTimeMillis()
            }
            is AgentEvent.Usage -> {
                promptTokens += event.prompt
                completionTokens += event.completion
            }
            is AgentEvent.Thought -> {
                val streamedId = thinkId
                flushStream()
                if (streamedId != null) {
                    update(streamedId) { it.copy(pending = false) }
                    thinkId = null
                } else if (event.text.isNotBlank()) {
                    add(AgentMessage(++seq, AgentMessage.Role.THOUGHT, event.text))
                }
            }
            is AgentEvent.ToolStart -> {
                flushStream()
                // content written before a tool call is reasoning about the action
                if (draftId != null) {
                    val id = draftId!!
                    update(id) { it.copy(role = AgentMessage.Role.THOUGHT, pending = false) }
                    draftId = null
                    contentBuf.setLength(0)
                }
                add(
                    AgentMessage(
                        ++seq, AgentMessage.Role.TOOL, "executando…",
                        toolName = event.name, toolArgs = event.args, pending = true,
                    ),
                )
                status = "ferramenta: ${event.name}"
            }
            is AgentEvent.ToolEnd -> {
                val idx = messages.indexOfLast { it.role == AgentMessage.Role.TOOL && it.pending }
                if (idx >= 0) {
                    messages = messages.toMutableList().also {
                        it[idx] = it[idx].copy(text = event.output, ok = event.ok, pending = false)
                    }
                } else {
                    add(AgentMessage(++seq, AgentMessage.Role.TOOL, event.output, toolName = event.name, ok = event.ok))
                }
            }
            is AgentEvent.Final -> {
                val streamedId = draftId
                flushStream()
                if (streamedId != null) {
                    update(streamedId) { it.copy(text = event.text, role = AgentMessage.Role.FINAL, pending = false) }
                    draftId = null
                } else {
                    add(AgentMessage(++seq, AgentMessage.Role.FINAL, event.text))
                }
            }
            is AgentEvent.Failure -> {
                flushStream()
                thinkId?.let { id -> update(id) { it.copy(pending = false) } }
                draftId?.let { id -> update(id) { it.copy(pending = false) } }
                thinkId = null
                draftId = null
                add(AgentMessage(++seq, AgentMessage.Role.ERROR, event.message))
            }
        }
    }

    private fun flushStream() {
        if (thinkId != null && thinkBuf.isNotEmpty()) {
            val id = thinkId!!
            val text = thinkBuf.toString()
            thinkBuf.setLength(0)
            upsert(id, AgentMessage.Role.THOUGHT, text, pending = true)
        }
        if (draftId != null && contentBuf.isNotEmpty()) {
            val id = draftId!!
            val text = contentBuf.toString()
            contentBuf.setLength(0)
            upsert(id, AgentMessage.Role.FINAL, text, pending = true)
        }
        bufStamp = 0L
    }

    private fun update(id: Long, transform: (AgentMessage) -> AgentMessage) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) messages = messages.toMutableList().also { it[idx] = transform(it[idx]) }
    }

    private fun upsert(id: Long, role: AgentMessage.Role, text: String, pending: Boolean) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            messages = messages.toMutableList().also {
                val old = it[idx]
                it[idx] = old.copy(text = old.text + text, pending = pending)
            }
        } else {
            messages = messages + AgentMessage(id, role, text, pending = pending)
        }
    }

    private fun add(m: AgentMessage) { messages = messages + m }

    fun stop() {
        job?.cancel()
        running = false
        status = "interrompido"
        messages = messages.map { if (it.pending) it.copy(pending = false, text = "cancelado") else it }
    }

    fun newSession() {
        stop()
        messages = emptyList()
        status = "pronto"
        promptTokens = 0
        completionTokens = 0
        localPromptTokens = 0
        elapsedMs = 0
        tokensPerSecond = 0.0
    }

    fun injectFromCodex(summary: String) {
        newSession()
        add(AgentMessage(++seq, AgentMessage.Role.USER, summary))
        status = "contexto do Codex carregado"
    }

    fun runTerminal(command: String) {
        if (command.isBlank() || busyTerminal) return
        busyTerminal = true
        viewModelScope.launch {
            val r = shell.run(command, 120)
            terminalOut = buildString {
                append(terminalOut.takeLast(20_000))
                append("\n$ ").append(command).append('\n').append(r.output)
                append("\n[exit ").append(r.exitCode).append(" · ").append(r.durationMs).append(" ms]\n")
            }.takeLast(30_000)
            busyTerminal = false
        }
    }

    fun gitAction(action: String) {
        viewModelScope.launch {
            gitOut = "…"
            gitOut = when (action) {
                "status" -> git.status()
                "commit" -> git.commit("checkpoint manual · ${System.currentTimeMillis()}")
                "log" -> git.log(12)
                "reset" -> git.resetHard("HEAD~1")
                "init" -> git.init()
                else -> "ação desconhecida"
            }
        }
    }

    fun saveLesson(title: String, body: String, tags: List<String>) {
        viewModelScope.launch {
            lessonList = withContext(Dispatchers.IO) {
                lessons.save(title, body, tags)
                lessons.all()
            }
        }
    }

    fun deleteLesson(id: String) {
        viewModelScope.launch {
            lessonList = withContext(Dispatchers.IO) {
                lessons.delete(id)
                lessons.all()
            }
        }
    }

    fun refreshLessons() {
        viewModelScope.launch {
            lessonList = withContext(Dispatchers.IO) { lessons.all() }
        }
    }

    private fun quantBits(label: String): Double = when {
        label.contains("Q8") -> 8.5
        label.contains("Q6") -> 6.56
        label.contains("Q5") -> 5.67
        label.contains("Q4") -> 4.85
        label.contains("Q3") -> 3.9
        label.contains("Q2") -> 2.7
        label.contains("F16") || label.contains("BF16") -> 16.0
        else -> 4.85
    }
}
