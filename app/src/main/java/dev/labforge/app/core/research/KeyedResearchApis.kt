package dev.labforge.app.core.research

import dev.labforge.app.core.github.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Optional credentials that unlock extra research sources. Blank = source skipped. */
data class ResearchKeys(val brave: String = "", val github: String = "")

/**
 * Research sources that use (or benefit from) a user-provided key: Brave Search
 * (key required) and GitHub repository search (key optional, raises rate limits).
 */
object KeyedResearchApis {

    private const val MAX_BODY_CHARS = 4_000_000

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun brave(theme: String, key: String, count: Int = 10): List<ResearchDoc> {
        if (key.isBlank()) return emptyList()
        val url = "https://api.search.brave.com/res/v1/web/search?q=${GitHubApi.enc(theme)}" +
            "&count=$count&extra_snippets=true&safesearch=moderate"
        val json = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", key.trim())
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Brave HTTP ${resp.code}")
                resp.body?.string().orEmpty().also { if (it.length > MAX_BODY_CHARS) error("resposta grande demais") }
            }
        }
        return parseBrave(json)
    }

    fun parseBrave(json: String): List<ResearchDoc> {
        val arr = JSONObject(json).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        val n = arr.length()
        return (0 until n).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val extra = o.optJSONArray("extra_snippets")
                ?.let { a -> (0 until a.length()).joinToString(" ") { a.optString(it) } }
                .orEmpty()
            val url = o.optString("url")
            if (!url.startsWith("http")) return@mapNotNull null
            ResearchDoc(
                title = clean(o.optString("title")),
                url = url,
                snippet = clean("${o.optString("description")} $extra").take(1200),
                source = "Brave Search",
                score = 1.2 + (n - i) * 0.08,
            )
        }
    }

    suspend fun githubRepos(theme: String, token: String = ""): List<ResearchDoc> = parseGithubRepos(
        GitHubApi.request(
            "GET",
            "/search/repositories?q=${GitHubApi.enc(theme)}&sort=stars&order=desc&per_page=6",
            token,
        ),
    )

    fun parseGithubRepos(json: String): List<ResearchDoc> {
        val arr = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val topics = o.optJSONArray("topics")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val lang = o.optString("language").takeIf { it.isNotBlank() && it != "null" }
            val desc = o.optString("description").takeIf { it != "null" }.orEmpty()
            val snippet = buildString {
                append(desc)
                append(" · ★ ").append(o.optInt("stargazers_count"))
                lang?.let { append(" · ").append(it) }
                if (topics.isNotEmpty()) append(" · tópicos: ").append(topics.take(8).joinToString(", "))
            }
            ResearchDoc(
                title = o.optString("full_name"),
                url = o.optString("html_url"),
                snippet = snippet.take(1200),
                source = "GitHub",
                year = o.optString("pushed_at").take(4).toIntOrNull(),
                score = 1.0 + (6 - i) * 0.1,
            )
        }
    }

    private fun clean(s: String): String = s
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
}
