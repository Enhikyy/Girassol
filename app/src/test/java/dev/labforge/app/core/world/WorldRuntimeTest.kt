package dev.labforge.app.core.world

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WorldRuntimeTest {
    @Test
    fun mutationRequestedDuringLoadIsAppliedToInstalledSnapshot() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = WorldSnapshotStore(File(app.filesDir, "world"))
        val persisted = stableWorld(seed = 101L)
        store.save(persisted)
        val runtime = testRuntime(app)

        val mutation = launch(start = CoroutineStart.UNDISPATCHED) {
            runtime.setPlayback(paused = true, speed = 16)
        }
        assertTrue("A mutação deveria aguardar o carregamento.", mutation.isActive)

        runCurrent()

        assertTrue(mutation.isCompleted)
        assertEquals(persisted.id, runtime.state.value.id)
        assertTrue(runtime.state.value.paused)
        assertEquals(16, runtime.state.value.timeScale)
        assertFalse(runtime.status.value.loading)
    }

    @Test
    fun recoveryWarningSurvivesStartupSaveUntilAcknowledged() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.filesDir, "world")
        val store = WorldSnapshotStore(directory)
        val initial = stableWorld(seed = 102L)
        store.save(initial)
        store.save(initial.copy(tick = 1L))
        File(directory, "world-v1.json").writeText("{broken")
        val runtime = testRuntime(app)

        runCurrent()

        assertTrue(runtime.status.value.recoveredFromBackup)
        assertNotNull(runtime.status.value.recoveryWarning)
        assertNull(runtime.status.value.warning)
        assertTrue(runtime.status.value.lastSavedAt > 0L)

        runtime.acknowledgeRecoveryWarning()
        assertNull(runtime.status.value.recoveryWarning)
    }

    @Test
    fun pausedHappeningIsPersistedAfterDebounceWithoutDisposal() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.filesDir, "world")
        val store = WorldSnapshotStore(directory)
        store.save(stableWorld(seed = 103L).copy(paused = true))
        val runtime = testRuntime(app)
        runCurrent()
        val description = "Persistência pausada verificável"

        val mutation = launch(start = CoroutineStart.UNDISPATCHED) {
            runtime.applyHappening(HappeningType.FESTIVAL, description)
        }
        runCurrent()

        assertTrue(mutation.isCompleted)
        assertTrue(runtime.state.value.events.any { it.description == description })
        assertFalse(store.load(seed = 1L, nowWallClockMs = 1L).state.events.any { it.description == description })

        advanceTimeBy(399L)
        runCurrent()
        assertFalse(store.load(seed = 1L, nowWallClockMs = 1L).state.events.any { it.description == description })

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(store.load(seed = 1L, nowWallClockMs = 1L).state.events.any { it.description == description })
    }

    private fun TestScope.testRuntime(app: Application) = WorldRuntime(
        app = app,
        scope = backgroundScope,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun stableWorld(seed: Long) = WorldFactory.create(
        seed = seed,
        population = 16,
        nowWallClockMs = 0L,
    ).let { world ->
        world.copy(parameters = world.parameters.copy(offlineCatchUpEnabled = false))
    }
}
