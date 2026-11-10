package dev.labforge.app.core.research

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Ingests readable source pages into the research corpus. Downloaded content is
 * merged into ResearchDoc.snippet before synthesis and training dataset creation.
 */
class ResearchDownloader(
    private val directory: File,
    private val client: OkHttpClient = defaultClient(),
) {
    data class Result(
        val documents: List<ResearchDoc>,
        val downloadedDocuments: Int,
        val bytesWritten: Long,
    )

    private data class DownloadedText(val text: String, val bytes: Long)

    init { directory.mkdirs() }

    suspend fun enrich(documents: List<ResearchDoc>, maxDocuments: Int = 8): Result = coroutineScope {
        val count = minOf(maxDocuments.coerceAtLeast(0), documents.size)
        val semaphore = Semaphore(3)
        val fetched = documents.take(count).map { document ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        downloadText(document.url)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll()

        var downloaded = 0
        var bytes = 0L
        val enriched = documents.mapIndexed { index, document ->
            val stored = fetched.getOrNull(index) ?: return@mapIndexed document
            val normalized = stored.text.take(MAX_TEXT_CHARS).trim()
            if (normalized.length < MIN_TEXT_CHARS) return@mapIndexed document
            downloaded++
            bytes += stored.bytes
            val merged = buildString {
                append(document.snippet.trim())
                if (isNotEmpty()) append("\n\n")
                append("[Conteúdo obtido de ").append(document.url).append("]\n")
                append(normalized)
            }.take(MAX_ENRICHED_CHARS)
            document.copy(snippet = merged)
        }
        Result(enriched, downloaded, bytes)
    }

    private suspend fun downloadText(url: String): DownloadedText? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank()) return null
        val key = sha256(url.toByteArray(Charsets.UTF_8)).take(24)
        val destination = File(directory, key + ".txt")
        if (destination.isFile && destination.length() in 1L..MAX_DOCUMENT_BYTES) {
            val cached = destination.readText(Charsets.UTF_8)
            return DownloadedText(cached, destination.length())
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/plain, text/markdown, text/html, application/json, application/xml, text/xml;q=0.9")
            .get()
            .build()

        val call = client.newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful || !response.request.url.isHttps) return null
                val body = response.body ?: return null
                val type = body.contentType()?.toString()?.lowercase().orEmpty()
                val readable = type.startsWith("text/") || "json" in type || "xml" in type
                if (!readable || body.contentLength() > MAX_DOCUMENT_BYTES) return null
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val temporary = File.createTempFile("$key-", ".part", directory)
                try {
                    var copied = 0L
                    body.byteStream().use { input ->
                        temporary.outputStream().buffered(32 * 1024).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                copied += read
                                require(copied <= MAX_DOCUMENT_BYTES) { "documento excede o tamanho de ingestão permitido" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (copied == 0L) return null
                    val normalized = toPlainText(temporary.readText(charset), type)
                    if (normalized.length < MIN_TEXT_CHARS) return null
                    temporary.writeText(normalized, Charsets.UTF_8)
                    currentCoroutineContext().ensureActive()
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    return DownloadedText(normalized, destination.length())
                } finally {
                    temporary.delete()
                }
            }
        } finally {
            cancellation.dispose()
        }
    }

    companion object {
        private const val USER_AGENT = "Girassol/1.6 Android research client"
        private const val MAX_DOCUMENT_BYTES = 4_000_000L
        private const val MAX_TEXT_CHARS = 24_000
        private const val MAX_ENRICHED_CHARS = 28_000
        private const val MIN_TEXT_CHARS = 80

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        internal fun toPlainText(input: String, contentType: String = "text/html"): String {
            var text = input
            if (contentType.contains("html") || contentType.contains("xml")) {
                text = text
                    .replace(Regex("(?is)<(script|style|noscript|svg)\\b[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("(?is)<!--.*?-->"), " ")
                    .replace(Regex("(?s)<[^>]+>"), " ")
            }
            return decodeEntities(text)
                .replace('\u00a0', ' ')
                .replace(Regex("[\\t\\r ]+"), " ")
                .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
                .trim()
        }

        private fun decodeEntities(value: String): String = value
            .replace("&amp;", "&", true)
            .replace("&lt;", "<", true)
            .replace("&gt;", ">", true)
            .replace("&quot;", "\"", true)
            .replace("&#39;", "'", true)
            .replace("&apos;", "'", true)
            .replace("&nbsp;", " ", true)
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.let { codePoint ->
                    runCatching { String(Character.toChars(codePoint)) }.getOrNull()
                } ?: match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.let { codePoint ->
                    runCatching { String(Character.toChars(codePoint)) }.getOrNull()
                } ?: match.value
            }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
