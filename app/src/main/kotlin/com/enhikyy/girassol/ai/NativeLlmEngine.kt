package com.enhikyy.girassol.ai

import com.enhikyy.girassol.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Motor de inferência LLM 100% nativo via JNI.
 *
 * Não depende de bibliotecas externas - usa implementação C++ própria.
 * Carrega modelos GGUF e fornece geração de texto e embeddings.
 */
class NativeLlmEngine(
    private val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        AppLogger.log("ERROR", "LLM_ENGINE", "Exceção não tratada: ${e.message}", e)
    }
) : LlmEngine {

    companion object {
        private const val TAG = "NativeLlmEngine"

        init {
            runCatching {
                System.loadLibrary("girassol-llm")
                AppLogger.log("INFO", TAG, "Biblioteca nativa girassol-llm carregada")
            }.onFailure {
                AppLogger.log("ERROR", TAG, "Falha ao carregar biblioteca nativa: ${it.message}", it)
            }
        }

        @Volatile
        private var libraryAvailable: Boolean = true
    }

    @Volatile
    private var _isLoaded = false

    @Volatile
    private var _modelPath: String? = null

    override val isLoaded: Boolean
        get() = _isLoaded

    override val modelPath: String?
        get() = _modelPath

    // Métodos nativos
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeGenerateStream(prompt: String, maxTokens: Int, callback: StreamCallback)
    private external fun nativeEmbed(text: String): FloatArray

    /** Interface de callback para streaming. */
    interface StreamCallback {
        fun onDelta(text: String)
        fun onComplete()
        fun onError(message: String)
    }

    override suspend fun load(modelPath: String): Boolean {
        val file = File(modelPath)
        if (!file.exists()) {
            val msg = "Arquivo do modelo não encontrado: $modelPath"
            AppLogger.log("ERROR", TAG, msg)
            throw ModelLoadException(msg)
        }

        val modelSizeMB = file.length() / (1024 * 1024)
        AppLogger.log("INFO", TAG, "Iniciando carga do modelo: ${file.name} ($modelSizeMB MB)")

        val startTime = System.nanoTime()

        return try {
            val ok = withContext(Dispatchers.Default + exceptionHandler) {
                nativeLoadModel(modelPath)
            }

            val loadTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

            if (ok) {
                _isLoaded = true
                _modelPath = modelPath
                AppLogger.log("INFO", TAG,
                    "Modelo carregado: ${file.name} ($modelSizeMB MB) em $loadTimeMs ms")
                true
            } else {
                val msg = "Falha ao carregar modelo: $modelPath"
                AppLogger.log("ERROR", TAG, msg)
                throw ModelLoadException(msg)
            }
        } catch (e: ModelLoadException) {
            _isLoaded = false
            _modelPath = null
            throw e
        } catch (e: Exception) {
            val loadTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            AppLogger.log("ERROR", TAG,
                "Exceção ao carregar após $loadTimeMs ms: ${e.message}", e)
            _isLoaded = false
            _modelPath = null
            throw ModelLoadException("Falha ao carregar: ${e.message}", e)
        }
    }

    override suspend fun generate(prompt: String, onToken: (String) -> Unit): Boolean {
        if (!_isLoaded) {
            val msg = "Tentativa de gerar sem modelo carregado"
            AppLogger.log("WARN", TAG, msg)
            throw GenerationException(msg)
        }

        if (prompt.trim().isEmpty()) {
            throw GenerationException("Prompt vazio")
        }

        val genStartTime = System.nanoTime()
        var tokensGenerated = 0

        return try {
            withContext(Dispatchers.Default + exceptionHandler) {
                val callback = object : StreamCallback {
                    override fun onDelta(text: String) {
                        tokensGenerated++
                        onToken(text)
                    }

                    override fun onComplete() {
                        val genTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStartTime)
                        val tokPerSec = if (genTimeMs > 0) tokensGenerated * 1000.0 / genTimeMs else 0.0
                        AppLogger.log("INFO", TAG,
                            "Geração: $tokensGenerated tokens em $genTimeMs ms (${"%.2f".format(tokPerSec)} tok/s)")
                    }

                    override fun onError(message: String) {
                        AppLogger.log("ERROR", TAG, "Erro no streaming: $message")
                    }
                }

                nativeGenerateStream(prompt, 256, callback)
            }
            true
        } catch (e: Exception) {
            val genTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStartTime)
            AppLogger.log("ERROR", TAG, "Falha na geração após $genTimeMs ms: ${e.message}", e)
            throw GenerationException("Falha na geração: ${e.message}", e)
        }
    }

    /** Gera embedding para texto (síncrono, usar com cuidado). */
    fun embed(text: String): FloatArray {
        if (!_isLoaded) {
            return FloatArray(384) { 0f }
        }
        return nativeEmbed(text)
    }

    override fun close() {
        if (_isLoaded) {
            AppLogger.log("INFO", TAG, "Liberando modelo: $_modelPath")
            runCatching {
                nativeUnloadModel()
            }.onFailure {
                AppLogger.log("WARN", TAG, "Erro ao descarregar: ${it.message}", it)
            }
            _isLoaded = false
            _modelPath = null
            AppLogger.log("INFO", TAG, "Modelo liberado")
        }
    }
}
