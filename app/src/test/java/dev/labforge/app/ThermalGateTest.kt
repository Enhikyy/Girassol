package dev.labforge.app

import android.os.PowerManager
import dev.labforge.app.core.autonomy.ThermalGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGateTest {

    @Test
    fun `normal and light thermal states allow work`() {
        assertTrue(ThermalGate.allowsWork(ThermalGate.UNSUPPORTED))
        assertTrue(ThermalGate.allowsWork(PowerManager.THERMAL_STATUS_NONE))
        assertTrue(ThermalGate.allowsWork(PowerManager.THERMAL_STATUS_LIGHT))
    }

    @Test
    fun `moderate and above block work`() {
        assertFalse(ThermalGate.allowsWork(PowerManager.THERMAL_STATUS_MODERATE))
        assertFalse(ThermalGate.allowsWork(PowerManager.THERMAL_STATUS_SEVERE))
        assertFalse(ThermalGate.allowsWork(PowerManager.THERMAL_STATUS_CRITICAL))
    }
}
