package dev.labforge.app

import dev.labforge.app.core.train.AutoTuner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTunerTest {
    @Test
    fun fourGbProfileStartsWithSmallLoRAWorkspace() {
        val preset = AutoTuner.recommend(
            tokens = 12_000,
            docs = 4,
            ramGb = 4.0,
            cores = 8,
            params = 120_000_000L,
        )
        assertEquals(2, preset.rank)
        assertEquals(128, preset.ctx)
        assertEquals("attn", preset.targets)
        assertTrue(preset.epochs in 1..2)
    }

    @Test
    fun largerDevicesRetainConfigurableTrainingCapacity() {
        val preset = AutoTuner.recommend(
            tokens = 60_000,
            docs = 12,
            ramGb = 8.0,
            cores = 8,
            params = 1_000_000_000L,
        )
        assertTrue(preset.rank >= 8)
        assertTrue(preset.ctx >= 512)
    }
}
