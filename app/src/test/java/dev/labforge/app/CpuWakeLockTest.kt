package dev.labforge.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.labforge.app.service.CpuWakeLock
import dev.labforge.app.kiosk.KioskController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CpuWakeLockTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun releaseAllLocks() {
        CpuWakeLock.releaseManual()
        CpuWakeLock.releaseJob()
    }

    @Test
    fun manualWakeLockCanBeAcquiredAndReleased() {
        assertFalse(CpuWakeLock.isHeld())

        assertTrue(CpuWakeLock.acquire(context))
        assertTrue(CpuWakeLock.isHeld())

        CpuWakeLock.releaseManual()
        assertFalse(CpuWakeLock.isHeld())
    }

    @Test
    fun releasingJobLeaseDoesNotReleaseManualLease() {
        assertTrue(CpuWakeLock.acquire(context))
        assertTrue(CpuWakeLock.acquireForJob(context))

        CpuWakeLock.releaseJob()
        assertTrue(CpuWakeLock.isHeld())

        CpuWakeLock.releaseManual()
        assertFalse(CpuWakeLock.isHeld())
    }

    @Test
    fun wakeLockTimeoutIsClampedToSafeRange() {
        assertTrue(CpuWakeLock.acquire(context, timeoutMs = 1L))
        assertTrue(CpuWakeLock.isHeld())
    }

    @Test
    fun kioskProvisioningCommandUsesApplicationPackage() {
        assertEquals(
            "adb shell dpm set-device-owner app.girassol/dev.labforge.app.admin.GirassolDeviceAdminReceiver",
            KioskController.provisioningCommand(context.packageName),
        )
    }
}
