package dev.labforge.app

import dev.labforge.app.core.research.ResearchDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    @Test
    fun githubResearchPrefersReadmeFromMainAndMasterBranches() {
        val candidates = ResearchDownloader.githubReadmeCandidates("https://github.com/ggml-org/llama.cpp")
        assertEquals(6, candidates.size)
        assertEquals("https://raw.githubusercontent.com/ggml-org/llama.cpp/main/README.md", candidates.first())
        assertTrue(candidates.last().endsWith("/master/README"))
        assertTrue(ResearchDownloader.githubReadmeCandidates("https://example.com/owner/repo").isEmpty())
    }
}
