package dev.labforge.app.core.native

import android.util.Log

/** Receives native engine output. Called from a native background thread. */
interface NativeCallback {
    fun onLog(line: String)
    fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double)
}

/** Streaming callback for local llama.cpp inference. */
interface NativeChatCallback {
    fun onToken(piece: String)
    fun onLog(line: String)
    fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double)
}

data class NativeChatResult(
    val ok: Boolean,
    val promptTokens: Int,
    val completionTokens: Int,
    val elapsedMs: Long,
    val error: String?,
)

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

    /** Requests stop after the current native training epoch; the latest LoRA checkpoint remains on disk. */
    fun requestTrainingStop() {
        if (isAvailable) runCatching { nativeRequestTrainingStop() }
    }

    /** Interrupts an in-flight local generation without killing the app process. */
    fun requestChatStop() {
        if (isAvailable) runCatching { nativeRequestChatStop() }
    }

    /** Runs a full local inference pass against the selected GGUF model. */
    fun generateLocal(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        nCtx: Int,
        threads: Int,
        maxTokens: Int,
        temperature: Float,
        callback: NativeChatCallback?,
    ): NativeChatResult {
        if (!isAvailable) return NativeChatResult(false, 0, 0, 0, loadError ?: "motor nativo indisponível")
        if (model.isBlank() || systemPrompt.isBlank() || userPrompt.isBlank()) {
            return NativeChatResult(false, 0, 0, 0, "modelo ou prompt vazio")
        }
        val json = runCatching {
            nativeGenerateLocal(
                model, systemPrompt, userPrompt, nCtx.coerceIn(256, 4096),
                threads.coerceIn(1, 8), maxTokens.coerceIn(1, 2048),
                temperature.coerceIn(0f, 2f), callback,
            )
        }.getOrElse {
            return NativeChatResult(false, 0, 0, 0, it.message ?: it.javaClass.simpleName)
        }
        return runCatching {
            val o = org.json.JSONObject(json)
            NativeChatResult(
                o.optBoolean("ok"), o.optInt("prompt_tokens"), o.optInt("completion_tokens"),
                o.optLong("elapsed_ms"), o.optString("error").ifBlank { null },
            )
        }.getOrElse { NativeChatResult(false, 0, 0, 0, "resposta nativa inválida: $json") }
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
    private external fun nativeRequestTrainingStop()
    private external fun nativeRequestChatStop()
    private external fun nativeGenerateLocal(
        model: String, systemPrompt: String, userPrompt: String,
        nCtx: Int, threads: Int, maxTokens: Int, temperature: Float,
        callback: NativeChatCallback?,
    ): String
}
