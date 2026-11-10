package app.girassol.core.research

import app.girassol.core.activity.ActivityHub
import app.girassol.core.activity.ActivityKind
import app.girassol.core.data.SafeFiles
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class ResearchNote(
    val id: String,
    val title: String,
    val source: String,
    val url: String,
    val content: String,
    val retrievedAt: Long,
)

data class DatasetResult(val path: String, val examples: Int, val bytes: Long)

class ResearchEngine(private val directory: File, private val activities: ActivityHub) {
    private val notesFile = File(directory, "library.json")
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).build()
    private val mutableNotes = MutableStateFlow(loadNotes())
    val notes: StateFlow<List<ResearchNote>> = mutableNotes.asStateFlow()

    suspend fun research(topic: String): List<ResearchNote> = withContext(Dispatchers.IO) {
        val cleanTopic = topic.trim()
        require(cleanTopic.length in 3..160) { "Descreva um tema entre 3 e 160 caracteres" }
        val operation = activities.begin(ActivityKind.RESEARCH, "Autoestudo: $cleanTopic", "Consultando fontes públicas")
        try {
            activities.progress(operation, 0.12f, "Abrindo a Biblioteca")
            val found = coroutineScope {
                val wiki = async { runCatching { wikipedia(cleanTopic) }.getOrElse { emptyList() } }
                val crossref = async { runCatching { crossref(cleanTopic) }.getOrElse { emptyList() } }
                activities.progress(operation, 0.52f, "Lendo e extraindo conteúdo")
                wiki.await() + crossref.await()
            }
            check(found.isNotEmpty()) { "Nenhuma fonte respondeu com conteúdo útil. Verifique a conexão ou refine o tema." }
            val merged = (mutableNotes.value + found).associateBy { it.id }.values.sortedByDescending { it.retrievedAt }.take(200)
            mutableNotes.value = merged
            persist(merged)
            activities.progress(operation, 0.9f, "Organizando ${found.size} novas notas")
            activities.complete(operation, "${found.size} fontes adicionadas à Biblioteca")
            found
        } catch (failure: Throwable) {
            activities.fail(operation, failure.message ?: "Falha no autoestudo")
            throw failure
        }
    }

    fun removeNote(id: String) {
        mutableNotes.value = mutableNotes.value.filterNot { it.id == id }
        persist(mutableNotes.value)
    }

    fun createDataset(topic: String): DatasetResult {
        val clean = topic.trim().ifBlank { "Conhecimento geral" }
        val selected = mutableNotes.value.filter { note ->
            clean.equals("Conhecimento geral", true) || note.title.contains(clean, true) || note.content.contains(clean, true)
        }.ifEmpty { mutableNotes.value }
        require(selected.isNotEmpty()) { "Pesquise e salve fontes antes de criar um dataset" }
        val operation = activities.begin(ActivityKind.TRAINING, "Preparando especialização", "Transformando fontes em exemplos")
        try {
            val output = File(directory, "datasets/${safeName(clean)}.jsonl")
            val builder = StringBuilder()
            var examples = 0
            selected.forEach { note ->
                note.content.split(Regex("(?<=[.!?])\\s+")).chunked(4).map { it.joinToString(" ") }
                    .filter { it.length in 160..5000 }.take(8).forEach { passage ->
                        val item = JSONObject().apply {
                            put("instruction", "Explique com precisão o tema ${note.title} usando somente o contexto fornecido.")
                            put("input", passage)
                            put("output", passage)
                            put("source", note.url)
                        }
                        builder.append(item.toString()).append('\n'); examples++
                    }
                activities.progress(operation, examples.toFloat() / (selected.size * 8).coerceAtLeast(1), "Exemplos: $examples")
            }
            check(examples > 0) { "As notas não possuem texto suficiente para gerar exemplos" }
            SafeFiles.writeAtomic(output, builder.toString())
            activities.complete(operation, "Dataset com $examples exemplos salvo")
            return DatasetResult(output.absolutePath, examples, output.length())
        } catch (failure: Throwable) {
            activities.fail(operation, failure.message ?: "Falha ao criar dataset")
            throw failure
        }
    }

    private fun wikipedia(topic: String): List<ResearchNote> {
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("pt.wikipedia.org").addPathSegments("w/api.php")
            .addQueryParameter("action", "query").addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", topic).addQueryParameter("gsrlimit", "4")
            .addQueryParameter("prop", "extracts|info").addQueryParameter("explaintext", "1")
            .addQueryParameter("exlimit", "4").addQueryParameter("inprop", "url").addQueryParameter("format", "json").build()
        val root = getJson(url.toString())
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        return pages.keys().asSequence().mapNotNull { key ->
            val page = pages.optJSONObject(key) ?: return@mapNotNull null
            val text = page.optString("extract").trim().take(18_000)
            if (text.length < 200) return@mapNotNull null
            val title = page.optString("title")
            note(title, "Wikipédia", page.optString("fullurl").ifBlank { "https://pt.wikipedia.org/wiki/${title.replace(' ', '_')}" }, text)
        }.toList()
    }

    private fun crossref(topic: String): List<ResearchNote> {
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("api.crossref.org").addPathSegment("works")
            .addQueryParameter("query", topic).addQueryParameter("rows", "3")
            .addQueryParameter("select", "title,DOI,abstract,URL,published").build()
        val items = getJson(url.toString()).getJSONObject("message").optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val title = item.optJSONArray("title")?.optString(0).orEmpty()
                val abstract = item.optString("abstract").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                if (title.isBlank() || abstract.length < 160) continue
                add(note(title, "Crossref", item.optString("URL").ifBlank { "https://doi.org/${item.getString("DOI")}" }, abstract.take(12_000)))
            }
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "Girassol/2.0 (local research; Android)").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Fonte respondeu HTTP ${response.code}" }
            val body = response.body ?: error("Resposta vazia")
            val bytes = java.io.ByteArrayOutputStream()
            body.byteStream().use { input -> SafeFiles.copyBounded(input, bytes, 4L * 1024 * 1024) }
            return JSONObject(bytes.toString(Charsets.UTF_8.name()))
        }
    }

    private fun note(title: String, source: String, url: String, content: String) = ResearchNote(
        stableId(url), title.take(240), source, url, content, System.currentTimeMillis(),
    )

    private fun loadNotes(): List<ResearchNote> = runCatching {
        if (!notesFile.isFile) return emptyList()
        val array = JSONArray(notesFile.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(ResearchNote(item.getString("id"), item.getString("title"), item.getString("source"),
                    item.getString("url"), item.getString("content"), item.getLong("retrievedAt")))
            }
        }
    }.getOrDefault(emptyList())

    private fun persist(notes: List<ResearchNote>) {
        val array = JSONArray()
        notes.forEach { note -> array.put(JSONObject().apply {
            put("id", note.id); put("title", note.title); put("source", note.source); put("url", note.url)
            put("content", note.content); put("retrievedAt", note.retrievedAt)
        }) }
        SafeFiles.writeAtomic(notesFile, array.toString())
    }

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    private fun safeName(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifBlank { "dataset" }
}
