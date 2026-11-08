package dev.labforge.app.core.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free, key-less research APIs. Everything here is public and does not require
 * an account: Wikipedia, OpenAlex, arXiv, Stack Exchange, DuckDuckGo and
 * Semantic Scholar (best effort).
 */
object FreeResearchApis {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val UA = "Girassol/1.2 (Android research assistant; +https://github.com/ggml-org/llama.cpp)"

    /** Hard cap on a single response body: a hostile/broken endpoint must not OOM the app. */
    private const val MAX_BODY_BYTES = 4_000_000

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json, application/atom+xml, text/html;q=0.8, */*;q=0.5")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} em ${req.url.host}")
            val body = resp.body ?: error("resposta vazia")
            val declared = body.contentLength()
            if (declared > MAX_BODY_BYTES) error("resposta grande demais (${declared} bytes)")
            val text = body.string()
            if (text.length > MAX_BODY_BYTES) error("resposta grande demais")
            text
        }
    }

    // ------------------------------------------------------------------ Wikipedia

    suspend fun wikipedia(theme: String, lang: String = "pt"): List<ResearchDoc> {
        val q = enc(theme)
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q" +
            "&format=json&utf8=1&srlimit=6&srprop=snippet"
        val root = JSONObject(get(url))
        val arr = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        val docs = ArrayList<ResearchDoc>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            val pageId = o.optLong("pageid")
            var snippet = stripHtml(o.optString("snippet"))
            // enrich the first two with the REST summary
            if (i < 2) {
                runCatching {
                    val s = JSONObject(get("https://$lang.wikipedia.org/api/rest_v1/page/summary/${enc(title)}"))
                    val extract = s.optString("extract")
                    if (extract.isNotBlank()) snippet = extract
                }
            }
            docs += ResearchDoc(
                title = title,
                url = "https://$lang.wikipedia.org/?curid=$pageId",
                snippet = snippet.take(1200),
                source = "Wikipedia ${lang.uppercase()}",
                score = 1.0 + (6 - i) * 0.15,
            )
        }
        return docs
    }

    // ------------------------------------------------------------------- OpenAlex

    suspend fun openAlex(theme: String): List<ResearchDoc> {
        val url = "https://api.openalex.org/works?search=${enc(theme)}&per-page=6" +
            "&select=id,title,publication_year,doi,abstract_inverted_index,primary_location"
        val root = JSONObject(get(url))
        val arr = root.optJSONArray("results") ?: return emptyList()
        val docs = ArrayList<ResearchDoc>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val abstract = reconstructAbstract(o.optJSONObject("abstract_inverted_index"))
            if (abstract.isBlank()) continue
            val doi = o.optString("doi")
            docs += ResearchDoc(
                title = o.optString("title").ifBlank { "OpenAlex work" },
                url = doi.ifBlank { o.optString("id") },
                snippet = abstract.take(1200),
                source = "OpenAlex",
                year = o.optInt("publication_year").takeIf { it > 1900 },
                score = 1.1 + (6 - i) * 0.1,
            )
        }
        return docs
    }

    private fun reconstructAbstract(inv: JSONObject?): String {
        if (inv == null) return ""
        var max = 0
        val keys = inv.keys().asSequence().toList()
        val positions = HashMap<String, IntArray>()
        for (k in keys) {
            val a = inv.optJSONArray(k) ?: continue
            val arr = IntArray(a.length()) { a.optInt(it) }
            positions[k] = arr
            arr.forEach { if (it > max) max = it }
        }
        val out = arrayOfNulls<String>(max + 1)
        positions.forEach { (word, pos) -> pos.forEach { if (it in out.indices) out[it] = word } }
        return out.filterNotNull().joinToString(" ")
    }

    // --------------------------------------------------------------------- arXiv

    suspend fun arxiv(theme: String): List<ResearchDoc> {
        val q = URLEncoder.encode("all:\"$theme\"", "UTF-8")
        val xml = get("https://export.arxiv.org/api/query?search_query=$q&start=0&max_results=6&sortBy=relevance")
        val entries = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xml).toList()
        val docs = ArrayList<ResearchDoc>()
        entries.take(6).forEachIndexed { i, m ->
            val e = m.groupValues[1]
            val title = cleanXml(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1).orEmpty())
            val summary = cleanXml(Regex("<summary>(.*?)</summary>", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1).orEmpty())
            val id = Regex("<id>(.*?)</id>").find(e)?.groupValues?.get(1)?.trim().orEmpty()
            val year = Regex("<published>(\\d{4})").find(e)?.groupValues?.get(1)?.toIntOrNull()
            if (title.isBlank() || summary.isBlank()) return@forEachIndexed
            docs += ResearchDoc(
                title = title,
                url = id,
                snippet = summary.take(1200),
                source = "arXiv",
                year = year,
                score = 1.25 + (6 - i) * 0.12,
            )
        }
        return docs
    }

    // ------------------------------------------------------------- Stack Exchange

    suspend fun stackExchange(theme: String): List<ResearchDoc> {
        val url = "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance" +
            "&q=${enc(theme)}&site=stackoverflow&pagesize=6&filter=withbody"
        val root = JSONObject(get(url))
        val arr = root.optJSONArray("items") ?: return emptyList()
        val docs = ArrayList<ResearchDoc>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            docs += ResearchDoc(
                title = cleanXml(o.optString("title")),
                url = o.optString("link"),
                snippet = stripHtml(o.optString("body")).take(1200),
                source = "Stack Overflow",
                year = o.optLong("creation_date").takeIf { it > 0 }?.let {
                    java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).year
                },
                score = 1.0 + (6 - i) * 0.08,
            )
        }
        return docs
    }

    // ------------------------------------------------------------------- DuckDuckGo

    suspend fun duckDuckGo(theme: String): List<ResearchDoc> {
        val root = JSONObject(get("https://api.duckduckgo.com/?q=${enc(theme)}&format=json&no_html=1&skip_disambig=1"))
        val docs = ArrayList<ResearchDoc>()
        val abstract = root.optString("AbstractText")
        if (abstract.isNotBlank()) {
            docs += ResearchDoc(
                title = root.optString("Heading").ifBlank { theme },
                url = root.optString("AbstractURL"),
                snippet = abstract.take(1200),
                source = "DuckDuckGo",
                score = 1.3,
            )
        }
        val related = root.optJSONArray("RelatedTopics") ?: JSONArray()
        for (i in 0 until related.length()) {
            val o = related.optJSONObject(i) ?: continue
            val text = o.optString("Text")
            val first = o.optString("FirstURL")
            if (text.isNotBlank() && first.isNotBlank()) {
                docs += ResearchDoc(
                    title = text.substringBefore('.').take(90),
                    url = first,
                    snippet = text.take(600),
                    source = "DuckDuckGo",
                    score = 0.9,
                )
            }
        }
        return docs.take(4)
    }

    // ------------------------------------------------------------ Semantic Scholar

    suspend fun semanticScholar(theme: String): List<ResearchDoc> {
        val url = "https://api.semanticscholar.org/graph/v1/paper/search?query=${enc(theme)}" +
            "&limit=6&fields=title,abstract,url,year"
        val root = JSONObject(get(url))
        val arr = root.optJSONArray("data") ?: return emptyList()
        val docs = ArrayList<ResearchDoc>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val abstract = o.optString("abstract")
            if (abstract.isBlank()) continue
            docs += ResearchDoc(
                title = o.optString("title"),
                url = o.optString("url"),
                snippet = abstract.take(1200),
                source = "Semantic Scholar",
                year = o.optInt("year").takeIf { it > 1900 },
                score = 1.15 + (6 - i) * 0.1,
            )
        }
        return docs
    }

    // ------------------------------------------------------------------- helpers

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun stripHtml(input: String): String = cleanXml(input)
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun cleanXml(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
