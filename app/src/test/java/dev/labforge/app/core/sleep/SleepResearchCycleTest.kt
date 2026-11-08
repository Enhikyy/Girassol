package dev.labforge.app.core.sleep

import dev.labforge.app.core.research.ResearchBundle
import dev.labforge.app.core.research.ResearchDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepResearchCycleTest {
    private val doc = ResearchDoc("Fonte", "https://example.com", "texto", "teste")
    private val bundle = ResearchBundle(theme = "Android", queries = listOf("Android"), docs = listOf(doc))

    @Test
    fun publishesEnrichedResult() = runBlocking {
        var published: ResearchBundle? = null
        runSleepCycle(
            research = { bundle },
            enrich = { it.copy(docs = listOf(doc.copy(snippet = "texto enriquecido"))) },
            publish = { published = it },
        )
        assertEquals("texto enriquecido", published?.docs?.single()?.snippet)
    }

    @Test
    fun emptyResearchDoesNotPublishOrDownload() = runBlocking {
        var enriched = false
        var published = false
        try {
            runSleepCycle(
                research = { bundle.copy(docs = emptyList()) },
                enrich = { enriched = true; it },
                publish = { published = true },
            )
        } catch (_: IllegalStateException) {
            // The worker retries instead of replacing the last successful result.
        }
        assertFalse(enriched)
        assertFalse(published)
    }

    @Test
    fun cancellationIsPropagatedWithoutPublishing() = runBlocking {
        var published = false
        var cancelled = false
        try {
            runSleepCycle(
                research = { bundle },
                enrich = { throw CancellationException("cancelled") },
                publish = { published = true },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(published)
    }
}
