package dev.labforge.app.core.rsi

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local-first harness experiment ledger.
 *
 * This optimizes agent runtime settings only. It does not claim to improve model weights.
 * Candidate promotion is explicit and requires measured runs for both baseline and candidate.
 */
data class HarnessProfile(
    val id: String,
    val label: String,
    val maxSteps: Int,
    val memoryTopK: Int,
    val repeatLimit: Int,
    val description: String,
)

data class AgentRunRecord(
    val id: String,
    val profileId: String,
    val at: Long,
    val success: Boolean,
    val toolCalls: Int,
    val toolFailures: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val elapsedMs: Long,
    val failure: String = "",
)

data class ProfileMetrics(
    val profile: HarnessProfile,
    val samples: Int,
    val successRate: Double,
    val meanTokens: Double,
    val meanLatencyMs: Double,
    val toolFailureRate: Double,
)

data class RsiSnapshot(
    val activeProfileId: String,
    val metrics: List<ProfileMetrics>,
    val recentRuns: List<AgentRunRecord>,
    val recommendedProfileId: String?,
    val recommendation: String,
    val updatedAt: Long,
) {
    val activeProfile: HarnessProfile
        get() = metrics.firstOrNull { it.profile.id == activeProfileId }?.profile
            ?: RsiEngine.DEFAULT_PROFILES.first()
}

class RsiEngine(private val file: File) {
    private var activeProfileId: String = RsiEngine.DEFAULT_PROFILE_ID
    private var runs: List<AgentRunRecord> = emptyList()

    init { load() }

    @Synchronized
    fun activeProfile(): HarnessProfile =
        RsiEngine.DEFAULT_PROFILES.firstOrNull { it.id == activeProfileId }
            ?: RsiEngine.DEFAULT_PROFILES.first()

    @Synchronized
    fun setActiveProfile(profileId: String): Boolean {
        if (RsiEngine.DEFAULT_PROFILES.none { it.id == profileId }) return false
        activeProfileId = profileId
        persist()
        return true
    }

    @Synchronized
    fun recordRun(
        profileId: String,
        success: Boolean,
        toolCalls: Int,
        toolFailures: Int,
        promptTokens: Int,
        completionTokens: Int,
        elapsedMs: Long,
        failure: String = "",
        at: Long = System.currentTimeMillis(),
    ) {
        if (RsiEngine.DEFAULT_PROFILES.none { it.id == profileId }) return
        val record = AgentRunRecord(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            at = at,
            success = success,
            toolCalls = toolCalls.coerceAtLeast(0),
            toolFailures = toolFailures.coerceAtLeast(0),
            promptTokens = promptTokens.coerceAtLeast(0),
            completionTokens = completionTokens.coerceAtLeast(0),
            elapsedMs = elapsedMs.coerceAtLeast(0),
            failure = failure.take(500),
        )
        runs = (runs + record).takeLast(MAX_RUNS)
        persist()
    }

    @Synchronized
    fun snapshot(now: Long = System.currentTimeMillis()): RsiSnapshot {
        val metrics = RsiEngine.DEFAULT_PROFILES.map { profile ->
            val samples = runs.filter { it.profileId == profile.id }
            val count = samples.size
            ProfileMetrics(
                profile = profile,
                samples = count,
                successRate = if (count == 0) 0.0 else samples.count { it.success }.toDouble() / count,
                meanTokens = if (count == 0) 0.0 else samples.map { (it.promptTokens + it.completionTokens).toDouble() }.average(),
                meanLatencyMs = if (count == 0) 0.0 else samples.map { it.elapsedMs.toDouble() }.average(),
                toolFailureRate = samples.sumOf { it.toolFailures }.toDouble() /
                    samples.sumOf { it.toolCalls }.coerceAtLeast(1),
            )
        }
        val baseline = metrics.first { it.profile.id == RsiEngine.DEFAULT_PROFILE_ID }
        val candidates = metrics.filter { it.profile.id != baseline.profile.id && it.samples >= MIN_PROMOTION_SAMPLES }
        val eligible = candidates.filter { candidate ->
            baseline.samples >= MIN_PROMOTION_SAMPLES &&
                candidate.successRate >= baseline.successRate - MAX_SUCCESS_REGRESSION &&
                candidate.meanTokens <= baseline.meanTokens * MAX_TOKEN_MULTIPLIER &&
                candidate.meanLatencyMs <= baseline.meanLatencyMs * MAX_LATENCY_MULTIPLIER &&
                candidate.toolFailureRate <= baseline.toolFailureRate + MAX_TOOL_FAILURE_REGRESSION
        }
        val recommended = eligible.maxWithOrNull(
            compareBy<ProfileMetrics> { it.successRate }
                .thenBy { -it.meanTokens }
                .thenBy { -it.meanLatencyMs },
        )
        val reason = when {
            baseline.samples < MIN_PROMOTION_SAMPLES ->
                "Colete pelo menos " + MIN_PROMOTION_SAMPLES + " execuções no perfil Base antes de comparar."
            recommended == null ->
                "Ainda não há candidato com evidência suficiente e sem regressão relevante."
            else ->
                recommended.profile.label + " atende aos critérios preliminares; a troca continua manual."
        }
        return RsiSnapshot(
            activeProfileId = activeProfileId,
            metrics = metrics,
            recentRuns = runs.takeLast(12).asReversed(),
            recommendedProfileId = recommended?.profile?.id,
            recommendation = reason,
            updatedAt = now,
        )
    }

