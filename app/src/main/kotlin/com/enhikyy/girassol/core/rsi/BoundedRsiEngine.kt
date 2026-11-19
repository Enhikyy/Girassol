package com.enhikyy.girassol.core.rsi

import java.security.MessageDigest
import kotlin.math.abs

/**
 * [ADAPTAÇÃO / HIPÓTESE H1]
 * Genoma cognitivo e arquitetural sujeito a autoaperfeiçoamento recursivo (RSI) delimitado.
 * Nenhuma mutação altera código arbitrário; apenas parâmetros tipados e auditáveis
 * validados por shadow-replay determinístico antes da promoção.
 */
data class CognitivePolicyGenome(
    val version: Int,
    val epistemicExplorationWeight: Double,     // Peso do ganho epistêmico em G(pi) [0.10, 1.50]
    val cognitiveGateTieThreshold: Double,      // Limiar |U1 - U2| para acionar LLM [0.01, 0.20]
    val cognitiveGateMinUrgency: Double,        // Urgência mínima para deliberar [0.40, 0.90]
    val intentionSwitchHysteresis: Double,      // Custo de troca c_switch contra thrashing [0.02, 0.35]
    val memRlLearningRate: Double,              // Taxa alpha de atualização Q(m) [0.05, 0.50]
    val actRDecayExponent: Double,              // Expoente d de decaimento ACT-R [0.20, 0.85]
    val thermalThrottleSensitivity: Double,     // Multiplicador de custo metabólico térmico [0.50, 3.00]
    val activeVpoRules: List<String>            // Regras verbais compactas injetadas no prompt
) {
    fun clamped(): CognitivePolicyGenome = copy(
        epistemicExplorationWeight = epistemicExplorationWeight.coerceIn(0.10, 1.50),
        cognitiveGateTieThreshold = cognitiveGateTieThreshold.coerceIn(0.01, 0.20),
        cognitiveGateMinUrgency = cognitiveGateMinUrgency.coerceIn(0.40, 0.90),
        intentionSwitchHysteresis = intentionSwitchHysteresis.coerceIn(0.02, 0.35),
        memRlLearningRate = memRlLearningRate.coerceIn(0.05, 0.50),
        actRDecayExponent = actRDecayExponent.coerceIn(0.20, 0.85),
        thermalThrottleSensitivity = thermalThrottleSensitivity.coerceIn(0.50, 3.00),
        activeVpoRules = activeVpoRules.take(8)
    )

    fun sha256Signature(): String {
        val payload = listOf(
            version, epistemicExplorationWeight, cognitiveGateTieThreshold,
            cognitiveGateMinUrgency, intentionSwitchHysteresis, memRlLearningRate,
            actRDecayExponent, thermalThrottleSensitivity, activeVpoRules.joinToString("|")
        ).joinToString(":")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Proposta estruturada emitida pela sessão SESSION_CRITIC_REFLECTOR via gramática GBNF.
 */
data class RsiMutationProposal(
    val proposalId: String,
    val proposerAgentId: String,
    val tick: Long,
    val rationale: String,
    val deltaEpistemicWeight: Double = 0.0,
    val deltaGateTieThreshold: Double = 0.0,
    val deltaSwitchHysteresis: Double = 0.0,
    val deltaMemRlAlpha: Double = 0.0,
    val deltaThermalSensitivity: Double = 0.0,
    val newVpoRule: String? = null,
    val declaredConfidence: Double
) {
    fun applyTo(base: CognitivePolicyGenome): CognitivePolicyGenome {
        val updatedRules = if (!newVpoRule.isNullOrBlank()) {
            val sanitized = newVpoRule.trim().take(140)
            (base.activeVpoRules + sanitized).distinct().takeLast(8)
        } else {
            base.activeVpoRules
        }
        return base.copy(
            version = base.version + 1,
            epistemicExplorationWeight = base.epistemicExplorationWeight + deltaEpistemicWeight,
            cognitiveGateTieThreshold = base.cognitiveGateTieThreshold + deltaGateTieThreshold,
            intentionSwitchHysteresis = base.intentionSwitchHysteresis + deltaSwitchHysteresis,
            memRlLearningRate = base.memRlLearningRate + deltaMemRlAlpha,
            thermalThrottleSensitivity = base.thermalThrottleSensitivity + deltaThermalSensitivity,
            activeVpoRules = updatedRules
        ).clamped()
    }
}

data class ShadowReplayMetrics(
    val meanFreeEnergy: Double,
    val survivalEfficiency: Double,
    val brierScore: Double,
    val behavioralDiversityScore: Double,
    val llmCallsPer100Ticks: Double,
    val invariantsViolated: Int
)

data class RsiPromotionDecision(
    val promoted: Boolean,
    val reason: String,
    val baselineHash: String,
    val candidateHash: String,
    val netFitnessGain: Double
)

/**
 * Avaliador de promoção com portão estrito de invariantes e calibração Brier.
 */
class BoundedRsiPromotionGate(
    private val minNetFitnessGain: Double = 0.025,
    private val maxBrierDegradation: Double = 0.010,
    private val minDiversityFloor: Double = 0.35
) {
    fun evaluatePromotion(
        baseline: CognitivePolicyGenome,
        candidate: CognitivePolicyGenome,
        baseMetrics: ShadowReplayMetrics,
        candMetrics: ShadowReplayMetrics
    ): RsiPromotionDecision {
        val baseHash = baseline.sha256Signature()
        val candHash = candidate.sha256Signature()

        if (candMetrics.invariantsViolated > 0) {
            return RsiPromotionDecision(
                promoted = false,
                reason = "REJEITADO: Violação de ${candMetrics.invariantsViolated} invariante(s) no shadow-replay.",
                baselineHash = baseHash,
                candidateHash = candHash,
                netFitnessGain = -1.0
            )
        }

        if (candMetrics.behavioralDiversityScore < minDiversityFloor) {
            return RsiPromotionDecision(
                promoted = false,
                reason = "REJEITADO: Risco de colapso semântico/comportamental (diversidade=${candMetrics.behavioralDiversityScore}).",
                baselineHash = baseHash,
                candidateHash = candHash,
                netFitnessGain = 0.0
            )
        }

        if (candMetrics.brierScore > baseMetrics.brierScore + maxBrierDegradation) {
            return RsiPromotionDecision(
                promoted = false,
                reason = "REJEITADO: Degradação de calibração epistêmica (Brier ${baseMetrics.brierScore} -> ${candMetrics.brierScore}).",
                baselineHash = baseHash,
                candidateHash = candHash,
                netFitnessGain = 0.0
            )
        }

        // Função objetivo multi-critério: eficiência homeostática - energia livre - custo neural
        val baseScore = baseMetrics.survivalEfficiency - 0.35 * baseMetrics.meanFreeEnergy - 0.004 * baseMetrics.llmCallsPer100Ticks
        val candScore = candMetrics.survivalEfficiency - 0.35 * candMetrics.meanFreeEnergy - 0.004 * candMetrics.llmCallsPer100Ticks
        val gain = candScore - baseScore

        return if (gain >= minNetFitnessGain) {
            RsiPromotionDecision(
                promoted = true,
                reason = "PROMOVIDO: Ganho líquido comprovado (+${"%.4f".format(gain)}) com 0 violações de invariantes.",
                baselineHash = baseHash,
                candidateHash = candHash,
                netFitnessGain = gain
            )
        } else {
            RsiPromotionDecision(
                promoted = false,
                reason = "REJEITADO: Ganho líquido insuficiente (${"%.4f".format(gain)} < $minNetFitnessGain).",
                baselineHash = baseHash,
                candidateHash = candHash,
                netFitnessGain = gain
            )
        }
    }
}