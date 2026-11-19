package com.enhikyy.girassol.ai

import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.MyApplication
import com.llamatik.core.platform.LlamaBridge
import com.llamatik.core.platform.GenStream
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Motor de inferência LLM on-device usando Llamatik (llama.cpp).
 *
 * Interface mínima:
 * - suspend load(path): carrega modelo GGUF
 * - generate(prompt, onToken): streaming token-a-token
 * - close(): libera recursos nativos
 *
 * Toda a execução pesada roda em Dispatchers.Default (fora da thread principal).
 */
interface LlmEngine {
    /** Modelo está carregado e pronto. */
    val isLoaded: Boolean
    /** Caminho do modelo carregado (para logs). */
    val modelPath: String?
    suspend fun load(modelPath: String): Boolean
    suspend fun generate(prompt: String, onToken: (String) -> Unit): Boolean
    suspend fun generateChat(messages: List<Pair<String, String>>, onToken: (String) -> Unit): Boolean {
        val prompt = messages.joinToString("\\n") { (role, content) -> "<|$role|>\\n$content" } + "\\n<|assistant|>"
        return generate(prompt, onToken)
    }
    suspend fun generateStructured(prompt: String, jsonSchema: String, onToken: (String) -> Unit): Boolean =
        generate(prompt, onToken)
    fun close()
}

