package com.enhikyy.girassol.core.cognition

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

data class PerceptualSignal(
    val sourceModule: String,
    val summary: String,
    val entityId: String?,
    val salience: Double // [0.0, 1.0]
)

class GlobalWorkspaceStage(private val capacity: Int = 3) {
    fun broadcastWinningSignals(signals: List<PerceptualSignal>): List<PerceptualSignal> =
        signals.filter { it.salience > 0.15 }
            .sortedByDescending { it.salience }
            .take(capacity)
}

data class HomeostaticVariable(
    val name: String,
    val value: Double,
    val setpoint: Double,
    val weightDeficit: Double,
    val weightExcess: Double
) {
    fun driveCost(): Double {
        val def = max(0.0, setpoint - value)
        val exc = max(0.0, value - setpoint)
        return weightDeficit * def * def + weightExcess * exc * exc
    }
}

object ActiveInferencePolicyEvaluator {
    fun computeExpectedFreeEnergy(
        predictedPragmaticRisk: Double,
        priorEntropy: Double,
        posteriorExpectedEntropy: Double,
        epistemicWeight: Double = 0.65
    ): Double {
        val epistemicGain = (priorEntropy - posteriorExpectedEntropy).coerceAtLeast(0.0)
        return predictedPragmaticRisk - epistemicWeight * epistemicGain
    }

    fun shannonEntropy(probabilities: DoubleArray): Double {
        var h = 0.0
        for (p in probabilities) {
            if (p > 1e-9) h -= p * ln(p)
        }
        return h
    }

    fun applyHysteresisAndSoftmax(
        policyKeys: List<String>,
        expectedFreeEnergies: List<Double>,
        currentIntentionKey: String?,
        switchCost: Double,
        precisionGamma: Double,
        uniformSample: Double
    ): Int {
        if (policyKeys.isEmpty()) return -1
        if (policyKeys.size == 1) return 0

        val utilities = policyKeys.indices.map { i ->
            val penalty = if (currentIntentionKey != null && policyKeys[i] != currentIntentionKey) switchCost else 0.0
            -expectedFreeEnergies[i] - penalty
        }
        val maxU = utilities.max()
        val weights = utilities.map { exp((precisionGamma * (it - maxU)).coerceIn(-40.0, 0.0)) }
        val total = weights.sum()
        var acc = 0.0
        val target = uniformSample * total
        for (i in weights.indices) {
            acc += weights[i]
            if (target <= acc) return i
        }
        return weights.lastIndex
    }
}