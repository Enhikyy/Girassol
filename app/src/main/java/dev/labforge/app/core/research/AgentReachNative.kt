package dev.labforge.app.core.research

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Android-native subset of Agent Reach: public web, GitHub, Reddit, X, LinkedIn and Instagram search,
 * plus universal public-page reading through Jina Reader. It never imports browser
 * cookies or performs writes/social actions.
 */
object AgentReachNative {
    private const val MAX_BYTES = 512 * 1024
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Result(val provider: String, val title: String, val url: String, val text: String)

    suspend fun search(query: String, providers: Set<String> = setOf("web", "github", "reddit", "x", "linkedin", "instagram")): List<Result> =
        withContext(Dispatchers.IO) {
            require(query.trim().length >= 2) { "consulta muito curta" }
            coroutineScope {
                providers.map { provider ->
                    async {
                        try {
                            when (provider.lowercase()) {
                                "web" -> searchWeb(query)
                                "github" -> searchGitHub(query)
                                "reddit" -> searchReddit(query)
                                "x" -> searchWeb("site:x.com " + query).map { it.copy(provider = "X · público") }
                                "linkedin" -> searchWeb("site:linkedin.com " + query).map { it.copy(provider = "LinkedIn · público") }
                                "instagram" -> searchWeb("site:instagram.com " + query).map { it.copy(provider = "Instagram · público") }
                                else -> emptyList()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten().distinctBy { it.url.substringBefore("?") }.take(15)
            }
        }

    suspend fun read(rawUrl: String): Result = withContext(Dispatchers.IO) {
        val url = validatePublicUrl(rawUrl)
        val response = get("https://r.jina.ai/$url", "text/plain, text/markdown;q=0.9")
        val title = response.lineSequence().map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.length in 3..180 } ?: URI(url).host
        Result("Jina Reader", title, url, response.take(80_000))
    }

    internal fun validatePublicUrl(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrElse { error("URL inválida") }
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
            "somente URLs HTTP/HTTPS públicas são aceitas"
        }
        require(uri.userInfo.isNullOrBlank()) { "URLs com credenciais embutidas são bloqueadas" }
        val host = uri.host?.lowercase()?.trimEnd('.') ?: error("host inválido")
        require(host.contains(".") || host.contains(":")) { "hosts locais de rótulo único são bloqueados" }
        require(host !in setOf("localhost", "localhost.localdomain") &&
            !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")) {
            "endereços locais/internos são bloqueados"
        }
        require(uri.port == -1 || uri.port == 80 || uri.port == 443) { "porta não permitida" }
        val literal = host.matches(Regex("[0-9.]+")) || host.contains(':')
        if (literal) {
            val address = runCatching { InetAddress.getByName(host) }.getOrElse { error("IP inválido") }
            require(!address.isAnyLocalAddress && !address.isLoopbackAddress &&
                !address.isLinkLocalAddress && !address.isSiteLocalAddress && !address.isMulticastAddress) {
                "endereços IP privados ou locais são bloqueados"
            }
        }
        return uri.toASCIIString().substringBefore("#")
    }

    private fun searchWeb(query: String): List<Result> {
        val q = URLEncoder.encode(query, "UTF-8")
        val text = get("https://s.jina.ai/$q", "text/plain")
        val links = Regex("(?m)^.*?(https?://[^\\s)\\]>]+).*$").findAll(text)
            .map { it.groupValues[1].trimEnd('.', ',', ';') }.distinct().take(8).toList()
        if (links.isEmpty()) return listOf(Result("Jina Search", query, "https://s.jina.ai/$q", text.take(3000)))
        return links.mapIndexed { i, link ->
            val block = text.substringAfter(link, "").lineSequence().take(5).joinToString(" ").trim()
            Result("Jina Search", block.take(160).ifBlank { "Resultado ${i + 1}" }, link, block.take(1200))
        }
    }

    private fun searchGitHub(query: String): List<Result> {
        val q = URLEncoder.encode(query, "UTF-8")
        val root = JSONObject(get("https://api.github.com/search/repositories?q=$q&sort=stars&per_page=6", "application/vnd.github+json"))
        val items = root.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            Result(
                "GitHub",
                item.optString("full_name"),
                item.optString("html_url"),
                item.optString("description").ifBlank { "Repositório público · ${item.optInt("stargazers_count")} stars" },
            )
        }
    }

    private fun searchReddit(query: String): List<Result> {
        val q = URLEncoder.encode(query, "UTF-8")
        val root = JSONObject(get("https://www.reddit.com/search.json?q=$q&sort=relevance&limit=6", "application/json"))
        val children = root.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()
        return (0 until children.length()).mapNotNull { i ->
            val item = children.optJSONObject(i)?.optJSONObject("data") ?: return@mapNotNull null
            val permalink = item.optString("permalink")
            if (permalink.isBlank()) return@mapNotNull null
            Result(
                "Reddit",
                item.optString("title"),
                "https://www.reddit.com$permalink",
                item.optString("selftext").take(1200),
            )
        }
    }

    private fun get(url: String, accept: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Girassol-Android/2.1 (public read-only research)")
            .header("Accept", accept).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} em ${response.request.url.host}")
            val body = response.body ?: error("resposta vazia")
            require(body.contentLength() <= MAX_BYTES || body.contentLength() < 0) { "resposta acima do limite de leitura" }
            val out = body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                val bytes = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "resposta acima do limite de leitura" }
                    bytes.write(buffer, 0, n)
                }
                bytes.toByteArray()
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
