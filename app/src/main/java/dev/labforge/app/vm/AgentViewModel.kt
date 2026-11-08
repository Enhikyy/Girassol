package dev.labforge.app.vm

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.AppSelection
import dev.labforge.app.core.Perf
import dev.labforge.app.core.agent.AgentEvent
import dev.labforge.app.core.agent.AgentTurn
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.agent.AgentLlm
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.agent.NativeLlmClient
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var activityLog by mutableStateOf<List<String>>(emptyList())
        private set

    private val logClock = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logFileLock = Any()
    private val activityLogFile = File(store.agentDir, "agent-activity.log")
    private fun log(level: String, message: String) {
        val line = "[${synchronized(logClock) { logClock.format(Date()) }}][${level.uppercase(Locale.ROOT)}] $message"
        activityLog = (activityLog + line).takeLast(600)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(logFileLock) {
                    activityLogFile.parentFile?.mkdirs()
                    if (activityLogFile.length() > 2L * 1024 * 1024) {
                        val previous = File(activityLogFile.parentFile, "agent-activity.previous.log")
                        previous.delete()
                        activityLogFile.renameTo(previous)
                    }
                    activityLogFile.appendText(line + "\n")
                }
            }
        }
    }
    fun clearActivityLog() {
        activityLog = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(logFileLock) {
                activityLogFile.delete()
                File(activityLogFile.parentFile, "agent-activity.previous.log").delete()
            }
        }
    }
    val hasLocalModel: Boolean
        get() = AppSelection.modelPath?.let { File(it).isFile } == true

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
        viewModelScope.launch(Dispatchers.IO) {
            val persisted = runCatching { activityLogFile.readLines().takeLast(600) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { activityLog = persisted }
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
        val conversationHistory = messages.mapNotNull { message ->
            when (message.role) {
                AgentMessage.Role.USER -> AgentTurn("user", message.text)
                AgentMessage.Role.FINAL -> AgentTurn("assistant", message.text)
                else -> null
            }
        }.takeLast(12)
        log("INFO", "Nova solicitação: ${task.take(500)}")
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
            runCatching {
                val cfg = settingsRepo.settings.first()
                val localModel = AppSelection.modelPath
                    ?.let(::File)
                    ?.takeIf { it.isFile && it.extension.equals("gguf", true) }
                val backend: AgentLlm
                if (localModel != null) {
                    val manager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                    val modelBytes = localModel.length()
                    val contextSize = when {
                        memory.availMem < 700L * 1024 * 1024 || modelBytes > 2_600_000_000L -> 512
                        memory.availMem < 1_150L * 1024 * 1024 || modelBytes > 1_600_000_000L -> 768
                        else -> 1536
                    }
                    val outputTokens = when (contextSize) { 512 -> 128; 768 -> 192; else -> 384 }
                    val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                    backend = NativeLlmClient(
                        localModel.absolutePath, contextSize, threads, outputTokens, cfg.temperature.toFloat(),
                        onNativeLog = { line -> log("NATIVE", line) },
                    )
                    log("INFO", "Backend nativo llama.cpp: ${localModel.name}, ctx=$contextSize, threads=$threads, RAM livre=${memory.availMem / (1024 * 1024)} MiB")
                } else {
                    backend = LlmClient(cfg.endpoint, cfg.apiKey, cfg.model, cfg.temperature)
                    log("INFO", "Backend remoto OpenAI-compatible: ${LlmClient.describeNormalized(cfg.endpoint)} · modelo=${cfg.model}")
                }
                val agent = ReActAgent(
                    workspace = store.workspace(),
                    lessons = lessons,
                    llm = backend,
                    maxSteps = if (cfg.maxSteps <= 0) 0 else cfg.maxSteps,
                    settings = cfg,
                    history = conversationHistory,
                    onEvent = { event -> viewModelScope.launch(Dispatchers.Main) { handle(event) } },
                )
                agent.run(task)
            }.onFailure { error ->
                log("ERROR", "Falha no agente: ${error.stackTraceToString().take(7000)}")
                add(AgentMessage(++seq, AgentMessage.Role.ERROR, error.message ?: error.javaClass.simpleName))
            }
            flushStream()
            running = false
            status = "pronto"
            elapsedMs = System.currentTimeMillis() - startedAt
            if (completionTokens > 0 && elapsedMs > 0) {
                tokensPerSecond = completionTokens / (elapsedMs / 1000.0)
            }
            log("INFO", "Turno encerrado em ${elapsedMs} ms; tokens prompt=$promptTokens, completion=$completionTokens; throughput=%.2f tok/s".format(tokensPerSecond))
            lessonList = withContext(Dispatchers.IO) { lessons.all() }
        }
    }

    private suspend fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.Step -> {
                flushStream()
                status = if (event.max <= 0) "passo ${event.index} · autônomo" else "passo ${event.index}/${event.max}"
                log("DEBUG", "Passo do agente ${event.index}/${if (event.max <= 0) "∞" else event.max}")
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
                log("METRIC", "Uso de tokens: prompt=${event.prompt}, resposta=${event.completion}")
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
                log("TOOL", "Iniciando ${event.name} args=${event.args.take(2500)}")
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
                log(if (event.ok) "INFO" else "ERROR", "Ferramenta ${event.name}: ok=${event.ok}; saída=${event.output.take(2500)}")
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
                log("INFO", "Resposta final: ${event.text.take(2000)}")
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
                log("ERROR", event.message)
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
        log("WARN", "Interrupção solicitada pelo utilizador")
        LlamaBridge.requestChatStop()
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
            log(if (r.ok) "INFO" else "ERROR", "Terminal: $command · exit=${r.exitCode} · ${r.durationMs} ms · ${r.output.take(1400)}")
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
            log("TOOL", "Git action: $action")
            gitOut = when (action) {
                "status" -> git.status()
                "commit" -> git.commit("checkpoint manual · ${System.currentTimeMillis()}")
                "log" -> git.log(12)
                "reset" -> git.resetHard("HEAD~1")
                "init" -> git.init()
                else -> "ação desconhecida"
            }
            log(if (gitOut.contains("error", true) || gitOut.contains("fatal", true)) "ERROR" else "INFO", "Git $action: ${gitOut.take(1800)}")
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
