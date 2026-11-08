package dev.labforge.app.core.learning

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Local-first skill tree, spaced repetition and offline experience consolidation. */
data class SkillNode(
    val id: String, val title: String, val theme: String, val level: Int,
    val mastery: Float = 0f, val repetitions: Int = 0, val intervalDays: Int = 0,
    val dueAt: Long = 0L, val lastReviewedAt: Long = 0L,
)
data class LearningExperience(
    val id: String, val at: Long, val kind: String, val topic: String,
    val detail: String, val importance: Float,
)
data class DreamTile(
    val id: String, val at: Long, val kind: String, val title: String,
    val insight: String, val confidence: Float, val evidenceCount: Int,
)
data class LearningSnapshot(
    val skills: List<SkillNode> = emptyList(),
    val experiences: List<LearningExperience> = emptyList(),
    val dreams: List<DreamTile> = emptyList(),
)

/**
 * Inspired by cognitive-science skill trees and the offline dream-cycle pattern.
 * This is an original Kotlin implementation; no third-party runtime is executed.
 */
class LearningContinuity(private val file: File) {
    private var state: LearningSnapshot = load()

    @Synchronized fun snapshot(): LearningSnapshot = state

    @Synchronized fun syncCurriculum(theme: String, topics: List<String>): LearningSnapshot {
        val existing = state.skills.associateBy { it.id }
        val next = topics.mapIndexed { index, title ->
            val id = stableId(theme + "|" + title)
            existing[id] ?: SkillNode(id, title.trim(), theme, index + 1)
        }
        state = state.copy(skills = (state.skills.filter { it.theme != theme } + next).takeLast(240))
        persist()
        return state
    }

    /** SM-2-inspired review schedule; low recall resets the interval. */
    @Synchronized fun review(skillId: String, quality: Int, now: Long = System.currentTimeMillis()): LearningSnapshot {
        val q = quality.coerceIn(0, 5)
        state = state.copy(skills = state.skills.map { skill ->
            if (skill.id != skillId) skill else {
                val reps = if (q < 3) 0 else skill.repetitions + 1
                val interval = when {
                    q < 3 -> 1
                    reps == 1 -> 1
                    reps == 2 -> 3
                    else -> (max(1, skill.intervalDays) * 2).coerceAtMost(180)
                }
                val mastery = (skill.mastery + if (q >= 3) 0.12f else -0.08f).coerceIn(0f, 1f)
                skill.copy(mastery = mastery, repetitions = reps, intervalDays = interval,
                    dueAt = now + interval * DAY_MS, lastReviewedAt = now)
            }
        })
        persist()
        return state
    }

    @Synchronized fun record(kind: String, topic: String, detail: String, importance: Float = 0.5f): LearningSnapshot {
        val now = System.currentTimeMillis()
        val entry = LearningExperience(
            stableId(kind + "|" + topic + "|" + detail + "|" + now), now,
            kind.take(24), topic.take(120), detail.take(1400), importance.coerceIn(0f, 1f),
        )
        state = state.copy(experiences = (state.experiences + entry).takeLast(240))
        persist()
        return state
    }

    /** Consolidate repeated topics and preserve failure evidence for later cycles. */
    @Synchronized fun dream(now: Long = System.currentTimeMillis()): LearningSnapshot {
        val recent = state.experiences.takeLast(80)
        if (recent.isEmpty()) return state
        val tiles = recent.groupBy { it.topic.lowercase().trim() }.mapNotNull { (topic, items) ->
            val failures = items.filter { it.kind == "failure" }
            val evidence = (failures + items.filter { it.kind == "success" || it.kind == "cycle" }).takeLast(5)
            if (items.size < 2 && failures.isEmpty()) return@mapNotNull null
            val kind = if (failures.isNotEmpty()) "failure-review" else "consolidation"
            val insight = if (failures.isNotEmpty()) {
                "Revisar " + failures.size + " falha(s): " + failures.take(3).joinToString(" | ") { it.detail.take(180) }
            } else {
                "Padrão recorrente em " + items.size + " experiências: " +
                    items.takeLast(3).joinToString(" | ") { it.detail.take(160) }
            }
            val confidence = min(0.95f, 0.35f + evidence.size * 0.12f +
                (if (evidence.isEmpty()) 0f else evidence.map { it.importance }.average().toFloat()) * 0.15f)
            DreamTile(stableId(topic + "|" + now + "|" + kind), now, kind,
                topic.ifBlank { "Aprendizado consolidado" }.take(90), insight.take(1200),
                confidence, evidence.size)
        }
        state = state.copy(dreams = (state.dreams + tiles).distinctBy { it.id }.takeLast(80))
        persist()
        return state
    }

    @Synchronized fun due(now: Long = System.currentTimeMillis()): List<SkillNode> =
        state.skills.filter { it.dueAt == 0L || it.dueAt <= now }
            .sortedWith(compareBy<SkillNode> { it.dueAt }.thenBy { it.mastery })

    private fun load(): LearningSnapshot = runCatching {
        if (!file.isFile) return LearningSnapshot()
        val root = JSONObject(file.readText())
        val skills = root.optJSONArray("skills").objects().map { o ->
            SkillNode(o.optString("id"), o.optString("title"), o.optString("theme"), o.optInt("level"),
                o.optDouble("mastery").toFloat(), o.optInt("repetitions"), o.optInt("intervalDays"),
                o.optLong("dueAt"), o.optLong("lastReviewedAt"))
        }
        val experiences = root.optJSONArray("experiences").objects().map { o ->
            LearningExperience(o.optString("id"), o.optLong("at"), o.optString("kind"),
                o.optString("topic"), o.optString("detail"), o.optDouble("importance").toFloat())
        }
        val dreams = root.optJSONArray("dreams").objects().map { o ->
            DreamTile(o.optString("id"), o.optLong("at"), o.optString("kind"), o.optString("title"),
                o.optString("insight"), o.optDouble("confidence").toFloat(), o.optInt("evidenceCount"))
        }
        LearningSnapshot(skills, experiences, dreams)
    }.getOrDefault(LearningSnapshot())

    private fun persist() {
        file.parentFile?.mkdirs()
        val root = JSONObject().apply {
            put("version", 1)
            put("skills", JSONArray().apply { state.skills.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("title", s.title); put("theme", s.theme); put("level", s.level)
                    put("mastery", s.mastery.toDouble()); put("repetitions", s.repetitions)
                    put("intervalDays", s.intervalDays); put("dueAt", s.dueAt); put("lastReviewedAt", s.lastReviewedAt)
                })
            } })
            put("experiences", JSONArray().apply { state.experiences.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("at", e.at); put("kind", e.kind); put("topic", e.topic)
                    put("detail", e.detail); put("importance", e.importance.toDouble())
                })
            } })
            put("dreams", JSONArray().apply { state.dreams.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("at", d.at); put("kind", d.kind); put("title", d.title)
                    put("insight", d.insight); put("confidence", d.confidence.toDouble())
                    put("evidenceCount", d.evidenceCount)
                })
            } })
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(root.toString(2))
        if (!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
    }

    private fun stableId(value: String): String = value.hashCode().toUInt().toString(16)
    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    companion object { private const val DAY_MS = 86_400_000L }
}
