package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llamatik.sdk.agent.AgentToolResult
import com.llamatik.sdk.framework.AgentStreamChunk
import dev.labforge.app.integration.llamatik.LlamatikRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LlamatikViewModel(app: Application) : AndroidViewModel(app) {

    var initialized by mutableStateOf(false)
        private set
    var tools by mutableStateOf<List<String>>(emptyList())
        private set
    var memoryCount by mutableStateOf(0)
        private set
    var streaming by mutableStateOf(false)
        private set
    var output by mutableStateOf("")
        private set
    var status by mutableStateOf("inicializando…")
        private set
    var toolResult by mutableStateOf<String?>(null)
        private set
    var testingTool by mutableStateOf(false)
        private set

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                LlamatikRuntime.init(app)
                LlamatikRuntime.restoreMemory()
            }.onSuccess {
                initialized = true
                tools = LlamatikRuntime.toolIds()
                memoryCount = LlamatikRuntime.memorySize()
                status = "pronto · ${tools.size} ferramentas"
            }.onFailure {
                status = "falha: ${it.message}"
            }
        }
    }

    fun stream(prompt: String) {
        if (!initialized || streaming || prompt.isBlank()) return
        streaming = true
        output = ""
        status = "gerando…"
        streamJob = viewModelScope.launch {
            var done = false
            try {
                LlamatikRuntime.stream(prompt).collect { chunk ->
                    when (chunk) {
                        is AgentStreamChunk.Delta -> output += chunk.text
                        is AgentStreamChunk.ToolCallStarted -> {
                            output += "\n\n[tool ${chunk.toolId}]\n"
                        }
                        is AgentStreamChunk.ToolResult -> {
                            output += "[${if (chunk.success) "ok" else "erro"}] ${chunk.summary.take(600)}\n"
                        }
                        is AgentStreamChunk.Done -> {
                            if (output.isBlank()) output = chunk.final
                            done = true
                        }
                        is AgentStreamChunk.Failure -> {
                            output += "\n[erro] ${chunk.message}"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                output += "\n[erro] ${t.message}"
            }
            if (!done && output.isBlank()) output = "(sem resposta)"
            memoryCount = LlamatikRuntime.memorySize()
            streaming = false
            status = "pronto"
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        streaming = false
        status = "interrompido"
    }

    fun testRestApi() {
        if (!initialized || testingTool) return
        testingTool = true
        toolResult = "chamando jsonplaceholder.typicode.com…"
        viewModelScope.launch {
            val result = runCatching { LlamatikRuntime.testPublicApi() }
                .getOrElse { AgentToolResult.Failure(it.message ?: "falha") }
            toolResult = when (result) {
                is AgentToolResult.Success -> result.outputSummary
                is AgentToolResult.Failure -> "erro: ${result.errorMessage}"
                AgentToolResult.PermissionDenied -> "permissão negada"
                AgentToolResult.Unsupported -> "não suportado"
            }
            memoryCount = LlamatikRuntime.memorySize()
            testingTool = false
        }
    }

    fun consolidate() {
        if (!initialized) return
        viewModelScope.launch {
            status = "consolidando memória…"
            val msg = runCatching { LlamatikRuntime.consolidate() }
                .getOrElse { "falha: ${it.message}" }
            memoryCount = LlamatikRuntime.memorySize()
            status = msg
        }
    }

    fun clearOutput() {
        output = ""
        status = "pronto"
    }
}