/** Exceção de falha de carregamento do modelo. */
class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Exceção de falha de geração. */
class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Implementação real com Llamatik. */
class LlamatikEngine(
    private val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        AppLogger.log("ERROR", "LLM_ENGINE", "Exceção não tratada: ${e.message}", e)
    }
) : LlmEngine {

    /** Modelo está carregado e pronto. */
    @Volatile
    private var _isLoaded = false

    /** Caminho do modelo carregado (para logs). */
    @Volatile
    private var _modelPath: String? = null

    override val isLoaded: Boolean
        get() = _isLoaded

    override val modelPath: String?
        get() = _modelPath

    override suspend fun load(modelPath: String): Boolean {
        val file = File(modelPath)
        if (!file.exists()) {
            val msg = "Arquivo do modelo não encontrado: $modelPath"
            AppLogger.log("ERROR", "LLM_ENGINE", msg)
            throw ModelLoadException(msg)
        }

        val modelSizeMB = file.length() / (1024 * 1024)

        AppLogger.log("INFO", "LLM_ENGINE", "Iniciando carga do modelo: ${file.name} ($modelSizeMB MB)")

        val startTime = System.nanoTime()

        return try {
            // Executa em thread de background
            val budget = MyApplication.getInstance().runtimeGovernor.evaluateCurrentBudget()
            AppLogger.log(
                "INFO",
                "LLM_ENGINE",
                "Orçamento: RAM livre=${budget.availableMemoryMb}MB contexto=${budget.contextLength} " +
                    "tokens=${budget.maxOutputTokens} threads=${budget.numThreads} " +
                    "batch=${budget.batchSize} thermal=${budget.thermalStatusLabel}"
            )
            val ok = withContext(Dispatchers.Default + exceptionHandler) {
                LlamaBridge.updateGenerateParams(
                    temperature = 0.7f,
                    maxTokens = budget.maxOutputTokens,
                    topP = 0.95f,
                    topK = 40,
                    repeatPenalty = 1.1f,
                    contextLength = budget.contextLength,
                    numThreads = budget.numThreads,
                    useMmap = budget.useMmap,
                    flashAttention = budget.flashAttention,
                    batchSize = budget.batchSize,
                    gpuLayers = 0
                )
                LlamaBridge.initGenerateModel(modelPath)
            }

            val loadTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

            if (ok) {
                _isLoaded = true
                _modelPath = modelPath
                AppLogger.log("INFO", "LLM_ENGINE",
                    "Modelo carregado com sucesso: ${file.name} ($modelSizeMB MB) em $loadTimeMs ms")
                true
            } else {
                val msg = "LlamaBridge.initGenerateModel retornou false para: $modelPath"
                AppLogger.log("ERROR", "LLM_ENGINE", msg)
                throw ModelLoadException(msg)
            }
        } catch (e: ModelLoadException) {
            _isLoaded = false
            _modelPath = null
            throw e
        } catch (e: Exception) {
            val loadTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            AppLogger.log("ERROR", "LLM_ENGINE",
                "Falha ao carregar modelo após $loadTimeMs ms: ${e.message}", e)
            _isLoaded = false
            _modelPath = null
            throw ModelLoadException("Falha ao carregar modelo: ${e.message}", e)
        }
    }

    override suspend fun generateChat(
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit
    ): Boolean {
        if (!_isLoaded) throw GenerationException("Tentativa de gerar chat sem modelo carregado")
        val prompt = LlamaBridge.applyChatTemplate(messages, addAssistantPrefix = true)
            ?: messages.joinToString("\\n") { (role, content) -> "<|$role|>\\n$content" } + "\\n<|assistant|>"
        return generate(prompt, onToken)
    }

    override suspend fun generateStructured(
        prompt: String,
        jsonSchema: String,
        onToken: (String) -> Unit
    ): Boolean {
        if (!_isLoaded) throw GenerationException("Tentativa de gerar JSON sem modelo carregado")
        if (prompt.isBlank()) throw GenerationException("Prompt vazio")
        val startedAt = System.nanoTime()
        var tokenCount = 0
        var streamError: String? = null
        return try {
            withContext(Dispatchers.Default + exceptionHandler) {
                LlamaBridge.generateJsonStream(prompt, jsonSchema, object : GenStream {
                    override fun onDelta(text: String) {
                        tokenCount++
                        onToken(text)
                    }
                    override fun onComplete() {
                        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                        val rate = if (elapsed > 0) tokenCount * 1000.0 / elapsed else 0.0
                        AppLogger.log("INFO", "LLM_ENGINE", "JSON stream: $tokenCount tokens, ${"%.2f".format(rate)} tok/s")
                    }
                    override fun onError(message: String) {
                        streamError = message
                    }
                })
            }
            streamError?.let { throw GenerationException("Erro no JSON stream: $it") }
            true
        } catch (error: Exception) {
            AppLogger.log("ERROR", "LLM_ENGINE", "Falha no JSON stream: ${error.message}", error)
            throw if (error is GenerationException) error else GenerationException("Falha no JSON stream", error)
        }
    }

    override suspend fun generate(prompt: String, onToken: (String) -> Unit): Boolean {
        if (!_isLoaded) {
            val msg = "Tentativa de gerar sem modelo carregado"
            AppLogger.log("WARN", "LLM_ENGINE", msg)
            throw GenerationException(msg)
        }

        if (prompt.trim().isEmpty()) {
            throw GenerationException("Prompt vazio")
        }

        val genStartTime = System.nanoTime()
        var tokensGenerated = 0
        var streamError: String? = null

        return try {
            withContext(Dispatchers.Default + exceptionHandler) {
                val callback = object : GenStream {
                    override fun onDelta(text: String) {
                        tokensGenerated++
                        onToken(text)
                    }

                    override fun onComplete() {
                        val genTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStartTime)
                        val tokPerSec = if (genTimeMs > 0) tokensGenerated * 1000.0 / genTimeMs else 0.0
                        AppLogger.log("INFO", "LLM_ENGINE",
                            "Geração concluída: $tokensGenerated tokens em $genTimeMs ms (${String.format("%.2f", tokPerSec)} tok/s)")
                    }

                    override fun onError(message: String) {
                        streamError = message
                    }
                }

                LlamaBridge.generateStream(prompt, callback)
            }

            streamError?.let { throw GenerationException("Erro na geração: $it") }
            true
        } catch (e: GenerationException) {
            AppLogger.log("ERROR", "LLM_ENGINE", "Geração falhou: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            val genTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStartTime)
            AppLogger.log("ERROR", "LLM_ENGINE",
                "Exceção na geração após $genTimeMs ms: ${e.message}", e)
            throw GenerationException("Falha na geração: ${e.message}", e)
        }
    }

    override fun close() {
        if (_isLoaded) {
            AppLogger.log("INFO", "LLM_ENGINE", "Liberando modelo: $_modelPath")
            try {
                // shutdown() libera o modelo e os recursos nativos; não apenas o KV cache.
                LlamaBridge.shutdown()
            } catch (e: Exception) {
                AppLogger.log("WARN", "LLM_ENGINE", "Erro ao limpar sessão: ${e.message}", e)
            }
            _isLoaded = false
            _modelPath = null
            AppLogger.log("INFO", "LLM_ENGINE", "Modelo liberado")
        }
    }
}

/** Implementação nula (no-op) para quando não há modelo. */
class NullLlmEngine : LlmEngine {
    override suspend fun load(modelPath: String): Boolean = false
    override suspend fun generate(prompt: String, onToken: (String) -> Unit): Boolean = false
    override fun close() {}
    override val isLoaded: Boolean get() = false
    override val modelPath: String? get() = null
}
