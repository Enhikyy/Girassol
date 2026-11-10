package app.girassol.core.learning

import app.girassol.core.activity.ActivityHub
import app.girassol.core.activity.ActivityKind
import app.girassol.core.activity.WorldEvent
import app.girassol.core.data.SafeFiles
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SkillProgress(val id: String, val title: String, val mastery: Float, val experiences: Int, val failures: Int)
data class MemoryInsight(val id: String, val title: String, val detail: String, val createdAt: Long, val needsReview: Boolean)
data class MemorySnapshot(val skills: List<SkillProgress> = emptyList(), val insights: List<MemoryInsight> = emptyList(), val lastSleepAt: Long = 0)

/** Deterministic, inspectable consolidation; no claim that model weights changed. */
class MemoryEngine(private val file: File, private val activities: ActivityHub) {
    private val mutableState = MutableStateFlow(load())
    val state: StateFlow<MemorySnapshot> = mutableState.asStateFlow()

    fun consolidate(): MemorySnapshot {
        val operation = activities.begin(ActivityKind.SLEEP, "Sono profundo", "Consolidando experiências e falhas")
        try {
            val events = activities.state.value.recentEvents
            activities.progress(operation, 0.25f, "Agrupando experiências")
            val skills = listOf(
                ActivityKind.RESEARCH to "Pesquisa e síntese",
                ActivityKind.TRAINING to "Especialização",
                ActivityKind.BUILD to "Engenharia Android",
                ActivityKind.DIAGNOSIS to "Diagnóstico",
            ).map { (kind, title) -> skillFor(kind, title, events) }
            val grouped = events.groupBy { it.kind }
            val insights = grouped.mapNotNull { (kind, items) -> insightFor(kind, items) }
                .sortedByDescending { it.createdAt }.take(40)
            val snapshot = MemorySnapshot(skills, insights, System.currentTimeMillis())
            mutableState.value = snapshot
            persist(snapshot)
            activities.complete(operation, "${events.size} experiências processadas; ${insights.count { it.needsReview }} pontos exigem revisão")
            return snapshot
        } catch (failure: Throwable) {
            activities.fail(operation, failure.message ?: "Falha na consolidação")
            throw failure
        }
    }

    private fun skillFor(kind: ActivityKind, title: String, events: List<WorldEvent>): SkillProgress {
        val relevant = events.filter { it.kind == kind }
        val success = relevant.count { it.successful }
        val failures = relevant.count { !it.successful }
        val mastery = if (relevant.isEmpty()) 0f else (0.15f + success * 0.12f - failures * 0.05f).coerceIn(0f, 0.95f)
        return SkillProgress(kind.name, title, mastery, relevant.size, failures)
    }

    private fun insightFor(kind: ActivityKind, items: List<WorldEvent>): MemoryInsight? {
        if (items.isEmpty()) return null
        val failures = items.filter { !it.successful }
        val latest = items.maxBy { it.at }
        val title = if (failures.isEmpty()) "Padrão de ${kind.defaultTitle}" else "Revisar falhas de ${kind.defaultTitle}"
        val detail = if (failures.isEmpty()) {
            "${items.count { it.successful }} conclusões registradas. Última atividade: ${latest.detail}"
        } else {
            failures.takeLast(3).joinToString("\n") { "• ${it.title}: ${it.detail}" }
        }
        return MemoryInsight("${kind.name}-${latest.at}", title, detail, latest.at, failures.isNotEmpty())
    }

    private fun load(): MemorySnapshot = runCatching {
        if (!file.isFile) return MemorySnapshot()
        val root = JSONObject(file.readText())
        val skills = root.optJSONArray("skills")?.let { array ->
            buildList { for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(SkillProgress(item.getString("id"), item.getString("title"), item.getDouble("mastery").toFloat(), item.getInt("experiences"), item.getInt("failures")))
            } }
        }.orEmpty()
        val insights = root.optJSONArray("insights")?.let { array ->
            buildList { for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(MemoryInsight(item.getString("id"), item.getString("title"), item.getString("detail"), item.getLong("createdAt"), item.getBoolean("needsReview")))
            } }
        }.orEmpty()
        MemorySnapshot(skills, insights, root.optLong("lastSleepAt"))
    }.getOrDefault(MemorySnapshot())

    private fun persist(snapshot: MemorySnapshot) {
        val root = JSONObject()
        root.put("lastSleepAt", snapshot.lastSleepAt)
        root.put("skills", JSONArray().apply { snapshot.skills.forEach { skill -> put(JSONObject().apply {
            put("id", skill.id); put("title", skill.title); put("mastery", skill.mastery.toDouble())
            put("experiences", skill.experiences); put("failures", skill.failures)
        }) } })
        root.put("insights", JSONArray().apply { snapshot.insights.forEach { insight -> put(JSONObject().apply {
            put("id", insight.id); put("title", insight.title); put("detail", insight.detail)
            put("createdAt", insight.createdAt); put("needsReview", insight.needsReview)
        }) } })
        SafeFiles.writeAtomic(file, root.toString(2))
    }
}
