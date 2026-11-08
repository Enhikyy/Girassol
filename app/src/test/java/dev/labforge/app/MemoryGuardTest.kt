package dev.labforge.app

import dev.labforge.app.service.MemoryGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {
    @Test
    fun fourGbPhoneCanUseMmapBaseWhenAdapterWorkingSetFits() {
        val budget = MemoryGuard.evaluate(
            totalGb = 4.0, availableGb = 1.4, modelBytes = 1_500_000_000L,
            mode = "lora", nCtx = 256, rank = 4,
        )
        assertTrue(budget.ok)
        assertTrue(budget.requiredGb > budget.reserveGb)
    }

    @Test
    fun mmapLoraBudgetDoesNotTreatEntireBaseModelAsResidentHeap() {
        val small = MemoryGuard.evaluate(4.0, 2.0, 200_000_000L, "lora", 256, 4)
        val large = MemoryGuard.evaluate(4.0, 2.0, 8_000_000_000L, "lora", 256, 4)
        assertTrue(large.requiredGb - small.requiredGb < 0.30)
        assertTrue(large.ok)
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
    fun rankTwoContext128LoRAFitsWithARealisticFourGbReserve() {
        val budget = MemoryGuard.evaluate(
            totalGb = 4.0, availableGb = 1.2, modelBytes = 1_500_000_000L,
            mode = "lora", nCtx = 128, rank = 2,
        )
        assertTrue(budget.ok)
        assertTrue(budget.requiredGb <= 1.2)
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