package dev.labforge.app.core.native

import android.util.Log

/** Receives native engine output. Called from a native background thread. */
interface NativeCallback {
    fun onLog(line: String)
    fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double)
    fun onToken(token: String) {}
}

/**
 * JNI bridge to the bundled llama.cpp engine.
 *
 * Native capabilities:
 *  - imatrix-aware GGUF quantization (all llama.cpp ftypes)
 *  - on-device fine-tuning through llama_opt_* (full or selective tensors)
 *  - tokenizer statistics (dataset planning)
 */
object LlamaBridge {

    private const val TAG = "LlamaBridge"

    @Volatile
    private var loadError: String? = null

    val isAvailable: Boolean
        get() = loadError == null

    init {
        try {
            System.loadLibrary("labforge")
        } catch (t: Throwable) {
            loadError = t.message ?: "liblabforge.so não encontrada"
            Log.e(TAG, "native load failed", t)
        }
    }

    fun version(): String = runCatching { nativeVersion() }
        .getOrElse { "motor nativo indisponível: ${loadError ?: it.message}" }

    fun quantize(
        input: String,
        output: String,
        ftype: String,
        imatrix: String?,
        threads: Int,
        callback: NativeCallback?,
    ): QuantizeResult {
        if (!isAvailable) return QuantizeResult(false, 0, loadError ?: "nativo indisponível")
        val json = nativeQuantize(input, output, ftype, imatrix, threads, callback)
        return parseQuantize(json)
    }

    fun finetune(
        model: String,
        output: String,
        dataset: String,
        epochs: Int,
        lr: Float,
        nCtx: Int,
        nBatch: Int,
        nUbatch: Int,
        threads: Int,
        filter: String,
        callback: NativeCallback?,
    ): TrainResult {
        if (!isAvailable) return TrainResult(false, 0, 0, 0.0, loadError ?: "nativo indisponível")
        val json = nativeFinetune(model, output, dataset, epochs, lr, nCtx, nBatch, nUbatch, threads, filter, callback)
        return parseTrain(json)
    }

    fun loraTrain(
        model: String,
        output: String,
        dataset: String,
        epochs: Int,
        lr: Float,
        nCtx: Int,
        threads: Int,
        rank: Int,
        alpha: Float,
        targets: String,
        callback: NativeCallback?,
    ): LoraResult {
        if (!isAvailable) return LoraResult(false, 0, 0, 0, 0.0, loadError ?: "nativo indisponível")
        val json = nativeLoraTrain(model, output, dataset, epochs, lr, nCtx, threads, rank, alpha, targets, callback)
        return parseLora(json)
    }

    /** Runs GGUF inference in the bundled llama.cpp engine and streams native tokens. */
    fun generate(
        model: String,
        prompt: String,
        nCtx: Int = 1024,
        nPredict: Int = 256,
        threads: Int = 0,
        temperature: Float = 0.7f,
        callback: NativeCallback? = null,
    ): String {
        check(isAvailable) { loadError ?: "motor nativo indisponível" }
        require(model.isNotBlank() && java.io.File(model).isFile) { "modelo GGUF não encontrado" }
        require(prompt.isNotBlank()) { "prompt vazio" }
        return nativeGenerate(
            model, prompt, nCtx.coerceIn(128, 4096), nPredict.coerceIn(1, 2048),
            threads.coerceAtLeast(0), temperature.coerceIn(0f, 2f), callback,
        )
    }

    fun cancelGeneration() {
        if (isAvailable) runCatching { nativeCancelGeneration() }
    }

    /** Releases the cached mmap-backed model after the user switches models. */
    fun releaseModel() {
        if (isAvailable) runCatching { nativeReleaseModel() }
    }

