package dev.labforge.app.core.native

import android.util.Log

/** Receives native engine output. Called from a native background thread. */
interface NativeCallback {
    fun onLog(line: String)
    fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double)
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

    @Synchronized
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

    @Synchronized
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

    @Synchronized
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

    /** Token count using the GGUF's own vocabulary (native, no network). */
    @Synchronized
    fun countTokens(model: String, text: String): Int {
        if (!isAvailable || model.isBlank() || text.isBlank()) return 0
        return runCatching { nativeCountTokens(model, text) }.getOrDefault(0)
    }

    @Synchronized
    fun tokenStats(model: String, text: String, nCtx: Int): TokenStats {
        if (!isAvailable) return TokenStats(0, 0, 0, 0)
        val a = nativeTokenStats(model, text, nCtx)
        return TokenStats(a.getOrElse(0) { 0 }, a.getOrElse(1) { 0 }, a.getOrElse(2) { 0 }, a.getOrElse(3) { 0 })
    }

    private val inferenceLoaded by lazy { runCatching { System.loadLibrary("girassol_inference") } }

    @Synchronized
    fun generate(model: String, messages: String, adapter: String, context: Int,
                 temperature: Float, callback: GenerationCallback,
                 threads: Int = Runtime.getRuntime().availableProcessors(), maxTokens: Int = 512): String {
        check(isAvailable) { "Motor nativo indisponível" }
        inferenceLoaded.getOrElse { throw IllegalStateException("Inferência nativa indisponível: ${it.message}", it) }
        return nativeGenerate(model.toByteArray(), messages.toByteArray(), adapter.toByteArray(),
            context.coerceIn(1024, 32768), maxTokens.coerceIn(1, 4096), threads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),
            temperature, callback)?.toString(Charsets.UTF_8) ?: error("Geração interrompida")
    }

    private external fun nativeGenerate(model: ByteArray, messages: ByteArray, adapter: ByteArray,
        context: Int, maxTokens: Int, threads: Int, temperature: Float, callback: GenerationCallback): ByteArray?

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

interface GenerationCallback { fun onToken(bytes: ByteArray): Boolean }
