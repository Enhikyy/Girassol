package dev.labforge.app.core.train

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** One real, successfully saved native training output. No merge or divergence is inferred. */
data class TrainingCheckpoint(
    val kind: String,
    val outputName: String,
    val outputPath: String,
    val savedAt: Long,
    val finalLoss: Float?,
)

class TrainingCheckpointStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "training_checkpoints.json"))

    fun record(kind: String, output: File, savedAt: Long, finalLoss: Float?) {
        if (kind.isBlank() || savedAt <= 0L) return
        synchronized(lock) {
            val checkpoints = readUnlocked().toMutableList()
            if (checkpoints.any { it.outputPath == output.absolutePath && it.savedAt == savedAt }) return
            checkpoints += TrainingCheckpoint(
                kind = kind,
                outputName = output.name,
                outputPath = output.absolutePath,
                savedAt = savedAt,
                finalLoss = finalLoss?.takeIf { it.isFinite() && it >= 0f },
            )
            writeUnlocked(checkpoints.sortedBy { it.savedAt }.takeLast(100))
        }
    }

    fun list(): List<TrainingCheckpoint> = synchronized(lock) {
        readUnlocked().sortedByDescending { it.savedAt }
    }

    private fun readUnlocked(): List<TrainingCheckpoint> {
        if (!file.isFile) return emptyList()
        val array = runCatching { JSONArray(file.readText()) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val kind = item.optString("kind")
                val path = item.optString("outputPath")
                val savedAt = item.optLong("savedAt")
                if (kind.isBlank() || path.isBlank() || savedAt <= 0L) continue
                add(TrainingCheckpoint(
                    kind = kind,
                    outputName = item.optString("outputName", File(path).name),
                    outputPath = path,
                    savedAt = savedAt,
                    finalLoss = if (item.has("finalLoss") && !item.isNull("finalLoss")) {
                        item.optDouble("finalLoss").toFloat().takeIf { it.isFinite() && it >= 0f }
                    } else null,
                ))
            }
        }
    }

    private fun writeUnlocked(checkpoints: List<TrainingCheckpoint>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        checkpoints.forEach { checkpoint ->
            array.put(JSONObject().apply {
                put("kind", checkpoint.kind)
                put("outputName", checkpoint.outputName)
                put("outputPath", checkpoint.outputPath)
                put("savedAt", checkpoint.savedAt)
                put("finalLoss", checkpoint.finalLoss?.toDouble() ?: JSONObject.NULL)
            })
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private companion object {
        val lock = Any()
    }
}
