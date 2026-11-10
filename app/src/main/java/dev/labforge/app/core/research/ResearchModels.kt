package dev.labforge.app.core.research

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class ResearchDoc(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String,
    val year: Int? = null,
    val score: Double = 0.0,
) {
    val domain: String
        get() = runCatching { java.net.URI(url).host?.removePrefix("www.") ?: source }.getOrDefault(source)

    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("url", url)
        put("snippet", snippet)
        put("source", source)
        put("year", year ?: 0)
        put("score", score)
    }

    companion object {
        fun fromJson(o: JSONObject) = ResearchDoc(
            title = o.optString("title"),
            url = o.optString("url"),
            snippet = o.optString("snippet"),
            source = o.optString("source"),
            year = o.optInt("year").takeIf { it > 0 },
            score = o.optDouble("score", 0.0),
        )
    }
}

data class ResearchBundle(
    val id: String = UUID.randomUUID().toString(),
    val theme: String,
    val queries: List<String>,
    val docs: List<ResearchDoc>,
    val createdAt: Long = System.currentTimeMillis(),
    val autoSummary: String = "",
) {
    val tokensEstimate: Long
        get() = (docs.sumOf { it.snippet.length + it.title.length } / 3.6).toLong()

    val sourceCounts: Map<String, Int>
        get() = docs.groupingBy { it.source }.eachCount()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("theme", theme)
        put("createdAt", createdAt)
        put("autoSummary", autoSummary)
        put("queries", JSONArray(queries))
        put("docs", JSONArray().apply { docs.forEach { put(it.toJson()) } })
    }

    fun save(file: File) {
        val parent = file.absoluteFile.parentFile
            ?: throw IllegalArgumentException("arquivo de pesquisa sem diretório pai")
        check(parent.isDirectory || parent.mkdirs()) { "não foi possível criar ${parent.path}" }
        val serialized = toJson().toString(2)
        val temporary = File.createTempFile("${file.name}.", ".part", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(serialized.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    companion object {
        fun fromJson(o: JSONObject): ResearchBundle {
            val docs = o.optJSONArray("docs") ?: JSONArray()
            val queries = o.optJSONArray("queries") ?: JSONArray()
            return ResearchBundle(
                id = o.optString("id", UUID.randomUUID().toString()),
                theme = o.optString("theme"),
                queries = (0 until queries.length()).map { queries.optString(it) },
                docs = (0 until docs.length()).mapNotNull { docs.optJSONObject(it)?.let(ResearchDoc::fromJson) },
                createdAt = o.optLong("createdAt"),
                autoSummary = o.optString("autoSummary"),
            )
        }

        fun load(file: File): ResearchBundle? = runCatching {
            fromJson(JSONObject(file.readText()))
        }.getOrNull()
    }
}
