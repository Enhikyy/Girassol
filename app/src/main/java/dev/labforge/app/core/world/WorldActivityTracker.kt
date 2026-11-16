package dev.labforge.app.core.world

/** A completed study session emitted only after a new persisted activity appears. */
data class StudyCompletion(
    val agentId: String,
    val agentName: String,
    val record: ActivityRecord,
)

/** A camera transition requested by a real Library/Study -> Bedroom/Office change. */
data class WorldFocusTransition(
    val agentId: String,
    val from: ActionType,
    val to: ActionType,
    val fromBuildingId: String?,
    val targetBuildingId: String?,
)

/**
 * Tracks activity heads without replaying the snapshot restored at process start.
 * The WorldState remains the source of truth; this observer only detects new records.
 */
class WorldActivityTracker {
    private var primed = false
    private val latestRecords = mutableMapOf<String, ActivityRecord>()
    private val knownAgents = mutableSetOf<String>()

    fun reset() {
        primed = false
        latestRecords.clear()
        knownAgents.clear()
    }

    fun observe(state: WorldState): List<StudyCompletion> {
        val completions = mutableListOf<StudyCompletion>()
        val liveIds = state.agents.mapTo(mutableSetOf()) { it.id }
        latestRecords.keys.retainAll(liveIds)
        knownAgents.retainAll(liveIds)

        state.agents.forEach { agent ->
            val latest = agent.activityHistory.firstOrNull()
            val previous = latestRecords[agent.id]
            val wasKnown = agent.id in knownAgents
            if (primed && wasKnown && latest != null && latest != previous) {
                val previousIndex = agent.activityHistory.indexOfFirst { it == previous }
                val fresh = when {
                    previous == null -> agent.activityHistory
                    previousIndex > 0 -> agent.activityHistory.subList(0, previousIndex)
                    previousIndex == 0 -> emptyList()
                    // A large catch-up can replace the whole bounded history. Those
                    // records are real, so emit them once rather than dropping them.
                    else -> agent.activityHistory
                }
                fresh.filter { it.action == ActionType.LEARN }.forEach { record ->
                    completions += StudyCompletion(agent.id, agent.name, record)
                }
            }
            knownAgents += agent.id
            if (latest != null) latestRecords[agent.id] = latest else latestRecords.remove(agent.id)
        }
        primed = true
        return completions.sortedBy { it.record.simMinute }
    }
}

/** Only the selected resident drives automatic cinematic focus, never background agents. */
fun cinematicFocusTransition(previous: AgentState?, current: AgentState?): WorldFocusTransition? {
    if (previous == null || current == null || previous.id != current.id) return null
    val before = previous.action ?: return null
    val after = current.action ?: return null
    if (before.type != ActionType.LEARN) return null
    if (after.type != ActionType.SLEEP && after.type != ActionType.WORK) return null
    if (before.type == after.type && before.startedMinute == after.startedMinute) return null
    return WorldFocusTransition(
        agentId = current.id,
        from = before.type,
        to = after.type,
        fromBuildingId = before.targetBuildingId,
        targetBuildingId = after.targetBuildingId,
    )
}
