package dev.labforge.app

import dev.labforge.app.core.world.WorldSimulationEngine
import dev.labforge.app.core.world.WorldSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldSimulationEngineTest {
    private val engine=WorldSimulationEngine()

    @Test fun initialWorldContainsSixteenResidents() {
        assertEquals(16,WorldSnapshot.initial().residents.size)
    }

    @Test fun elapsedTimeAdvancesWorldAndKeepsDrivesWithinBounds() {
        val result=engine.advance(WorldSnapshot.initial(),3_600.0)
        assertTrue(result.simulatedMinutes>=54_000L)
        assertEquals(16,result.residents.size)
        result.residents.forEach {
            assertTrue(it.energy in 0f..100f)
            assertTrue(it.social in 0f..100f)
            assertTrue(it.knowledge in 0f..100f)
            assertTrue(it.focus in 0f..100f)
            assertTrue(it.cooperation in 0f..1f)
        }
    }

    @Test fun zeroElapsedTimeDoesNotMutateSnapshot() {
        val initial=WorldSnapshot.initial()
        assertEquals(initial,engine.advance(initial,0.0))
    }
}
