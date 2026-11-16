package dev.labforge.app.service

import android.app.ActivityManager
import android.content.Context
import kotlin.math.max
import kotlin.math.min

/** RAM budgeting and graceful pressure handling for native jobs. */
object MemoryGuard {

    data class Budget(
        val ok: Boolean,
        val availableGb: Double,
        val requiredGb: Double,
        val message: String,
    )

    fun availableGb(context: Context): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / 1e9
    }

    fun totalGb(context: Context): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / 1e9
    }

    /**
     * @param mode "quantize" | "lora" | "full"
     */
    fun check(context: Context, modelBytes: Long, mode: String): Budget {
        val avail = availableGb(context)
        val modelGb = modelBytes / 1e9
        val factor = when (mode) {
            "quantize" -> 1.25
            "lora" -> 1.6
            else -> 4.0
        }
        val required = modelGb * factor + 0.4
        val ok = avail >= required * 0.85
        val message = when {
            ok -> "RAM ok: %.1f GB livres, ~%.1f GB necessários".format(avail, required)
            mode == "full" -> "RAM insuficiente (%.1f GB livres / ~%.1f GB). Use LoRA ou reduza o contexto.".format(avail, required)
            else -> "RAM apertada (%.1f GB livres / ~%.1f GB). O sistema pode encerrar o job; feche outros apps.".format(avail, required)
        }
        return Budget(ok, avail, required, message)
    }

    /**
     * Conservative preflight for mmap-backed local inference.
     * GGUF file size is a lower-bound proxy for resident weights; KV cache and
     * runtime buffers are budgeted separately. This prevents predictable LMK kills,
     * but the native loader remains the final authority.
     */
    fun checkInference(context: Context, modelBytes: Long, contextTokens: Int): Budget {
        val avail = availableGb(context)
        val modelGb = modelBytes.coerceAtLeast(0L) / 1e9
        val kvAndRuntimeGb = 0.30 + contextTokens.coerceIn(128, 4096) * 0.00030
        val required = modelGb * 1.08 + kvAndRuntimeGb
        val ok = avail >= required
        val message = if (ok) {
            "RAM estimada suficiente: %.2f GB livres; reserva prevista %.2f GB".format(avail, required)
        } else {
            "Memória insuficiente para iniciar com segurança: %.2f GB livres; estimativa mínima %.2f GB. Reduza o contexto ou use um GGUF menor/mais quantizado.".format(avail, required)
        }
        return Budget(ok, avail, required, message)
    }

    /** Best-effort memory release before a heavy allocation. */
    fun trim() {
        runCatching { Runtime.getRuntime().gc() }
    }

    fun fitsContext(params: Long, ctx: Int, availableGb: Double): Int {
        // rough KV cache estimate: 2 * n_layer * n_kv_head * head_dim * ctx * 2 bytes
        // without arch details, scale the requested context down if RAM is tight
        val gbNeeded = params / 1e9 * 0.6 + ctx / 512.0 * 0.15
        if (gbNeeded <= availableGb * 0.8) return ctx
        val scale = max(0.25, min(1.0, (availableGb * 0.8) / max(0.1, gbNeeded)))
        return (ctx * scale).toInt().coerceAtLeast(128)
    }
}
