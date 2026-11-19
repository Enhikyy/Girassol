package com.enhikyy.girassol.core.cognition

/**
 * [ADAPTAÇÃO / HIPÓTESE H3]
 * Acopla a telemetria térmica/RAM do dispositivo Android dedicado ao custo metabólico
 * interno de acionar o LLM local (Llamatik 2.0), induzindo migração natural para reflexos
 * Tsetlin e estigmergia sem quebrar o determinismo do WorldCore.
 */
object AllostaticThermalCoupling {

    enum class ThermalTier(val baseMetabolicMultiplier: Double) {
        NONE_OR_LIGHT(1.0),
        MODERATE(1.85),
        SEVERE_OR_CRITICAL(4.50)
    }

    /**
     * Calcula o custo metabólico efetivo de uma chamada neural no funcional G(pi).
     */
    fun computeNeuralDeliberationCost(
        tier: ThermalTier,
        thermalSensitivity: Double,
        agentEnergyRatio: Double
    ): Double {
        val scarcityFactor = if (agentEnergyRatio < 0.30) 2.0 else 1.0
        return 0.12 * tier.baseMetabolicMultiplier * thermalSensitivity * scarcityFactor
    }

    /**
     * Decide de forma determinística se o CognitiveGate deve acionar o LLM ou delegar
     * ao motor proposicional Tsetlin + Estigmergia O(1).
     */
    fun shouldInvokeLocalLlm(
        topActionUtility: Double,
        secondActionUtility: Double,
        tieThreshold: Double,
        minUrgency: Double,
        consecutiveHtnFailures: Int,
        socialNegotiationActive: Boolean,
        tier: ThermalTier,
        tsetlinReflexConfidence: Double
    ): Boolean {
        // Se um reflexo booleano de Tsetlin já domina este padrão com alta confiança e não há crise térmica:
        if (tsetlinReflexConfidence >= 0.88 && consecutiveHtnFailures == 0 && !socialNegotiationActive) {
            return false
        }

        // Sob estresse térmico severo, bloqueia deliberações não-críticas
        if (tier == ThermalTier.SEVERE_OR_CRITICAL && consecutiveHtnFailures < 3) {
            return false
        }

        val closeTie = (topActionUtility >= minUrgency) &&
            ((topActionUtility - secondActionUtility) < tieThreshold)

        return closeTie || consecutiveHtnFailures >= 2 || socialNegotiationActive
    }
}