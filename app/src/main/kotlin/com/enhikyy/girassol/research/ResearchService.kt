package com.enhikyy.girassol.research

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResearchHit(
    val source: String,
    val title: String,
    val url: String,
    val snippet: String,
    val score: Float = 0f,
)

/** Configuração opcional da Brave Search; as outras fontes não exigem chave. */
object BraveConfig {
    @Volatile var apiKey: String? = null
        private set

    fun init(context: Context) {
        apiKey = context.getSharedPreferences("research", Context.MODE_PRIVATE)
            .getString("brave_api_key", null)
    }

    fun setApiKey(context: Context, key: String?) {
        context.getSharedPreferences("research", Context.MODE_PRIVATE).edit()
            .putString("brave_api_key", key?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
        apiKey = key?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Agregador de pesquisa: DuckDuckGo, Wikipedia, Hacker News, arXiv,
 * Stack Overflow, GitHub, Crossref e Brave opcional.
 *
 * A camada devolve fontes e snippets; não baixa datasets nem altera pesos.
 */
object ResearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, maxHits: Int = 8): List<ResearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim().take(300)
        if (q.length < 2) return@withContext emptyList()
        val jobs = listOf<suspend () -> List<ResearchHit>>(
            { duckDuckGo(q, maxHits) },
            { wikipedia(q, maxHits) },
            { hackerNews(q, maxHits) },
            { arxiv(q, maxHits) },
            { stackOverflow(q, maxHits) },
            { github(q, maxHits) },
            { crossref(q, maxHits) },
        )
        val hits = ArrayList<ResearchHit>()
        jobs.forEach { job ->
            runCatching { job() }.onSuccess { hits += it }
        }
        BraveConfig.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            runCatching { brave(q, key, maxHits) }.onSuccess { hits += it }
        }
        hits.distinctBy { it.url }.sortedByDescending { it.score }.take(maxHits.coerceIn(1, 30))
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private suspend fun duckDuckGo(q: String, limit: Int): List<ResearchHit> {
        val url = "https://api.duckduckgo.com/?q=${enc(q)}&format=json&no_html=1&skip_disambig=1"
        val json = get(url)?.let(::JSONObject) ?: return emptyList()
        val hits = ArrayList<ResearchHit>()
        json.optJSONArray("RelatedTopics")?.let { topics ->
            fun collect(array: JSONArray) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val nested = item.optJSONArray("Topics")
                    if (nested != null) {
                        collect(nested)
                    } else {
                        val text = item.optString("Text")
                        val firstUrl = item.optString("FirstURL")
                        if (text.isNotBlank() && firstUrl.startsWith("http")) {
                            hits += ResearchHit("DuckDuckGo", text.take(180), firstUrl, text, 0.5f)
                        }
                    }
                    if (hits.size >= limit) break
                }
            }
            collect(topics)
        }
        return hits.take(limit)
    }

    private suspend fun wikipedia(q: String, limit: Int): List<ResearchHit> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${enc(q)}&format=json&srlimit=$limit"
        val json = get(url, mapOf("User-Agent" to "GirassolAndroid/2.0"))?.let(::JSONObject) ?: return emptyList()
        val rows = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title")
            if (title.isBlank()) return@mapNotNull null
            val snippet = item.optString("snippet").replace(Regex("<[^>]+>"), "").take(300)
            ResearchHit("Wikipedia", title, "https://en.wikipedia.org/wiki/${title.replace(" ", "_")}", snippet, 0.45f)
        }
    }

    private suspend fun hackerNews(q: String, limit: Int): List<ResearchHit> {
        val url = "https://hn.algolia.com/api/v1/search?query=${enc(q)}&hitsPerPage=$limit"
        val json = get(url)?.let(::JSONObject) ?: return emptyList()
        val rows = json.optJSONArray("hits") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").ifBlank { item.optString("story_title") }
            val link = item.optString("url").ifBlank { item.optString("story_url") }
            if (title.isBlank() || !link.startsWith("http")) return@mapNotNull null
            ResearchHit("Hacker News", title, link, item.optString("comment_text").take(220), 0.55f)
        }
    }

    private suspend fun arxiv(q: String, limit: Int): List<ResearchHit> {
        val url = "https://export.arxiv.org/api/query?search_query=all:${enc(q)}&start=0&max_results=$limit"
        val xml = get(url) ?: return emptyList()
        val entries = Regex("<entry>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml)
        return entries.mapNotNull { match ->
            val body = match.groupValues[1]
            val title = xmlTag(body, "title")?.replace(Regex("\\s+"), " ") ?: return@mapNotNull null
            val summary = xmlTag(body, "summary")?.replace(Regex("\\s+"), " ")?.take(260).orEmpty()
            val link = Regex("<id>(.*?)</id>").find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (!link.startsWith("http")) return@mapNotNull null
            ResearchHit("arXiv", title, link, summary, 0.8f)
        }.take(limit).toList()
    }

    private suspend fun stackOverflow(q: String, limit: Int): List<ResearchHit> {
        val url = "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=${enc(q)}&site=stackoverflow&pagesize=$limit"
        val json = get(url)?.let(::JSONObject) ?: return emptyList()
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title")
            val link = item.optString("link")
            if (title.isBlank() || !link.startsWith("http")) return@mapNotNull null
            ResearchHit("Stack Overflow", title, link, "Pontuação ${item.optInt("score")}", 0.4f)
        }
    }

    private suspend fun github(q: String, limit: Int): List<ResearchHit> {
        val url = "https://api.github.com/search/repositories?q=${enc(q)}&sort=stars&order=desc&per_page=$limit"
        val json = get(url, mapOf("Accept" to "application/vnd.github+json"))?.let(::JSONObject) ?: return emptyList()
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("full_name")
            val link = item.optString("html_url")
            if (name.isBlank() || !link.startsWith("http")) return@mapNotNull null
            ResearchHit("GitHub", name, link, item.optString("description").take(240), 0.7f)
        }
    }

    private suspend fun crossref(q: String, limit: Int): List<ResearchHit> {
        val url = "https://api.crossref.org/works?query=${enc(q)}&rows=$limit"
        val json = get(url)?.let(::JSONObject) ?: return emptyList()
        val rows = json.optJSONObject("message")?.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optJSONArray("title")?.optString(0).orEmpty()
            val link = item.optString("URL")
            if (title.isBlank() || !link.startsWith("http")) return@mapNotNull null
            ResearchHit("Crossref", title, link, item.optString("publisher").take(180), 0.75f)
        }
    }

    private suspend fun brave(q: String, key: String, limit: Int): List<ResearchHit> {
        val url = "https://api.search.brave.com/res/v1/web/search?q=${enc(q)}&count=$limit"
        val json = get(url, mapOf("X-Subscription-Token" to key, "Accept" to "application/json"))
            ?.let(::JSONObject) ?: return emptyList()
        val rows = json.optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title")
            val link = item.optString("url")
            if (title.isBlank() || !link.startsWith("http")) return@mapNotNull null
            ResearchHit("Brave", title, link, item.optString("description").take(240), 0.85f)
        }
    }

    private fun xmlTag(xml: String, tag: String): String? =
        Regex("<$tag[^>]*>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(xml)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")?.replace("&lt;", "<")

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