    /** Reports actual registered llama.cpp devices; does not guess GPU support. */
    fun backendInfo(): BackendInfo = runCatching {
        val json = org.json.JSONObject(nativeBackendInfo())
        BackendInfo(
            gpuOffload = json.optBoolean("gpu_offload", false),
            deviceCount = json.optInt("device_count", 0),
        )
    }.getOrElse { BackendInfo(false, 0) }

    data class BackendInfo(val gpuOffload: Boolean, val deviceCount: Int) {
        val selectedBackend: String get() = if (gpuOffload) "GPU (llama.cpp backend)" else "CPU"
    }

    /** Token count using the GGUF's own vocabulary (native, no network). */
    fun countTokens(model: String, text: String): Int {
        if (!isAvailable || model.isBlank() || text.isBlank()) return 0
        return runCatching { nativeCountTokens(model, text) }.getOrDefault(0)
    }

    fun tokenStats(model: String, text: String, nCtx: Int): TokenStats {
        if (!isAvailable) return TokenStats(0, 0, 0, 0)
        val a = nativeTokenStats(model, text, nCtx)
        return TokenStats(a.getOrElse(0) { 0 }, a.getOrElse(1) { 0 }, a.getOrElse(2) { 0 }, a.getOrElse(3) { 0 })
    }

    data class QuantizeResult(val ok: Boolean, val bytes: Long, val error: String?)
    data class TrainResult(val ok: Boolean, val tokens: Long, val sequences: Long, val loss: Double, val error: String?)
    data class LoraResult(val ok: Boolean, val tokens: Long, val sequences: Long, val tensors: Long, val loss: Double, val error: String?)
    data class TokenStats(val tokens: Long, val sequences: Long, val vocab: Long, val params: Long)

    private fun parseQuantize(json: String): QuantizeResult = runCatching {
        val o = org.json.JSONObject(json)
        QuantizeResult(o.optBoolean("ok"), o.optLong("bytes"), o.optString("error").ifBlank { null })
    }.getOrElse { QuantizeResult(false, 0, "resposta nativa inválida: $json") }

    private fun parseLora(json: String): LoraResult = runCatching {
        val o = org.json.JSONObject(json)
        LoraResult(
            o.optBoolean("ok"), o.optLong("tokens"), o.optLong("sequences"),
            o.optLong("tensors"), o.optDouble("loss"), o.optString("error").ifBlank { null },
        )
    }.getOrElse { LoraResult(false, 0, 0, 0, 0.0, "resposta nativa inválida: $json") }

    private fun parseTrain(json: String): TrainResult = runCatching {
        val o = org.json.JSONObject(json)
        TrainResult(
            o.optBoolean("ok"), o.optLong("tokens"), o.optLong("sequences"),
            o.optDouble("loss"), o.optString("error").ifBlank { null },
        )
    }.getOrElse { TrainResult(false, 0, 0, 0.0, "resposta nativa inválida: $json") }

    private external fun nativeVersion(): String
    private external fun nativeGenerate(
        model: String, prompt: String, nCtx: Int, nPredict: Int, threads: Int,
        temperature: Float, cb: NativeCallback?,
    ): String
    private external fun nativeCancelGeneration()
    private external fun nativeReleaseModel()
    private external fun nativeBackendInfo(): String
    private external fun nativeQuantize(
        input: String, output: String, ftype: String, imatrix: String?,
        threads: Int, cb: NativeCallback?,
    ): String

    private external fun nativeFinetune(
        model: String, output: String, dataset: String,
        epochs: Int, lr: Float, nCtx: Int, nBatch: Int, nUbatch: Int,
        threads: Int, filter: String, cb: NativeCallback?,
    ): String

    private external fun nativeLoraTrain(
        model: String, output: String, dataset: String,
        epochs: Int, lr: Float, nCtx: Int, threads: Int,
        rank: Int, alpha: Float, targets: String, cb: NativeCallback?,
    ): String

    private external fun nativeCountTokens(model: String, text: String): Int

    private external fun nativeTokenStats(model: String, text: String, nCtx: Int): LongArray
}
