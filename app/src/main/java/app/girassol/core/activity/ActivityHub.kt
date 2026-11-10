package app.girassol.core.activity

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ActivityKind(val zoneId: String, val defaultTitle: String) {
    IDLE("atrium", "Em repouso"),
    CHAT("atrium", "Conversando"),
    RESEARCH("library", "Pesquisando na Biblioteca"),
    TRAINING("university", "Especializando-se na Faculdade"),
    SLEEP("home", "Consolidando memória na Casa"),
    DIAGNOSIS("clinic", "Revisando erros no Consultório"),
    BUILD("studio", "Construindo no Studio"),
}

data class WorldOperation(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val detail: String,
    val progress: Float,
    val startedAt: Long,
)

data class WorldEvent(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val detail: String,
    val at: Long,
    val successful: Boolean,
)

data class ActivitySnapshot(
    val operations: List<WorldOperation> = emptyList(),
    val recentEvents: List<WorldEvent> = emptyList(),
) {
    val primary: WorldOperation? get() = operations.maxByOrNull { it.startedAt }
}

/**
 * Shared source of truth for the World. Producers report real operation state;
 * the renderer never invents training, builds or research progress.
 */
class ActivityHub(private val historyFile: File) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(loadHistory())
    val state: StateFlow<ActivitySnapshot> = mutableState.asStateFlow()

    fun begin(kind: ActivityKind, title: String = kind.defaultTitle, detail: String = ""): String {
        val id = UUID.randomUUID().toString()
        synchronized(lock) {
            val operation = WorldOperation(id, kind, title, detail, 0f, System.currentTimeMillis())
            updateLocked(mutableState.value.copy(operations = mutableState.value.operations + operation))
        }
        return id
    }

    fun progress(id: String, progress: Float, detail: String? = null) {
        synchronized(lock) {
            val operations = mutableState.value.operations.map { operation ->
                if (operation.id == id) operation.copy(
                    progress = progress.coerceIn(0f, 1f),
                    detail = detail ?: operation.detail,
                ) else operation
            }
            updateLocked(mutableState.value.copy(operations = operations))
        }
    }

    fun complete(id: String, detail: String = "Concluído") = finish(id, detail, true)
    fun fail(id: String, detail: String) = finish(id, detail, false)

    private fun finish(id: String, detail: String, successful: Boolean) {
        synchronized(lock) {
            val operation = mutableState.value.operations.firstOrNull { it.id == id } ?: return
            val event = WorldEvent(operation.id, operation.kind, operation.title, detail, System.currentTimeMillis(), successful)
            updateLocked(mutableState.value.copy(
                operations = mutableState.value.operations.filterNot { it.id == id },
                recentEvents = (mutableState.value.recentEvents + event).takeLast(80),
            ))
        }
    }

    private fun updateLocked(value: ActivitySnapshot) {
        mutableState.value = value
        persistHistory(value.recentEvents)
    }

    private fun loadHistory(): ActivitySnapshot = runCatching {
        if (!historyFile.isFile) return ActivitySnapshot()
        val items = JSONArray(historyFile.readText())
        val events = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(WorldEvent(
                    id = item.getString("id"),
                    kind = ActivityKind.valueOf(item.getString("kind")),
                    title = item.getString("title"),
                    detail = item.getString("detail"),
                    at = item.getLong("at"),
                    successful = item.getBoolean("successful"),
                ))
            }
        }
        ActivitySnapshot(recentEvents = events.takeLast(80))
    }.getOrDefault(ActivitySnapshot())

    private fun persistHistory(events: List<WorldEvent>) {
        runCatching {
            historyFile.parentFile?.mkdirs()
            val array = JSONArray()
            events.forEach { event ->
                array.put(JSONObject().apply {
                    put("id", event.id); put("kind", event.kind.name); put("title", event.title)
                    put("detail", event.detail); put("at", event.at); put("successful", event.successful)
                })
            }
            val temporary = File(historyFile.parentFile, historyFile.name + ".tmp")
            temporary.writeText(array.toString())
            if (!temporary.renameTo(historyFile)) {
                historyFile.writeText(temporary.readText()); temporary.delete()
            }
        }
    }
}
