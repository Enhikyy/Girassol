package com.enhikyy.girassol.core.world

import java.nio.ByteBuffer
import java.security.MessageDigest

object StreamSeedDeriver {
    fun derive(worldSeed: Long, entityId: String, tick: Long, streamName: String): Long {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(ByteBuffer.allocate(8).putLong(worldSeed).array())
        md.update(entityId.toByteArray(Charsets.UTF_8))
        md.update(ByteBuffer.allocate(8).putLong(tick).array())
        md.update(streamName.toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(md.digest(), 0, 8).long
    }
}

class DeterministicRng(private var state: Long) {
    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextDouble(): Double = (nextLong() ushr 11) * 1.1102230246251565E-16

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound deve ser positivo" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun snapshotState(): Long = state
}

enum class ProposalOrigin { UTILITY_AI, LOCAL_LLM, USER_INTERVENTION, INSTITUTION_ENFORCEMENT }

sealed interface AgentAction {
    val actorId: String
    val origin: ProposalOrigin
    val tick: Long
    val targetId: String?

    data class MoveTo(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String?,
        val targetX: Float,
        val targetZ: Float
    ) : AgentAction

    data class GatherResource(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String,
        val resourceType: String,
        val requestedAmount: Int
    ) : AgentAction

    data class TransferItem(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String,
        val itemType: String,
        val quantity: Int
    ) : AgentAction

    data class SpeakTo(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String,
        val domain: String,
        val propositionKey: String?,
        val claimedValue: Double?,
        val utteranceSummary: String
    ) : AgentAction

    data class StudyOrTeach(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String, // skillId
        val mentorId: String?
    ) : AgentAction

    data class RepairStructure(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String,
        val resourceCost: Int
    ) : AgentAction

    data class Rest(
        override val actorId: String,
        override val origin: ProposalOrigin,
        override val tick: Long,
        override val targetId: String? = null
    ) : AgentAction
}

data class CanonicalEvent(
    val schemaVersion: Int,
    val eventId: Long,
    val tick: Long,
    val actorId: String,
    val actionType: String,
    val targetId: String?,
    val origin: ProposalOrigin,
    val payloadJson: String,
    val prevHash: String,
    val eventHash: String
) {
    companion object {
        fun computeHash(
            schemaVersion: Int,
            eventId: Long,
            tick: Long,
            actorId: String,
            actionType: String,
            targetId: String?,
            origin: ProposalOrigin,
            payloadJson: String,
            prevHash: String
        ): String {
            val raw = "v=$schemaVersion|id=$eventId|t=$tick|actor=$actorId|type=$actionType|" +
                "target=${targetId ?: "NONE"}|origin=${origin.name}|payload=$payloadJson|prev=$prevHash"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}