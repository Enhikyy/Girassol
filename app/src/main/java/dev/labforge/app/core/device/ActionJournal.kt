package dev.labforge.app.core.device

import org.json.JSONObject
import java.io.File

/**
 * Append-only journal of every device action. Two reasons:
 *  - the agent can review what it did and undo/avoid repeats;
 *  - the UI can show evidence without parsing logcat.
 *
 * Bounded to [maxLines] so a long autonomous run cannot fill the device.
 */
class ActionJournal(private val file: File, private val maxLines: Int = 500) {

    @Synchronized
    fun append(record: DeviceActionRecord) {
        runCatching {
            file.parentFile?.mkdirs()
            val line = JSONObject().apply {
                put("at", record.at)
                put("action", record.action)
                put("detail", record.detail.take(300))
                put("ok", record.ok)
                put("before", record.before)
                put("after", record.after)
                put("note", record.note.take(300))
            }.toString()
            file.appendText(line + "\n")
            trimIfNeeded()
        }
    }

    @Synchronized
    fun recent(limit: Int = 20): List<DeviceActionRecord> {
        if (!file.isFile) return emptyList()
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        return lines.asReversed().asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { parse(it) }
            .take(limit.coerceIn(1, maxLines))
            .toList()
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }

    private fun trimIfNeeded() {
        val lines = file.readLines()
        if (lines.size <= maxLines) return
        val keep = lines.takeLast(maxLines)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(keep.joinToString("\n", postfix = "\n"))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun parse(line: String): DeviceActionRecord? = runCatching {
        val o = JSONObject(line)
        DeviceActionRecord(
            at = o.optLong("at"),
            action = o.optString("action"),
            detail = o.optString("detail"),
            ok = o.optBoolean("ok"),
            before = o.optString("before"),
            after = o.optString("after"),
            note = o.optString("note"),
        )
    }.getOrNull()
}
