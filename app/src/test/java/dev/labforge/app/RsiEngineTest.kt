package dev.labforge.app

import dev.labforge.app.core.rsi.RsiEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RsiEngineTest {
    @Test fun profilesArePersistedAndUnknownProfilesAreRejected() {
        val file = File(Files.createTempDirectory("girassol-rsi").toFile(), "rsi.json")
        val engine = RsiEngine(file)
        assertTrue(engine.setActiveProfile("compact"))
        assertFalse(engine.setActiveProfile("unknown"))
        assertEquals("compact", RsiEngine(file).activeProfile().id)
    }

    @Test fun candidateCannotBePromotedWithoutMeasuredBaselineAndCandidateRuns() {
        val engine = RsiEngine(File(Files.createTempDirectory("girassol-rsi-gate").toFile(), "rsi.json"))
        repeat(5) { index ->
            engine.recordRun("balanced", true, 2, 0, 100, 40, 1000, at = index.toLong())
        }
        assertNull(engine.snapshot().recommendedProfileId)
        assertFalse(engine.promote("compact"))
    }

    @Test fun measuredLowerCostCandidateCanBeRecommendedAndPromotedExplicitly() {
        val engine = RsiEngine(File(Files.createTempDirectory("girassol-rsi-pareto").toFile(), "rsi.json"))
        repeat(5) { index ->
            engine.recordRun("balanced", true, 3, 0, 200, 100, 2000, at = index.toLong())
            engine.recordRun("compact", true, 2, 0, 120, 60, 1300, at = index.toLong())
        }
        val snapshot = engine.snapshot()
        assertEquals("compact", snapshot.recommendedProfileId)
        assertEquals("balanced", snapshot.activeProfileId)
        assertTrue(engine.promote("compact"))
        assertEquals("compact", engine.activeProfile().id)
    }

    @Test fun candidateWithQualityRegressionIsNotRecommended() {
        val engine = RsiEngine(File(Files.createTempDirectory("girassol-rsi-regression").toFile(), "rsi.json"))
        repeat(5) { index ->
            engine.recordRun("balanced", true, 2, 0, 100, 40, 1000, at = index.toLong())
            engine.recordRun("deliberate", index == 0, 4, 2, 200, 100, 3000, at = index.toLong())
        }
        assertNull(engine.snapshot().recommendedProfileId)
    }

    @Test fun runHistoryIsReloadedAndMetricsAreComputed() {
        val file = File(Files.createTempDirectory("girassol-rsi-reload").toFile(), "rsi.json")
        val engine = RsiEngine(file)
        engine.recordRun("balanced", true, 4, 1, 80, 20, 900, at = 10L)
        val metrics = RsiEngine(file).snapshot().metrics.first { it.profile.id == "balanced" }
        assertEquals(1, metrics.samples)
        assertEquals(1.0, metrics.successRate, 0.0)
        assertEquals(100.0, metrics.meanTokens, 0.0)
        assertEquals(0.25, metrics.toolFailureRate, 0.0)
    }
}
