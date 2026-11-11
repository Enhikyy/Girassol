package dev.labforge.app

import dev.labforge.app.core.research.Bm25
import dev.labforge.app.core.research.FreeResearchApis
import dev.labforge.app.core.research.ResearchBundle
import dev.labforge.app.core.research.ResearchDoc
import dev.labforge.app.core.research.ResearchRepository
import dev.labforge.app.core.train.AutoTuner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchTest {

    @Test
    fun bm25RanksRelevantDocument() {
        val docs = listOf(
            "SQL joins: inner join, left join and indexes",
            "Gardening tips for sunflowers and soil",
            "Data pipelines with dbt and SQL models",
        )
        val top = Bm25(docs).top("sql join dbt", 2)
        assertTrue(top.contains(0) || top.contains(2))
        assertEquals(0, top.first())
    }

    @Test
    fun autoTunerScalesWithCorpusAndRam() {
        val small = AutoTuner.recommend(tokens = 3_000, docs = 4, ramGb = 4.0, cores = 4)
        val large = AutoTuner.recommend(tokens = 300_000, docs = 20, ramGb = 12.0, cores = 8)
        assertTrue(small.rank < large.rank)
        assertTrue(small.epochs >= large.epochs)
        assertTrue(large.ctx >= 1024)
        assertEquals("attn", small.targets)
    }

    @Test
    fun researchDocJsonRoundTrip() {
        val d = ResearchDoc("T", "https://x.y", "snippet", "Wikipedia", 2024, 1.2)
        val back = ResearchDoc.fromJson(d.toJson())
        assertEquals(d.title, back.title)
        assertEquals(d.url, back.url)
        assertEquals(d.source, back.source)
        assertEquals(d.year, back.year)
    }

    @Test
    fun `research history excludes project files`() {
        val dir = kotlin.io.path.createTempDirectory("girassol-research").toFile()
        try {
            File(dir, "project-sql-123.json").writeText("{}")
            File(dir, "sql-456.json").writeText("{}")
            assertEquals(listOf("sql-456.json"), ResearchRepository(dir).list().map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `bundle save commits complete json and removes temporary file`() {
        val dir = kotlin.io.path.createTempDirectory("girassol-bundle-save").toFile()
        try {
            val target = File(dir, "bundle.json")
            val bundle = ResearchBundle(
                theme = "SQL",
                queries = listOf("joins"),
                docs = listOf(ResearchDoc("Doc", "https://example.com", "snippet", "Test")),
            )
            bundle.save(target)
            assertEquals("SQL", ResearchBundle.load(target)?.theme)
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `failed bundle serialization preserves previous file`() {
        val dir = kotlin.io.path.createTempDirectory("girassol-bundle-atomic").toFile()
        try {
            val target = File(dir, "bundle.json")
            val previous = "{\"theme\":\"original\"}"
            target.writeText(previous)
            val invalid = ResearchBundle(
                theme = "broken",
                queries = emptyList(),
                docs = listOf(ResearchDoc("Doc", "https://example.com", "snippet", "Test", score = Double.NaN)),
            )
            assertTrue(runCatching { invalid.save(target) }.isFailure)
            assertEquals(previous, target.readText())
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun htmlStripping() {
        assertEquals("Olá mundo", FreeResearchApis.stripHtml("<p>Olá <b>mundo</b></p>"))
    }
}