    /** Explicit promotion only. The caller must request it; no background auto-switch occurs. */
    @Synchronized
    fun promote(profileId: String): Boolean {
        val snap = snapshot()
        if (snap.recommendedProfileId != profileId) return false
        activeProfileId = profileId
        persist()
        return true
    }

    private fun load() {
        runCatching {
            if (!file.isFile) return
            val root = JSONObject(file.readText())
            val selected = root.optString("activeProfileId", RsiEngine.DEFAULT_PROFILE_ID)
            if (RsiEngine.DEFAULT_PROFILES.any { it.id == selected }) activeProfileId = selected
            val array = root.optJSONArray("runs") ?: JSONArray()
            runs = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val profileId = item.optString("profileId")
                if (RsiEngine.DEFAULT_PROFILES.none { it.id == profileId }) return@mapNotNull null
                AgentRunRecord(
                    id = item.optString("id"),
                    profileId = profileId,
                    at = item.optLong("at"),
                    success = item.optBoolean("success"),
                    toolCalls = item.optInt("toolCalls"),
                    toolFailures = item.optInt("toolFailures"),
                    promptTokens = item.optInt("promptTokens"),
                    completionTokens = item.optInt("completionTokens"),
                    elapsedMs = item.optLong("elapsedMs"),
                    failure = item.optString("failure"),
                )
            }.takeLast(MAX_RUNS)
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val root = JSONObject().apply {
            put("version", 1)
            put("activeProfileId", activeProfileId)
            put("runs", JSONArray().apply {
                runs.forEach { run ->
                    put(JSONObject().apply {
                        put("id", run.id)
                        put("profileId", run.profileId)
                        put("at", run.at)
                        put("success", run.success)
                        put("toolCalls", run.toolCalls)
                        put("toolFailures", run.toolFailures)
                        put("promptTokens", run.promptTokens)
                        put("completionTokens", run.completionTokens)
                        put("elapsedMs", run.elapsedMs)
                        put("failure", run.failure)
                    })
                }
            })
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(root.toString(2))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "balanced"
        const val MIN_PROMOTION_SAMPLES = 5
        private const val MAX_RUNS = 500
        private const val MAX_SUCCESS_REGRESSION = 0.02
        private const val MAX_TOKEN_MULTIPLIER = 1.15
        private const val MAX_LATENCY_MULTIPLIER = 1.20
        private const val MAX_TOOL_FAILURE_REGRESSION = 0.05

        val DEFAULT_PROFILES = listOf(
            HarnessProfile(
                id = DEFAULT_PROFILE_ID,
                label = "Base",
                maxSteps = 15,
                memoryTopK = 4,
                repeatLimit = 2,
                description = "Equilíbrio entre planejamento, memória e custo.",
            ),
            HarnessProfile(
                id = "compact",
                label = "Eficiente",
                maxSteps = 9,
                memoryTopK = 3,
                repeatLimit = 2,
                description = "Menos passos e contexto recuperado para tarefas objetivas.",
            ),
            HarnessProfile(
                id = "deliberate",
                label = "Deliberativo",
                maxSteps = 22,
                memoryTopK = 6,
                repeatLimit = 3,
                description = "Mais espaço para tarefas longas e recuperação de experiências.",
            ),
        )
    }
}
