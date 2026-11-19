package com.enhikyy.girassol.core.social

import kotlin.math.abs
import kotlin.math.exp

enum class DunbarRing(val maxCapacity: Int) {
    INTIMATE_CLIQUE(5),
    SYMPATHY_GROUP(15),
    FUNCTIONAL_BAND(50),
    CLAN_COMMUNITY(150)
}

data class DirectedSocialTie(
    val fromAgentId: String,
    val toAgentId: String,
    var affinity: Double = 0.0,
    var familiarity: Double = 0.05,
    var respect: Double = 0.0,
    val domainAlpha: MutableMap<String, Double> = mutableMapOf(),
    val domainBeta: MutableMap<String, Double> = mutableMapOf(),
    var opinionValue: Double = 0.5
) {
    fun domainTrust(domain: String): Double {
        val a = domainAlpha[domain] ?: 1.5
        val b = domainBeta[domain] ?: 1.5
        return a / (a + b)
    }

    fun recordTrustOutcome(domain: String, success: Boolean) {
        val a = domainAlpha[domain] ?: 1.5
        val b = domainBeta[domain] ?: 1.5
        if (success) {
            domainAlpha[domain] = a + 1.0
        } else {
            domainBeta[domain] = b + 2.2
        }
    }

    fun tieStrength(): Double =
        (0.40 * abs(affinity) + 0.35 * familiarity + 0.25 * domainTrust("general")).coerceIn(0.0, 1.0)

    fun decayFamiliarity(dtTicks: Long, lambda: Double = 0.0005) {
        familiarity *= exp(-lambda * dtTicks.coerceAtLeast(0L))
    }
}

data class CommonsInstitution(
    val institutionId: String,
    val protectedResourceNodeId: String,
    val memberIds: MutableSet<String>,
    val maxHarvestPerWindow: Int,
    val graduatedFineSchedule: List<Int> = listOf(2, 5, 12)
) {
    private val harvestCounter = mutableMapOf<String, Int>()
    private val offenseCounter = mutableMapOf<String, Int>()

    fun evaluateHarvestCompliance(agentId: String, amount: Int): Int {
        if (agentId !in memberIds) {
            val offenses = (offenseCounter[agentId] ?: 0) + 1
            offenseCounter[agentId] = offenses
            return graduatedFineSchedule[(offenses - 1).coerceIn(0, graduatedFineSchedule.lastIndex)]
        }
        val total = (harvestCounter[agentId] ?: 0) + amount
        harvestCounter[agentId] = total
        if (total > maxHarvestPerWindow) {
            val offenses = (offenseCounter[agentId] ?: 0) + 1
            offenseCounter[agentId] = offenses
            return graduatedFineSchedule[(offenses - 1).coerceIn(0, graduatedFineSchedule.lastIndex)]
        }
        return 0
    }

    fun resetWindow() {
        harvestCounter.clear()
    }
}