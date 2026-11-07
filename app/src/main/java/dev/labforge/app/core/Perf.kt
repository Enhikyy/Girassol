package dev.labforge.app.core

import kotlin.math.max
import kotlin.math.pow

/** Throughput heuristics used for time estimates in the UI. */
object Perf {

    fun decodeTps(params: Long, bits: Double, cores: Int): Double {
        if (params <= 0) return 0.0
        val billions = params / 1e9
        val base = (cores.coerceAtLeast(1) * 3.5) / max(0.25, billions.pow(0.85))
        val quantFactor = (4.5 / bits.coerceAtLeast(1.0)).pow(0.55)
        return (base * quantFactor).coerceAtLeast(0.4)
    }

    fun prefillTps(decodeTps: Double) = (decodeTps * 6.5).coerceAtLeast(2.0)

    fun responseSeconds(promptTokens: Int, outputTokens: Int, params: Long, bits: Double, cores: Int): Double {
        val tps = decodeTps(params, bits, cores)
        if (tps <= 0) return 0.0
        return promptTokens / prefillTps(tps) + outputTokens / tps
    }

    fun trainingSeconds(tokens: Long, epochs: Int, params: Long, bits: Double, cores: Int, lora: Boolean): Double {
        val tps = decodeTps(params, bits, cores) / if (lora) 3.2 else 4.6
        if (tps <= 0) return 0.0
        return tokens.toDouble() * epochs / tps + epochs * 4.0
    }

    fun fmtDuration(seconds: Double): String {
        if (seconds <= 0 || !seconds.isFinite()) return "—"
        val s = seconds.toLong()
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}min ${s % 60}s"
            s < 86400 -> "${s / 3600}h ${(s % 3600) / 60}min"
            else -> "${s / 86400}d ${(s % 86400) / 3600}h"
        }
    }

    fun fmtEta(startedAt: Long, progress: Float): String {
        if (startedAt <= 0 || progress <= 0.02f) return "calculando…"
        val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
        val total = elapsed / progress
        return fmtDuration((total - elapsed).coerceAtLeast(0.0))
    }
}
