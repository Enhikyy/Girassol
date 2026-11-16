package dev.labforge.app

import dev.labforge.app.ui.world.CinematicRenderBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicRenderBudgetTest {
    @Test
    fun portraitDisplayRespectsEdgeAndMemoryBudget() {
        val size = CinematicRenderBudget.targetSize(1080, 2400)
        assertEquals(1600, size.height)
        assertEquals(720, size.width)
        assertTrue(size.estimatedBytes <= CinematicRenderBudget.DEFAULT_MAX_BYTES)
    }

    @Test
    fun landscapeDisplayRespectsMaximumEdge() {
        val size = CinematicRenderBudget.targetSize(3840, 2160)
        assertEquals(1600, size.width)
        assertTrue(size.height < 1000)
        assertTrue(size.estimatedBytes <= CinematicRenderBudget.DEFAULT_MAX_BYTES)
    }

    @Test
    fun invalidDimensionsAreSafelyClamped() {
        val size = CinematicRenderBudget.targetSize(0, -20)
        assertEquals(1, size.width)
        assertEquals(1, size.height)
        assertTrue(size.estimatedBytes > 0)
    }
}
