package com.enhikyy.girassol.core.evolution

import kotlin.math.ln

/**
 * [BASE / HIPÓTESE H4 & H5]
 * 1. Mede a diversidade comportamental e lexical da população (D_within e D_peers)
 *    para prevenir o Colapso Semântico Coletivo (Kong et al., 2026).
 * 2. Gerencia estruturas de Construção de Nicho na grade espacial (Herança Ecológica).
 */
data class EcologicalNicheStructure(
    val structureId: String,
    val structureType: String,   // ex: "PAVED_TRAIL", "GRANARY", "WATCHTOWER", "SCHOOL_HEARTH"
    val gridX: Int,
    val gridZ: Int,
    val level: Int,
    val structuralIntegrity: Double, // Omega(t) in [0.0, 1.0]
    val builderAgentId: String,
    val builtAtTick: Long
) {
    /** Bônus de redução de custo energético / aumento de eficiência para gerações futuras */
    fun ecologicalEfficiencyBonus(): Double =
        (0.08 * level * structuralIntegrity).coerceIn(0.0, 0.45)
}

object SemanticCollapseGuard {

    /**
     * Calcula a entropia de Shannon normalizada H_sem in [0, 1] sobre a distribuição
     * de ações/conceitos emitidos pela população.
     */
    fun normalizedPopulationEntropy(actionCounts: Map<String, Int>): Double {
        val total = actionCounts.values.sum().toDouble()
        if (total <= 0.0 || actionCounts.size <= 1) return 0.0

        var entropy = 0.0
        for (count in actionCounts.values) {
            if (count > 0) {
                val p = count / total
                entropy -= p * ln(p)
            }
        }
        val maxEntropy = ln(actionCounts.size.toDouble())
        return (entropy / maxEntropy).coerceIn(0.0, 1.0)
    }

    /**
     * Retorna um bônus epistêmico dependente de frequência negativa para ações raras
     * quando a entropia coletiva cai abaixo do limiar de segurança (anti-homogeneização).
     */
    fun noveltyEpistemicBonus(
        actionType: String,
        actionCounts: Map<String, Int>,
        collapseThreshold: Double = 0.55
    ): Double {
        val hSem = normalizedPopulationEntropy(actionCounts)
        if (hSem >= collapseThreshold) return 0.0

        val total = actionCounts.values.sum().coerceAtLeast(1).toDouble()
        val freq = (actionCounts[actionType] ?: 0) / total
        val scarcity = (1.0 - freq).coerceIn(0.0, 1.0)
        val urgency = ((collapseThreshold - hSem) / collapseThreshold).coerceIn(0.0, 1.0)
        return 0.30 * urgency * scarcity
    }
}