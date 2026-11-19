package com.enhikyy.girassol.core.world

import java.security.MessageDigest

/**
 * Snapshot verificável do WorldCore.
 *
 * O hash é separado do hash chain dos eventos: protege também o JSON do estado
 * e o estado dos streams aleatórios guardados no checkpoint.
 */
data class WorldCheckpoint(
    val tick: Long,
    val lastEventId: Long,
    val stateJson: String,
    val rngStateJson: String,
    val lastEventHash: String,
    val createdUtc: Long,
    val checkpointHash: String,
) {
    fun verify(): Boolean = checkpointHash == computeHash(
        tick = tick,
        lastEventId = lastEventId,
        stateJson = stateJson,
        rngStateJson = rngStateJson,
        lastEventHash = lastEventHash,
        createdUtc = createdUtc,
    )

    companion object {
        fun create(
            tick: Long,
            lastEventId: Long,
            stateJson: String,
            rngStateJson: String,
            lastEventHash: String,
            createdUtc: Long,
        ): WorldCheckpoint = WorldCheckpoint(
            tick = tick,
            lastEventId = lastEventId,
            stateJson = stateJson,
            rngStateJson = rngStateJson,
            lastEventHash = lastEventHash,
            createdUtc = createdUtc,
            checkpointHash = computeHash(
                tick, lastEventId, stateJson, rngStateJson, lastEventHash, createdUtc
            ),
        )

        private fun computeHash(
            tick: Long,
            lastEventId: Long,
            stateJson: String,
            rngStateJson: String,
            lastEventHash: String,
            createdUtc: Long,
        ): String {
            val fields = listOf(
                "GIRASSOL_WORLD_CHECKPOINT_V1",
                tick.toString(),
                lastEventId.toString(),
                stateJson,
                rngStateJson,
                lastEventHash,
                createdUtc.toString(),
            )
            val canonical = buildString {
                fields.forEach { field ->
                    append(field.toByteArray(Charsets.UTF_8).size)
                    append(':')
                    append(field)
                }
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
