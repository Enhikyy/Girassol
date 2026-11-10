package app.girassol.core.ai

import com.llamatik.library.platform.LlamaBridge
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Real GGUF inference on-device through Llamatik/llama.cpp; no network calls. */
class LocalLlmEngine {
    private val mutex = Mutex()
    private var loadedPath: String? = null

    val activeModel: String? get() = loadedPath

    suspend fun generate(
        modelPath: String,
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val model = File(modelPath).canonicalFile
            require(model.isFile && model.extension.equals("gguf", true)) { "Modelo GGUF não encontrado" }
            if (loadedPath != model.path) {
                LlamaBridge.shutdown()
                check(LlamaBridge.initGenerateModel(model.path)) { "O motor nativo recusou o modelo ou não há memória suficiente" }
                LlamaBridge.updateGenerateParams(temperature = 0.65f, maxTokens = 768, topP = 0.92f, topK = 48, repeatPenalty = 1.08f)
                loadedPath = model.path
            }
            suspendCancellableCoroutine { continuation ->
                val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                LlamaBridge.generateWithContextStream(
                    system = system,
                    context = context,
                    user = user,
                    onDelta = onDelta,
                    onDone = { if (resumed.compareAndSet(false, true) && continuation.isActive) continuation.resume(Unit) },
                    onError = { message ->
                        if (resumed.compareAndSet(false, true) && continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                    },
                )
                continuation.invokeOnCancellation {
                    resumed.set(true)
                    LlamaBridge.nativeCancelGenerate()
                }
            }
        }
    }

    fun cancel() = LlamaBridge.nativeCancelGenerate()
    fun close() {
        runCatching { LlamaBridge.shutdown() }
        loadedPath = null
    }
}
