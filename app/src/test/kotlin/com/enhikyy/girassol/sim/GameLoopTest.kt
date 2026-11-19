package com.enhikyy.girassol.sim

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLoopTest {
    private class RecordingSimulation : Simulation {
        val steps = mutableListOf<Long>()
        override suspend fun step(dtMs: Long) {
            steps += dtMs
        }
    }

    @Test
    fun adaptiveBudgetChangesActualSimulationStepSize() = runBlocking {
        val simulation = RecordingSimulation()
        val loop = GameLoop(simulation, defaultStepMs = 100L, maxCatchUpSteps = 10)
        loop.reset(wallMs = 0L)

        val report = loop.advance(nowWallMs = 1_000L, stepMs = 500L)

        assertEquals(listOf(500L, 500L), simulation.steps)
        assertEquals(2, report.steps)
        assertEquals(1_000L, report.simulatedMs)
        assertFalse(report.coarse)
    }

    @Test
    fun largeDelayUsesOneCoarseStepAfterBoundedCatchUp() = runBlocking {
        val simulation = RecordingSimulation()
        val loop = GameLoop(simulation, defaultStepMs = 100L, maxCatchUpSteps = 2)
        loop.reset(wallMs = 0L)

        val report = loop.advance(nowWallMs = 1_000L, stepMs = 100L)

        assertEquals(listOf(100L, 100L, 800L), simulation.steps)
        assertEquals(3, report.steps)
        assertEquals(1_000L, report.simulatedMs)
        assertTrue(report.coarse)
    }
}
