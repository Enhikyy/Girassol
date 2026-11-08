package dev.labforge.app.core.train

import dev.labforge.app.core.Perf
import kotlin.math.roundToInt

/** Hyperparameters recommended from the researched corpus and the device budget. */
data class TrainingPreset(
    val rank: Int,
    val epochs: Int,
    val lr: Float,
    val ctx: Int,
    val targets: String,
    val notes: String,
    val estimatedTokens: Long,
    val estimatedMinutes: Int,
)

object AutoTuner {

    fun recommend(
        tokens: Long,
        docs: Int,
        ramGb: Double,
        cores: Int,
        params: Long = 1_000_000_000L,
        technical: Boolean = true,
    ): TrainingPreset {
        // A 4-GB Android phone learns through small repeated LoRA cycles.
        // Do not prescribe a desktop-sized context/rank and hope the OS survives.
        val lowMemory = ramGb < 6.0
        val rank = if (lowMemory) 4 else when {
            tokens < 15_000 -> 8
            tokens < 60_000 -> 16
            tokens < 200_000 -> 24
            else -> 32
        }
        val epochs = if (lowMemory) {
            if (tokens < 5_000) 2 else 1
        } else when {
            tokens < 5_000 -> 5
            tokens < 40_000 -> 4
            tokens < 150_000 -> 3
            else -> 2
        }
        val lr = when {
            rank <= 8 -> 2e-4f
            rank <= 16 -> 1.5e-4f
            else -> 1e-4f
        }
        val ctx = when {
            lowMemory -> 256
            tokens > 80_000 -> 1024
            technical -> 1024
            else -> 512
        }
        val targets = if (!lowMemory && tokens > 40_000 && ramGb >= 8) "attn,ffn" else "attn"
        val perEpoch = tokens.toDouble() / (Perf.decodeTps(params, 4.85, cores) / 3.2).coerceAtLeast(0.5)
        val minutes = ((perEpoch * epochs) / 60.0).roundToInt().coerceAtLeast(1)

        val notes = buildString {
            append("$docs fontes · ~${tokens} tokens. ")
            append(
                when {
                    tokens < 5_000 -> "Corpus curto: rank baixo e mais epochs para não decorar."
                    tokens < 60_000 -> "Corpus médio: equilíbrio entre rank e epochs."
                    else -> "Corpus grande: rank maior e menos epochs."
                },
            )
            if (lowMemory) append(" Perfil móvel de baixa RAM: LoRA rank 4, contexto 256, atenção e ciclos curtos repetíveis.")
        }

        return TrainingPreset(rank, epochs, lr, ctx, targets, notes, tokens, minutes)
    }
}
