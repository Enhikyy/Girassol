package com.enhikyy.girassol.ai

import com.enhikyy.girassol.AppLogger
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
            val ok = withContext(Dispatchers.Default + exceptionHandler) {
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
                // Llamatik não expõe releaseModel direto no LlamaBridge; sessionReset limpa o KV cache
                LlamaBridge.sessionReset()
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
