package dev.labforge.app

import dev.labforge.app.core.research.ResearchDoc
import dev.labforge.app.core.research.ResearchDownloader
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ResearchDownloaderTest {
    @Test
    fun removesHtmlScriptsAndDecodesEntities() {
        val html = "<html><script>not training data</script><style>.x{}</style>" +
            "<main><h1>Aprendizado</h1><p>Olá &amp; mundo &#128640;</p></main></html>"
        val text = ResearchDownloader.toPlainText(html, "text/html")
        assertTrue(text.contains("Aprendizado"))
        assertTrue(text.contains("Olá & mundo 🚀"))
        assertFalse(text.contains("not training data"))
        assertFalse(text.contains(".x{}"))
    }

    @Test
    fun keepsPlainMarkdownReadable() {
        val markdown = "# Dataset\n\n- item A\n- item B"
        assertEquals(markdown, ResearchDownloader.toPlainText(markdown, "text/markdown"))
    }

    @Test(timeout = 10_000)
    fun concurrentDownloadsOfSameUrlUseIndependentTemporaryFiles() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("girassol-download-concurrent").toFile()
        val (server, client) = httpsServer()
        try {
            val content = "Conteúdo técnico suficientemente longo para integrar o corpus de pesquisa e treinamento do Girassol."
            repeat(2) {
                server.enqueue(
                    MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "text/plain; charset=utf-8")
                        .setBody(content),
                )
            }
            val url = server.url("/document").toString()
            val documents = listOf("A", "B").map { ResearchDoc(it, url, "", "Test") }
            val result = ResearchDownloader(directory, client).enrich(documents, maxDocuments = 2)
            assertEquals(2, result.downloadedDocuments)
            assertTrue(result.documents.all { content in it.snippet })
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test(timeout = 10_000)
    fun cancellingDownloadInterruptsStreamingAndLeavesNoPartialFiles() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("girassol-download-cancel").toFile()
        val (server, client) = httpsServer()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody("Conteúdo que só será entregue depois do atraso e não deve ser armazenado após o cancelamento.")
                    .setBodyDelay(5, TimeUnit.SECONDS),
            )
            val document = ResearchDoc("Lento", server.url("/slow").toString(), "", "Test")
            val job = launch { ResearchDownloader(directory, client).enrich(listOf(document)) }
            delay(300)
            withTimeout(2_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") || it.name.endsWith(".txt") })
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun httpsServer(): Pair<MockWebServer, OkHttpClient> {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        return server to client
    }
}
