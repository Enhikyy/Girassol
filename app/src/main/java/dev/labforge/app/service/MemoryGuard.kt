package dev.labforge.app.service

import android.app.ActivityManager
import android.content.Context
import kotlin.math.max
import kotlin.math.min

/** Conservative native-job budgeting. A refusal is preferable to Android killing the process. */
object MemoryGuard {
    data class Budget(
        val ok: Boolean,
        val availableGb: Double,
        val requiredGb: Double,
        val message: String,
        val totalGb: Double = 0.0,
        val reserveGb: Double = 0.0,
    )

    data class LoraPlan(
        val budget: Budget,
        val nCtx: Int,
        val rank: Int,
        val threads: Int,
        val targets: String,
        val adjusted: Boolean,
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

    /** Pure estimator so the safety policy can be unit-tested without a device. */
    fun evaluate(totalGb: Double, availableGb: Double, modelBytes: Long, mode: String, nCtx: Int = 256, rank: Int = 8): Budget {
        val modelGb = modelBytes.coerceAtLeast(0L) / 1e9
        val safeTotal = totalGb.coerceAtLeast(0.5)
        val reserve = max(0.45, safeTotal * 0.14)
        val workload = when (mode) {
            "quantize" -> modelGb * 1.35 + 0.30
            // The frozen LoRA base is mmap-backed; estimate only a bounded hot-page window.
            // Adapter tensors, activations, optimizer state and KV/context buffers remain resident.
            "lora" -> min(modelGb * 0.10, 0.25) + (nCtx.coerceAtLeast(128) / 512.0) * 0.12 + (rank.coerceAtLeast(4) / 8.0) * 0.035 + 0.35
            else -> modelGb * 4.0 + (nCtx.coerceAtLeast(128) / 512.0) * 0.20 + 0.55
        }
        val required = workload + reserve
        val fullSupported = mode != "full" || safeTotal >= 8.0
        val ok = fullSupported && availableGb >= required
        val message = when {
            mode == "full" && !fullSupported -> "Fine-tuning completo exige pelo menos 8 GB de RAM total neste motor. Use LoRA para treinar com segurança no aparelho."
            ok -> "Orçamento seguro: ${"%.2f".format(availableGb)} GB livres · estimativa ${"%.2f".format(required)} GB incluindo reserva do Android."
            else -> "Orçamento de trabalho insuficiente: ${"%.2f".format(availableGb)} GB livres; buffers/adaptadores estimados em ${"%.2f".format(required)} GB incluindo reserva do Android. O peso base usa mmap e não é contabilizado como heap residente integral."
        }
        return Budget(ok, availableGb, required, message, safeTotal, reserve)
    }

    fun check(context: Context, modelBytes: Long, mode: String, nCtx: Int = 256, rank: Int = 8): Budget =
        evaluate(totalGb(context), availableGb(context), modelBytes, mode, nCtx, rank)

    /** A22/4-GB profile reduces activation and adapter memory before entering JNI. */
    fun loraPlan(context: Context, modelBytes: Long, requestedCtx: Int, requestedRank: Int, requestedThreads: Int, requestedTargets: String): LoraPlan {
        val total = totalGb(context)
        val smallDevice = total < 6.0
        val safeCtx = requestedCtx.coerceIn(128, if (smallDevice) 256 else 512)
        val safeRank = requestedRank.coerceIn(4, if (smallDevice) 8 else 16)
        val safeThreads = requestedThreads.coerceIn(1, if (smallDevice) 2 else 4)
        val safeTargets = if (smallDevice) "attn" else requestedTargets
        val budget = check(context, modelBytes, "lora", safeCtx, safeRank)
        val adjusted = safeCtx != requestedCtx || safeRank != requestedRank || safeThreads != requestedThreads || safeTargets != requestedTargets
        return LoraPlan(budget, safeCtx, safeRank, safeThreads, safeTargets, adjusted)
    }

    /** Best-effort release before a heavy native allocation. */
    fun trim() { runCatching { Runtime.getRuntime().gc() } }

    fun fitsContext(params: Long, ctx: Int, availableGb: Double): Int {
        val gbNeeded = params / 1e9 * 0.6 + ctx / 512.0 * 0.15
        if (gbNeeded <= availableGb * 0.65) return ctx
        val scale = max(0.25, min(1.0, (availableGb * 0.65) / max(0.1, gbNeeded)))
        return (ctx * scale).toInt().coerceAtLeast(128)
    }
}