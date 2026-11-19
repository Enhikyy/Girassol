package com.enhikyy.girassol.core.memory

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

enum class MemoryLayer { EPISODIC, SEMANTIC, PROCEDURAL }

data class AssociativeMemoryNode(
    val id: Long,
    val ownerAgentId: String,
    val layer: MemoryLayer,
    val content: String,
    val tags: Set<String>,
    val entityIds: Set<String>,
    val linkedMemoryIds: MutableSet<Long> = mutableSetOf(),
    val salience: Double,
    val confidence: Double,
    val sourceEventId: Long?,
    val sourceAgentId: String?,
    val createdTick: Long,
    val recentAccessTicks: MutableList<Long> = mutableListOf(),
    var qUtility: Double = 0.0
) {
    fun baseLevelActivation(currentTick: Long, decay: Double = 0.5): Double {
        val samples = if (recentAccessTicks.isEmpty()) listOf(createdTick) else recentAccessTicks
        var sum = 0.0
        for (t in samples) {
            val dt = (currentTick - t).coerceAtLeast(1L).toDouble()
            sum += dt.pow(-decay)
        }
        return ln(sum.coerceAtLeast(1e-9))
    }

    fun updateMemRlUtility(observedReward: Double, alpha: Double = 0.2) {
        qUtility = (qUtility + alpha * (observedReward.coerceIn(-1.0, 1.0) - qUtility)).coerceIn(-1.0, 1.0)
    }
}

data class DomainBetaBelief(
    val subject: String,
    val predicate: String,
    val domain: String,
    var alpha: Double = 1.0,
    var beta: Double = 1.0,
    var lastVerifiedTick: Long = 0L
) {
    val probability: Double get() = alpha / (alpha + beta)
    val evidenceStrength: Double get() = alpha + beta

    fun updateFromDirectObservation(isTrue: Boolean, tick: Long, weight: Double = 1.0) {
        if (isTrue) alpha += weight else beta += weight
        lastVerifiedTick = tick
    }

    fun updateFromSocialClaim(isClaimedTrue: Boolean, speakerDomainTrust: Double, speakerConfidence: Double) {
        val effectiveWeight = 0.45 * speakerDomainTrust.coerceIn(0.0, 1.0) * speakerConfidence.coerceIn(0.0, 1.0)
        if (isClaimedTrue) alpha += effectiveWeight else beta += effectiveWeight
    }
}

class HybridMemoryRetriever {
    fun retrieve(
        nodes: List<AssociativeMemoryNode>,
        currentTick: Long,
        queryTags: Set<String>,
        queryEntities: Set<String>,
        activeWorkingMemoryIds: Set<Long>,
        topK: Int = 6
    ): List<AssociativeMemoryNode> {
        return nodes.map { node ->
            val rawAct = node.baseLevelActivation(currentTick)
            val normAct = 1.0 / (1.0 + exp(-rawAct))
            val associativeBoost = if (node.linkedMemoryIds.any { it in activeWorkingMemoryIds }) 0.25 else 0.0
            val tagScore = if (queryTags.isEmpty()) 0.0 else {
                node.tags.intersect(queryTags).size.toDouble() / queryTags.size.toDouble()
            }
            val entityScore = if (queryEntities.isEmpty()) 0.0 else {
                node.entityIds.intersect(queryEntities).size.toDouble() / queryEntities.size.toDouble()
            }
            val contextRel = (0.55 * tagScore + 0.45 * entityScore).coerceIn(0.0, 1.0)
            val memRlTerm = (node.qUtility + 1.0) * 0.5

            val totalScore = 0.30 * normAct +
                0.25 * (node.salience * node.confidence) +
                0.25 * contextRel +
                0.20 * memRlTerm +
                associativeBoost

            node to totalScore
        }
            .sortedByDescending { it.second }
            .take(topK)
            .map { (node, _) ->
                if (node.recentAccessTicks.size >= 16) node.recentAccessTicks.removeAt(0)
                node.recentAccessTicks.add(currentTick)
                node
            }
    }
}