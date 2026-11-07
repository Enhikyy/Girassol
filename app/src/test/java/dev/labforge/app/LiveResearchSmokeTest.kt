package dev.labforge.app

import dev.labforge.app.core.research.FreeResearchApis
import dev.labforge.app.core.research.ResearchEngine
import dev.labforge.app.core.research.ResearchRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network smoke test: LIVE_API=1 ./gradlew test --tests ... */
class LiveResearchSmokeTest {

    @Test
    fun wikipediaReturnsDocs() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val docs = FreeResearchApis.wikipedia("engenharia de dados")
        assertTrue("wikipedia devolveu ${docs.size}", docs.isNotEmpty())
        assertTrue(docs.first().snippet.length > 40)
    }

    @Test
    fun openAlexReturnsDocs() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val docs = FreeResearchApis.openAlex("retrieval augmented generation")
        assertTrue("openalex devolveu ${docs.size}", docs.isNotEmpty())
    }

    @Test
    fun arxivReturnsDocs() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val docs = FreeResearchApis.arxiv("lora fine tuning")
        assertTrue("arxiv devolveu ${docs.size}", docs.isNotEmpty())
    }

    @Test
    fun stackExchangeReturnsDocs() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val docs = FreeResearchApis.stackExchange("kotlin coroutines")
        assertTrue("stackexchange devolveu ${docs.size}", docs.isNotEmpty())
    }

    @Test
    fun enginePersistsBundle() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val dir = File(System.getProperty("java.io.tmpdir"), "girassol-research-${System.nanoTime()}")
        val engine = ResearchEngine(ResearchRepository(dir))
        val bundle = engine.research("engenharia de dados")
        assertTrue("engine devolveu ${bundle.docs.size}", bundle.docs.size >= 5)
        assertTrue(File(dir, "latest.json").exists())
        assertTrue(bundle.sourceCounts.size >= 2)
    }
}
