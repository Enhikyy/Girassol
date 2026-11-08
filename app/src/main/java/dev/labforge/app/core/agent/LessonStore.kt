package dev.labforge.app.core.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Persistent "caderno de estudos" — lessons the agent wrote after solving something. */
class LessonStore(val directory: File) {

    private val file = File(directory, "lessons.json").apply { parentFile?.mkdirs() }
    private val lock = locks.getOrPut(file.canonicalPath) { Any() }
    companion object {
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    }

    data class Lesson(
        val id: String,
        val title: String,
        val body: String,
        val tags: List<String>,
        val createdAt: Long,
        var uses: Int = 0,
    )

    fun all(): List<Lesson> = synchronized(lock) { runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Lesson(
                id = o.optString("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                createdAt = o.optLong("createdAt"),
                uses = o.optInt("uses"),
            )
        }
    }.getOrDefault(emptyList()) }

    fun save(title: String, body: String, tags: List<String> = emptyList()): Lesson {
        synchronized(lock) {
            val list = all().toMutableList()
            val existing = list.indexOfFirst { it.title.equals(title, true) }
            val lesson = if (existing >= 0) {
                list[existing].copy(body = body, tags = (list[existing].tags + tags).distinct())
            } else {
                Lesson(UUID.randomUUID().toString(), title, body, tags.distinct(), System.currentTimeMillis())
            }
            if (existing >= 0) list[existing] = lesson else list.add(0, lesson)
            persist(list)
            return lesson
        }
    }

    /** Lightweight lexical retrieval: tag hit > title hit > body hit, recency as tiebreaker. */
    fun search(query: String, limit: Int = 3): List<Lesson> {
        synchronized(lock) {
            val terms = query.lowercase().split(Regex("[^a-z0-9_]+")).filter { it.length > 2 }
            if (terms.isEmpty()) return all().take(limit)
            val scored = all().map { l ->
                var score = 0.0
                val title = l.title.lowercase()
                val body = l.body.lowercase()
                for (t in terms) {
                    if (l.tags.any { it.lowercase().contains(t) }) score += 3.0
                    if (title.contains(t)) score += 2.0
                    if (body.contains(t)) score += 1.0
                }
                score += (l.uses * 0.1)
                l to score
            }.filter { it.second > 0.0 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }

            // Reinforce what actually gets reused, so the notebook ranks by usefulness.
            if (scored.isNotEmpty()) {
                val hits = scored.map { it.id }.toSet()
                persist(all().map { if (it.id in hits) it.copy(uses = it.uses + 1) else it })
            }
            return scored
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            persist(all().filterNot { it.id == id })
        }
    }

    private fun persist(list: List<Lesson>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("title", l.title)
                put("body", l.body)
                put("tags", JSONArray(l.tags))
                put("createdAt", l.createdAt)
                put("uses", l.uses)
            })
        }
        val tmp = File.createTempFile("lessons-", ".part", file.parentFile)
        try { tmp.writeText(arr.toString(2)); check(tmp.renameTo(file)) { "falha ao persistir lições" } }
        finally { tmp.delete() }
    }
}
