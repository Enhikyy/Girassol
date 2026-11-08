package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.Handoff
import dev.labforge.app.core.agent.AgentEvent
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.agent.ReActAgent
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.term.GitRepo
import dev.labforge.app.core.term.ShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Autonomous coding agent ("Codex" tab): plan → edit → test → fix, with an
 * optional unbounded retry loop until the test command exits 0.
 */
class CodexViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ModelStore(app)
    private val lessons = LessonStore(store.agentDir)
    private val shell = ShellSession(store.workspace())
    private val git = GitRepo(store.workspace())
    private val settingsRepo = SettingsRepository(app)

    var messages by mutableStateOf<List<AgentMessage>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("pronto")
        private set
    var liveText by mutableStateOf("")
        private set
    var diff by mutableStateOf("")
        private set
    var testOutput by mutableStateOf("")
        private set
    var iteration by mutableStateOf(0)
        private set
    var totalPromptTokens by mutableStateOf(0)
        private set
    var totalCompletionTokens by mutableStateOf(0)
        private set
    var elapsedMs by mutableStateOf(0L)
        private set

    var prefill by mutableStateOf("")
        private set

    private var job: Job? = null
    private var seq = 0L
    private var startedAt = 0L

    fun consumePrefill(): String {
        val p = prefill
        prefill = ""
        return p
    }

    fun request(text: String) { prefill = text }

    fun start(task: String, testCommand: String, autoTest: Boolean, unlimited: Boolean, maxIterations: Int) {
        if (running || task.isBlank()) return
        running = true
        status = "planejando…"
        liveText = ""
        diff = ""
        testOutput = ""
        iteration = 0
        totalPromptTokens = 0
        totalCompletionTokens = 0
        startedAt = System.currentTimeMillis()
        messages = listOf(AgentMessage(++seq, AgentMessage.Role.USER, task))

        job = viewModelScope.launch {
            val cfg = settingsRepo.settings.first()
            val limit = if (unlimited) 0 else maxIterations.coerceAtLeast(1)
            var currentTask = task
            var rounds = 0

            while (true) {
                rounds++
                iteration = rounds
                val agent = ReActAgent(
                    workspace = store.workspace(),
                    lessons = lessons,
                    llm = LlmClient(cfg.endpoint, cfg.apiKey, cfg.model, cfg.temperature),
                    maxSteps = 0,
                    systemOverride = ReActAgent.CODEX_SYSTEM,
                    settings = cfg,
                    onEvent = { event -> viewModelScope.launch(Dispatchers.Main) { handle(event) } },
                )
                val summary = agent.run(currentTask)
                if (summary.isBlank()) {
                    messages = messages + AgentMessage(
                        ++seq, AgentMessage.Role.ERROR,
                        "O agente não conseguiu concluir esta iteração (modelo/API). Loop interrompido.",
                    )
                    finish()
                    return@launch
                }
                if (!autoTest || testCommand.isBlank()) {
                    finish()
                    return@launch
                }

                status = "rodando testes…"
                val r = shell.run(testCommand, 300)
                testOutput = r.output
                messages = messages + AgentMessage(
                    ++seq, AgentMessage.Role.TOOL,
                    r.output.take(6000) + "\n[exit ${r.exitCode}]",
                    toolName = "testes: $testCommand",
                    ok = r.ok,
                )
                if (r.ok) {
                    messages = messages + AgentMessage(
                        ++seq, AgentMessage.Role.SYSTEM,
                        "Testes passaram (exit 0). Iterações: $rounds.",
                    )
                    finish()
                    return@launch
                }
                if (limit != 0 && rounds >= limit) {
                    messages = messages + AgentMessage(
                        ++seq, AgentMessage.Role.ERROR,
                        "Limite de $limit iterações atingido sem testes verdes.",
                    )
                    finish()
                    return@launch
                }

                currentTask = """
A execução anterior terminou com testes falhando (exit ${r.exitCode}).
Saída:
${r.output.take(5000)}

Continue o trabalho: diagnostique, corrija os arquivos e rode os testes novamente.
Resumo do que já foi feito: ${summary.take(1200)}
""".trimIndent()
                messages = messages + AgentMessage(
                    ++seq, AgentMessage.Role.SYSTEM,
                    "Nova iteração $rounds: corrigindo a falha dos testes.",
                )
            }
        }
    }

    private suspend fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.Step -> status = "passo ${event.index} · autônomo"
            is AgentEvent.Token -> {
                liveText = (liveText + event.text).takeLast(420)
            }
            is AgentEvent.Usage -> {
                totalPromptTokens += event.prompt
                totalCompletionTokens += event.completion
            }
            is AgentEvent.Thought -> {
                if (event.text.isNotBlank()) {
                    messages = messages + AgentMessage(++seq, AgentMessage.Role.THOUGHT, event.text)
                }
            }
            is AgentEvent.ToolStart -> {
                liveText = ""
                messages = messages + AgentMessage(
                    ++seq, AgentMessage.Role.TOOL, "executando…",
                    toolName = event.name, toolArgs = event.args, pending = true,
                )
            }
            is AgentEvent.ToolEnd -> {
                val idx = messages.indexOfLast { it.role == AgentMessage.Role.TOOL && it.pending }
                if (idx >= 0) {
                    messages = messages.toMutableList().also {
                        it[idx] = it[idx].copy(text = event.output, ok = event.ok, pending = false)
                    }
                }
            }
            is AgentEvent.Final -> {
                liveText = ""
                messages = messages + AgentMessage(++seq, AgentMessage.Role.FINAL, event.text)
            }
            is AgentEvent.Failure -> {
                liveText = ""
                messages = messages + AgentMessage(++seq, AgentMessage.Role.ERROR, event.message)
            }
        }
    }

    private suspend fun finish() {
        status = "atualizando git…"
        diff = withContext(Dispatchers.IO) {
            runCatching { git.status() + "\n\n" + git.diff() }.getOrElse { it.message ?: "" }
        }
        elapsedMs = System.currentTimeMillis() - startedAt
        running = false
        status = "pronto"
        liveText = ""
    }

    fun stop() {
        job?.cancel()
        running = false
        status = "interrompido"
    }

    fun clear() {
        stop()
        messages = emptyList()
        diff = ""
        testOutput = ""
        liveText = ""
        iteration = 0
    }

    fun shareToChat(): String {
        val last = messages.lastOrNull { it.role == AgentMessage.Role.FINAL }?.text
            ?: messages.lastOrNull()?.text.orEmpty()
        val summary = buildString {
            appendLine("Resumo do Codex (iterações: $iteration):")
            appendLine(last.take(3000))
            if (diff.isNotBlank()) {
                appendLine()
                appendLine("git status/diff:")
                appendLine(diff.take(2000))
            }
        }
        Handoff.toChat = summary
        return summary
    }

    fun refreshDiff() {
        viewModelScope.launch {
            diff = withContext(Dispatchers.IO) {
                runCatching { git.status() + "\n\n" + git.diff() }.getOrElse { it.message ?: "" }
            }
        }
    }
}
