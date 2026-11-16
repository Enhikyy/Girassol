package dev.labforge.app.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldActivityTrackerTest {
    @Test
    fun restoredSnapshotDoesNotReplayStudyToasts() {
        val initial = WorldFactory.create(seed = 42L, population = 8)
        val agent = initial.agents.first()
        val restored = initial.withAgent(agent.withActivity(record(ActionType.LEARN, 120)))
        val tracker = WorldActivityTracker()

        assertTrue(tracker.observe(restored).isEmpty())
        assertTrue(tracker.observe(restored).isEmpty())
    }

    @Test
    fun onlyNewStudyRecordsProduceCompletionOnce() {
        val initial = WorldFactory.create(seed = 9L, population = 8)
        val agent = initial.agents.first()
        val tracker = WorldActivityTracker()
        tracker.observe(initial)

        val work = initial.withAgent(agent.withActivity(record(ActionType.WORK, 180)))
        assertTrue(tracker.observe(work).isEmpty())

        val studyAgent = work.agent(agent.id)!!.withActivity(record(ActionType.LEARN, 120))
        val studied = work.withAgent(studyAgent)
        val completion = tracker.observe(studied).single()
        assertEquals(agent.id, completion.agentId)
        assertEquals(ActionType.LEARN, completion.record.action)
        assertTrue(tracker.observe(studied).isEmpty())
    }

    @Test
    fun boundedHistoryReplacementEmitsEachNewStudyOnce() {
        val initial = WorldFactory.create(seed = 17L, population = 8)
        val agent = initial.agents.first()
        val tracker = WorldActivityTracker()
        tracker.observe(initial)
        val learned = agent.copy(
            activityHistory = listOf(
                record(ActionType.SLEEP, 360, 160),
                record(ActionType.LEARN, 120, 150),
                record(ActionType.LEARN, 90, 140),
            ),
        )
        val state = initial.withAgent(learned)
        assertEquals(2, tracker.observe(state).size)
        assertTrue(tracker.observe(state).isEmpty())
    }

    @Test
    fun cinematicTransitionRequiresStudyToSleepOrWork() {
        val initial = WorldFactory.create(seed = 4L, population = 8)
        val agent = initial.agents.first()
        val study = agent.copy(action = action(ActionType.LEARN, "building_university"))
        val sleep = agent.copy(action = action(ActionType.SLEEP, agent.homeId))
        val work = agent.copy(action = action(ActionType.WORK, "building_office"))

        assertEquals(ActionType.SLEEP, cinematicFocusTransition(study, sleep)?.to)
        assertEquals(ActionType.WORK, cinematicFocusTransition(study, work)?.to)
        assertNull(cinematicFocusTransition(work, sleep))
        assertNull(cinematicFocusTransition(study, agent.copy(action = action(ActionType.EAT, "building_cafe"))))
        assertNull(cinematicFocusTransition(study, initial.agents[1].copy(action = action(ActionType.SLEEP, "home"))))
    }

    private fun record(action: ActionType, duration: Int, minute: Long = 120L) = ActivityRecord(
        simMinute = minute,
        action = action,
        durationMinutes = duration,
        buildingId = "building_university",
        outcome = "registro real",
    )

    private fun action(type: ActionType, buildingId: String) = AgentAction(
        type = type,
        remainingMinutes = 60,
        totalMinutes = 120,
        targetBuildingId = buildingId,
        startedMinute = 10L,
    )

    private fun WorldState.withAgent(updated: AgentState): WorldState =
        copy(agents = agents.map { if (it.id == updated.id) updated else it })
}
