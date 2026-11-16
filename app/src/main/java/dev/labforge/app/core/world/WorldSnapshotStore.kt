package dev.labforge.app.core.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
private data class SnapshotEnvelope(
    val format: Int = WORLD_SCHEMA_VERSION,
    val checksum: String,
    val payload: String,
)

data class WorldLoadResult(
    val state: WorldState,
    val source: String,
    val recoveredFromBackup: Boolean = false,
    val warning: String? = null,
)

/** Atomic JSON snapshots with SHA-256 integrity check and one known-good backup. */
class WorldSnapshotStore(
    directory: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val primary = File(directory, "world-v1.json")
    private val backup = File(directory, "world-v1.backup.json")
    private val temporary = File(directory, "world-v1.tmp")

    @Synchronized
    fun load(seed: Long, nowWallClockMs: Long): WorldLoadResult {
        primary.parentFile?.mkdirs()
        val primaryResult = read(primary)
        if (primaryResult != null) return WorldLoadResult(primaryResult, primary.name)
        val primaryError = lastReadError
        val backupResult = read(backup)
        if (backupResult != null) {
            return WorldLoadResult(
                state = backupResult,
                source = backup.name,
                recoveredFromBackup = true,
                warning = "Snapshot principal inválido; backup íntegro restaurado.",
            )
        }
        val warning = if (primary.exists() || backup.exists()) {
            "Nenhum snapshot íntegro foi encontrado. Um novo mundo foi criado; os arquivos inválidos foram preservados. " +
                listOfNotNull(primaryError, lastReadError).joinToString("; ")
        } else null
        return WorldLoadResult(WorldFactory.create(seed = seed, nowWallClockMs = nowWallClockMs), "new", warning = warning)
    }

    @Synchronized
    fun save(state: WorldState) {
        WorldEngine.validate(state)
        primary.parentFile?.mkdirs()
        val hasValidPrimary = primary.isFile && read(primary) != null
        if (primary.exists() && !hasValidPrimary) preserveInvalid(primary)
        if (backup.exists() && read(backup) == null) preserveInvalid(backup)
        val payload = json.encodeToString(WorldState.serializer(), state)
        val envelope = SnapshotEnvelope(checksum = sha256(payload), payload = payload)
        val bytes = json.encodeToString(SnapshotEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
        writeSynced(temporary, bytes)
        if (hasValidPrimary) {
            val backupTemp = File(backup.parentFile, "${backup.name}.tmp")
            writeSynced(backupTemp, primary.readBytes())
            replace(backupTemp, backup)
        }
        replace(temporary, primary)
    }

    private var lastReadError: String? = null

    private fun read(file: File): WorldState? {
        lastReadError = null
        if (!file.isFile) return null
        if (file.length() > MAX_SNAPSHOT_BYTES) {
            lastReadError = "${file.name}: snapshot excede 64 MiB"
            return null
        }
        return runCatching {
            val envelope = json.decodeFromString(SnapshotEnvelope.serializer(), file.readText())
            require(envelope.format == WORLD_SCHEMA_VERSION) { "formato ${envelope.format} incompatível" }
            val digest = sha256(envelope.payload)
            require(digest == envelope.checksum) { "checksum divergente" }
            val world = json.decodeFromString(WorldState.serializer(), envelope.payload)
            WorldEngine.validate(world)
            world
        }.onFailure { lastReadError = "${file.name}: ${it.message}" }.getOrNull()
    }

    private fun preserveInvalid(file: File) {
        var target = File(file.parentFile, "${file.name}.corrupt")
        var suffix = 1
        while (target.exists()) target = File(file.parentFile, "${file.name}.corrupt-${suffix++}")
        if (!file.renameTo(target)) {
            writeSynced(target, file.readBytes())
            if (!file.delete()) error("Não foi possível preservar ${file.name} antes de substituí-lo.")
        }
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file, false).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    private fun replace(source: File, target: File) {
        if (!source.renameTo(target)) {
            // Some Android filesystems do not replace an existing destination via renameTo.
            val fallback = File(target.parentFile, "${target.name}.new")
            writeSynced(fallback, source.readBytes())
            if (!fallback.renameTo(target)) {
                // Last resort is still checksummed, so an interrupted write is detected on load.
                writeSynced(target, fallback.readBytes())
                fallback.delete()
            }
            source.delete()
        }
    }

    companion object {
        private const val MAX_SNAPSHOT_BYTES = 64L * 1024L * 1024L

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
