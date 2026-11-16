package dev.labforge.app.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorldSnapshotStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun snapshotRoundTripPreservesAllSimulationState() {
        val store = WorldSnapshotStore(folder.root)
        val engine = WorldEngine(WorldFactory.create(seed = 33L, population = 24, nowWallClockMs = 123L))
        repeat(12) { engine.step(10, wallClockMs = 123L, detailedIds = setOf("agent_1")) }
        store.save(engine.state)
        val loaded = store.load(seed = 99L, nowWallClockMs = 999L)
        assertEquals(engine.state, loaded.state)
        assertEquals("world-v1.json", loaded.source)
        assertFalse(loaded.recoveredFromBackup)
        assertNull(loaded.warning)
    }

    @Test
    fun corruptPrimaryFallsBackToLastKnownGoodSnapshot() {
        val store = WorldSnapshotStore(folder.root)
        val initial = WorldFactory.create(seed = 8L, population = 16, nowWallClockMs = 5L)
        store.save(initial)
        val engine = WorldEngine(initial)
        engine.step(60, wallClockMs = 6L)
        store.save(engine.state)
        File(folder.root, "world-v1.json").writeText("{broken")
        val loaded = store.load(seed = 44L, nowWallClockMs = 10L)
        assertTrue(loaded.recoveredFromBackup)
        assertEquals(initial, loaded.state)
        assertNotNull(loaded.warning)
    }

    @Test
    fun invalidSnapshotsAreNotSilentlyOverwrittenOnLoad() {
        val store = WorldSnapshotStore(folder.root)
        val primary = File(folder.root, "world-v1.json")
        primary.writeText("not a world")
        val loaded = store.load(seed = 17L, nowWallClockMs = 10L)
        assertEquals("new", loaded.source)
        assertNotNull(loaded.warning)
        assertEquals("not a world", primary.readText())
        assertEquals(17L, loaded.state.seed)
    }

    @Test
    fun corruptPrimaryIsPreservedWhenRecoveredStateIsSaved() {
        val store = WorldSnapshotStore(folder.root)
        val initial = WorldFactory.create(seed = 81L, population = 16, nowWallClockMs = 5L)
        store.save(initial)
        val engine = WorldEngine(initial)
        engine.step(60, wallClockMs = 6L)
        store.save(engine.state)
        val primary = File(folder.root, "world-v1.json")
        primary.writeText("{broken")
        val recovered = store.load(seed = 44L, nowWallClockMs = 10L)
        store.save(recovered.state)
        val preserved = folder.root.listFiles().orEmpty().single { it.name.startsWith("world-v1.json.corrupt") }
        assertEquals("{broken", preserved.readText())
        assertEquals(recovered.state, store.load(seed = 99L, nowWallClockMs = 20L).state)
    }

    @Test
    fun tamperingIsDetectedByChecksum() {
        val store = WorldSnapshotStore(folder.root)
        val state = WorldFactory.create(seed = 12L, population = 16)
        store.save(state)
        val primary = File(folder.root, "world-v1.json")
        val original = primary.readText()
        primary.writeText(original.replace("Aurora", "AurorX"))
        val loaded = store.load(seed = 18L, nowWallClockMs = 10L)
        assertEquals("new", loaded.source)
        assertNotNull(loaded.warning)
    }

    @Test
    fun economyWithNoActiveCompaniesRemainsFiniteAndPersistable() {
        val store = WorldSnapshotStore(folder.root)
        val initial = WorldFactory.create(seed = 91L, population = 24)
        val engine = WorldEngine(initial.copy(
            elapsedMinutes = 1_400L,
            agents = initial.agents.map { it.copy(lastUpdatedMinute = 1_400L) },
            companies = initial.companies.map { it.copy(cash = -30_000.0) },
        ))
        engine.step(60)

        assertTrue(engine.state.companies.all { it.bankrupt })
        val metric = engine.state.economyHistory.last()
        assertEquals(0, metric.companies)
        assertEquals(0f, metric.technology)
        assertTrue(metric.technology.isFinite())
        store.save(engine.state)
        assertEquals(engine.state, store.load(seed = 99L, nowWallClockMs = 20L).state)
    }

    @Test
    fun checksumIsStable() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            WorldSnapshotStore.sha256("abc"),
        )
    }
}
