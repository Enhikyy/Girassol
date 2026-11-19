package com.enhikyy.girassol.core.memory

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * [BASE / HIPÓTESE H2]
 * Opinião Subjetiva de Audun Jøsang: w = (b, d, u, a) com b + d + u = 1.
 * Permite propagar boatos e crenças sociais sem confundir falta de evidência (u alto)
 * com falsidade comprovada (d alto), bloqueando memory poisoning multi-salto.
 */
data class SubjectiveOpinion(
    val belief: Double,
    val disbelief: Double,
    val uncertainty: Double,
    val baseRate: Double = 0.5
) {
    init {
        val sum = belief + disbelief + uncertainty
        require(abs(sum - 1.0) < 1e-4) { "Opinião subjetiva inválida: b+d+u=$sum != 1.0" }
    }

    /** Probabilidade projetada P(x) = b + a * u */
    fun projectedProbability(): Double = (belief + baseRate * uncertainty).coerceIn(0.0, 1.0)

    /**
     * Operador de Desconto de Confiança (Trust Discounting):
     * Se A confia em B com grau c in [0, 1], a opinião de B é descontada para incerteza.
     */
    fun discountByTrust(trustWeight: Double): SubjectiveOpinion {
        val c = trustWeight.coerceIn(0.0, 1.0)
        val bNew = c * belief
        val dNew = c * disbelief
        val uNew = (1.0 - bNew - dNew).coerceIn(0.0, 1.0)
        return SubjectiveOpinion(bNew, dNew, uNew, baseRate)
    }

    companion object {
        fun fromBeta(alpha: Double, beta: Double, baseRate: Double = 0.5, priorWeight: Double = 2.0): SubjectiveOpinion {
            val r = (alpha - 1.0).coerceAtLeast(0.0)
            val s = (beta - 1.0).coerceAtLeast(0.0)
            val denom = r + s + priorWeight
            return SubjectiveOpinion(
                belief = r / denom,
                disbelief = s / denom,
                uncertainty = priorWeight / denom,
                baseRate = baseRate
            )
        }

        /**
         * Operador de Fusão Cumulativa (Cumulative Belief Fusion) para fontes independentes.
         */
        fun cumulativeFuse(w1: SubjectiveOpinion, w2: SubjectiveOpinion): SubjectiveOpinion {
            val kappa = w1.uncertainty + w2.uncertainty - w1.uncertainty * w2.uncertainty
            if (kappa <= 1e-9) {
                return SubjectiveOpinion(
                    belief = 0.5 * (w1.belief + w2.belief),
                    disbelief = 0.5 * (w1.disbelief + w2.disbelief),
                    uncertainty = 0.0,
                    baseRate = 0.5 * (w1.baseRate + w2.baseRate)
                )
            }
            val b = (w1.belief * w2.uncertainty + w2.belief * w1.uncertainty) / kappa
            val d = (w1.disbelief * w2.uncertainty + w2.disbelief * w1.uncertainty) / kappa
            val u = (w1.uncertainty * w2.uncertainty) / kappa
            val norm = b + d + u
            return SubjectiveOpinion(b / norm, d / norm, u / norm, w1.baseRate)
        }
    }
}

/**
 * Representa um nó de memória leve para consolidação noturna/repouso (Sharp-Wave Ripple Replay).
 */
data class ConsolidatableMemory(
    val id: Long,
    val content: String,
    val tags: Set<String>,
    val isSemantic: Boolean,
    val qUtility: Double,
    val tdPredictionError: Double,
    val accessTicks: List<Long>,
    val createdTick: Long,
    val verifiedByWorldCore: Boolean
) {
    fun activation(currentTick: Long, decay: Double = 0.5): Double {
        val ticks = if (accessTicks.isEmpty()) listOf(createdTick) else accessTicks
        var sum = 0.0
        for (t in ticks) {
            val dt = (currentTick - t).coerceAtLeast(1L).toDouble()
            sum += dt.pow(-decay)
        }
        return ln(sum.coerceAtLeast(1e-9))
    }
}

data class ConsolidationReport(
    val replayedNodeIds: List<Long>,
    val promotedSemanticSummaries: List<String>,
    val prunedNodeIds: List<Long>
)

/**
 * Motor de consolidação hipocampal para execução durante ciclos de sono/baixa carga térmica,
 * mantendo memória e SQLite limitados em maratonas 24/7.
 */
class HippocampalSleepConsolidator(
    private val maxNodesPerAgent: Int = 512,
    private val replayBatchSize: Int = 12
) {
    fun consolidate(
        memories: List<ConsolidatableMemory>,
        currentTick: Long,
        actRDecay: Double = 0.5
    ): ConsolidationReport {
        if (memories.isEmpty()) return ConsolidationReport(emptyList(), emptyList(), emptyList())

        // 1. Sharp-Wave Ripple Replay: prioriza nós com maior surpresa (|TD-error|) e utilidade |Q_m|
        val replayCandidates = memories
            .sortedByDescending { abs(it.tdPredictionError) * 0.65 + abs(it.qUtility) * 0.35 }
            .take(replayBatchSize)

        // 2. Promoção semântica: agrupa episódios verificados recorrentes por tag dominante
        val promoted = memories
            .filter { !it.isSemantic && it.verifiedByWorldCore && it.qUtility > 0.35 }
            .flatMap { mem -> mem.tags.map { tag -> tag to mem } }
            .groupBy({ it.first }, { it.second })
            .filter { (_, group) -> group.size >= 3 }
            .map { (tag, group) ->
                val avgQ = group.map { it.qUtility }.average()
                "SCHEMA[$tag]: ${group.size} episódios verificados consolidados (Q_m=${"%.2f".format(avgQ)})."
            }

        // 3. Poda homeostática: remove nós não-semânticos antigos com baixa ativação ACT-R e baixo |Q_m|
        val prunedIds = if (memories.size > maxNodesPerAgent) {
            val excess = memories.size - maxNodesPerAgent
            memories
                .filter { !it.isSemantic && !it.verifiedByWorldCore }
                .sortedBy { it.activation(currentTick, actRDecay) + 1.5 * abs(it.qUtility) }
                .take(excess)
                .map { it.id }
        } else {
            emptyList()
        }

        return ConsolidationReport(
            replayedNodeIds = replayCandidates.map { it.id },
            promotedSemanticSummaries = promoted,
            prunedNodeIds = prunedIds
        )
    }
}