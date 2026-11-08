package dev.labforge.app.core.memory

import dev.labforge.app.core.agent.LessonStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Deterministic replay corpus: only recorded lessons, never invented corrections. */
object MemoryConsolidator {
    data class Summary(val lessons: Int, val failures: Int, val replay: File?, val questions: List<String>)

    fun consolidate(lessons: List<LessonStore.Lesson>, directory: File): Summary {
        directory.mkdirs()
        val failures = lessons.filter { "failure" in it.tags }
        val replay = lessons.filter { "failure" !in it.tags && it.body.isNotBlank() }
            .distinctBy { it.title.trim().lowercase() to it.body.trim() }
            .sortedWith(compareByDescending<LessonStore.Lesson> { it.uses }.thenBy { it.id })
        val corpus = replay.joinToString("\n") {
            JSONObject().put("messages", JSONArray()
                .put(JSONObject().put("role", "user").put("content", it.title))
                .put(JSONObject().put("role", "assistant").put("content", it.body)))
                .put("source", "girassol-recorded-lesson").put("lesson_id", it.id).toString()
        }
        val destination = if (replay.isEmpty()) null else {
            val hash = MessageDigest.getInstance("SHA-256").digest(corpus.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16)
            File(directory, "memory-replay-$hash.jsonl").apply {
                if (!exists()) {
                    val temporary = File.createTempFile("replay-", ".part", directory)
                    try { temporary.writeText(corpus + "\n"); check(temporary.renameTo(this)) }
                    finally { temporary.delete() }
                }
            }
        }
        val questions = failures.map { "Como diagnosticar ${it.title.removePrefix("Falha em ")}?" }.distinct().take(8)
        return Summary(replay.size, failures.size, destination, questions)
    }
}
