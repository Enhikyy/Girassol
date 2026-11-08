package dev.labforge.app.core.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Minimal GitHub REST v3 client. The token is optional for public reads. */
object GitHubApi {
    private const val BASE = "https://api.github.com"
    private const val MAX_BODY_CHARS = 4_000_000
    private val REPO = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun isRepo(s: String) = REPO.matches(s)

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun encPath(path: String): String =
        path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { enc(it) }

    fun headers(token: String): Map<String, String> = buildMap {
        put("Accept", "application/vnd.github+json")
        put("X-GitHub-Api-Version", "2022-11-28")
        put("User-Agent", "Girassol-Agent")
        if (token.isNotBlank()) put("Authorization", "Bearer ${token.trim()}")
    }

    suspend fun request(method: String, path: String, token: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            require(path.startsWith("/")) { "caminho da API inválido" }
            val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
            val builder = Request.Builder().url(BASE + path).method(method, payload)
            headers(token).forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (text.length > MAX_BODY_CHARS) error("resposta grande demais")
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                    error("GitHub HTTP ${resp.code}: ${msg.ifBlank { text.take(200) }}")
                }
                text
            }
        }
}
