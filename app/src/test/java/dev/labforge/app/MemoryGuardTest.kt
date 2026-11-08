package dev.labforge.app

import dev.labforge.app.service.MemoryGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {
    @Test
    fun fourGbPhoneRejectsLargeLoraBeforeNativeAllocation() {
        val budget = MemoryGuard.evaluate(
            totalGb = 4.0, availableGb = 1.4, modelBytes = 1_500_000_000L,
            mode = "lora", nCtx = 256, rank = 4,
        )
        assertFalse(budget.ok)
        assertTrue(budget.message.contains("bloqueado"))
        assertTrue(budget.requiredGb > budget.reserveGb)
    }

    @Test
    fun smallLoRACanProceedWhenMeasuredFreeRamCoversConservativeBudget() {
        val budget = MemoryGuard.evaluate(
            totalGb = 4.0, availableGb = 2.0, modelBytes = 150_000_000L,
            mode = "lora", nCtx = 256, rank = 4,
        )
        assertTrue(budget.ok)
    }

    @Test
    fun fullFineTuneIsRejectedOnFourGbDeviceEvenWhenFreeRamLooksHigh() {
        val budget = MemoryGuard.evaluate(
            totalGb = 4.0, availableGb = 3.5, modelBytes = 80_000_000L,
            mode = "full", nCtx = 128, rank = 4,
        )
        assertFalse(budget.ok)
        assertTrue(budget.message.contains("8 GB"))
    }

    @Test
    fun fullFineTuneRequiresBothTotalAndAvailableMemory() {
        val enough = MemoryGuard.evaluate(8.0, 6.2, 1_000_000_000L, "full", 256, 8)
        val insufficient = MemoryGuard.evaluate(8.0, 4.0, 1_000_000_000L, "full", 256, 8)
        assertTrue(enough.ok)
        assertFalse(insufficient.ok)
    }
}