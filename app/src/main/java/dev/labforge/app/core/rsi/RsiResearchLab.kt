package dev.labforge.app.core.rsi

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * On-device RSI workbench. It evolves reusable instructions and agent workflows,
 * never model weights or executable source code. Every candidate is validated and
 * explicitly approved; the last approved artifact can always be restored.
 */
data class RsiSkill(
    val id: String,
    val title: String,
    val body: String,
    val version: Int,
    val approved: Boolean,
    val createdAt: Long,
    val evidenceCount: Int,
    val validation: String = "not_checked",
)

data class RsiReplay(
    val id: String,
    val taskHash: String,
    val profileId: String,
    val success: Boolean,
    val latencyMs: Long,
    val tokens: Int,
    val toolCalls: Int,
    val toolFailures: Int,
    val at: Long,
)

data class RsiExperiment(
    val id: String,
    val name: String,
    val baselineScore: Double,
    val candidateScore: Double,
    val samples: Int,
    val holdoutSamples: Int,
    val status: String,
    val createdAt: Long,
)

data class SkillValidation(val valid: Boolean, val issues: List<String>) {
    val summary: String get() = if (valid) "validado" else issues.joinToString("; ")
}

data class RsiLabSnapshot(
    val skills: List<RsiSkill>,
    val replays: List<RsiReplay>,
    val experiments: List<RsiExperiment>,
    val approvedSkill: RsiSkill?,
    val replaySuccessRate: Double,
    val meanLatencyMs: Double,
    val meanTokens: Double,
)

class RsiResearchLab(private val file: File) {
    private val lock = Any()
    private var skills = emptyList<RsiSkill>()
    private var replays = emptyList<RsiReplay>()
    private var experiments = emptyList<RsiExperiment>()

    init { synchronized(lock) { load() } }

    fun snapshot(): RsiLabSnapshot = synchronized(lock) {
        load()
        val successful = replays.count { it.success }
        RsiLabSnapshot(
            skills = skills.takeLast(30).asReversed(),
            replays = replays.takeLast(40).asReversed(),
            experiments = experiments.takeLast(20).asReversed(),
            approvedSkill = skills.lastOrNull { it.approved },
            replaySuccessRate = if (replays.isEmpty()) 0.0 else successful.toDouble() / replays.size,
            meanLatencyMs = replays.takeLast(100).map { it.latencyMs.toDouble() }.average().takeIf { it.isFinite() } ?: 0.0,
            meanTokens = replays.takeLast(100).map { it.tokens.toDouble() }.average().takeIf { it.isFinite() } ?: 0.0,
        )
    }

    fun recordReplay(
        task: String,
        profileId: String,
        success: Boolean,
        latencyMs: Long,
        tokens: Int,
        toolCalls: Int,
        toolFailures: Int,
        at: Long = System.currentTimeMillis(),
    ) = synchronized(lock) {
        load()
        replays = (replays + RsiReplay(
            id = UUID.randomUUID().toString(),
            taskHash = sha256(task),
            profileId = profileId,
            success = success,
            latencyMs = latencyMs.coerceAtLeast(0),
            tokens = tokens.coerceAtLeast(0),
            toolCalls = toolCalls.coerceAtLeast(0),
            toolFailures = toolFailures.coerceAtLeast(0),
            at = at,
        )).takeLast(MAX_REPLAYS)
        persist()
    }

    /** Stores a text skill candidate derived from user-approved content/evidence. */
    fun proposeSkill(title: String, body: String, evidenceCount: Int): RsiSkill = synchronized(lock) {
        load()
        require(title.isNotBlank()) { "título obrigatório" }
        require(body.isNotBlank()) { "conteúdo obrigatório" }
        val version = (skills.filter { it.title.equals(title.trim(), true) }.maxOfOrNull { it.version } ?: 0) + 1
        val item = RsiSkill(
            id = UUID.randomUUID().toString(),
            title = title.trim().take(100),
            body = body.trim().take(MAX_SKILL_CHARS),
            version = version,
            approved = false,
            createdAt = System.currentTimeMillis(),
            evidenceCount = evidenceCount.coerceAtLeast(0),
        )
        skills = (skills + item).takeLast(MAX_SKILLS)
        persist()
        item
    }

    /** Deterministic structural/security gate; it does not pretend to measure answer quality. */
    fun validateSkill(id: String): SkillValidation = synchronized(lock) {
        load()
        val index = skills.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized SkillValidation(false, listOf("skill não encontrada"))
        val item = skills[index]
        val issues = mutableListOf<String>()
        if (item.title.length < 3) issues += "título muito curto"
        if (item.body.length < 20) issues += "conteúdo precisa ter ao menos 20 caracteres"
        if (item.body.length >= MAX_SKILL_CHARS) issues += "conteúdo atingiu o limite de tamanho"
        if (item.body.contains(Regex("(?i)(ignore|bypass)\\s+(all\\s+)?(safety|security|polic(y|ies))"))) {
            issues += "instrução tenta desativar controles de segurança"
        }
        if (item.body.contains(Regex("(?i)(steal|exfiltrate|dump)\\s+(credentials|tokens|passwords|secrets)"))) {
            issues += "instrução solicita exfiltração de segredos"
        }
        val result = SkillValidation(issues.isEmpty(), issues)
        skills = skills.toMutableList().also { it[index] = item.copy(validation = result.summary) }
        persist()
        result
    }

    /** Explicit user action; only a candidate that passed the structural gate can be activated. */
    fun approveSkill(id: String): Boolean = synchronized(lock) {
        load()
        val validation = validateSkill(id)
        if (!validation.valid) return@synchronized false
        skills = skills.map { it.copy(approved = it.id == id) }
        persist()
        true
    }

    fun rollbackSkill(): RsiSkill? = synchronized(lock) {
        load()
        val approved = skills.filter { it.approved }
        if (approved.size < 2) return@synchronized approved.lastOrNull()
        val previous = approved[approved.lastIndex - 1]
        skills = skills.map { it.copy(approved = it.id == previous.id) }
        persist()
        previous
    }

    /**
     * Records a held-out comparison. Promotion is denied unless the candidate has enough
     * samples and improves the score without being statistically under-sampled.
     */
    fun recordExperiment(
        name: String,
        baselineScore: Double,
        candidateScore: Double,
        samples: Int,
        holdoutSamples: Int,
    ): RsiExperiment = synchronized(lock) {
        load()
        require(name.isNotBlank())
        require(baselineScore.isFinite() && candidateScore.isFinite())
        val accepted = samples >= MIN_EXPERIMENT_SAMPLES &&
            holdoutSamples >= MIN_HOLDOUT_SAMPLES &&
            candidateScore > baselineScore
        val item = RsiExperiment(
            UUID.randomUUID().toString(), name.trim().take(100), baselineScore.coerceIn(0.0, 1.0),
            candidateScore.coerceIn(0.0, 1.0), samples.coerceAtLeast(0), holdoutSamples.coerceAtLeast(0),
            if (accepted) "eligible_for_review" else "rejected_or_insufficient_evidence",
            System.currentTimeMillis(),
        )
        experiments = (experiments + item).takeLast(MAX_EXPERIMENTS)
        persist()
        item
    }

    private fun load() {
        runCatching {
            if (!file.isFile) return
            val root = JSONObject(file.readText())
            skills = root.optJSONArray("skills").toObjects { o ->
                RsiSkill(o.optString("id"), o.optString("title"), o.optString("body"),
                    o.optInt("version", 1), o.optBoolean("approved"), o.optLong("createdAt"),
                    o.optInt("evidenceCount"), o.optString("validation", "not_checked"))
            }
            replays = root.optJSONArray("replays").toObjects { o ->
                RsiReplay(o.optString("id"), o.optString("taskHash"), o.optString("profileId"),
                    o.optBoolean("success"), o.optLong("latencyMs"), o.optInt("tokens"),
                    o.optInt("toolCalls"), o.optInt("toolFailures"), o.optLong("at"))
            }
            experiments = root.optJSONArray("experiments").toObjects { o ->
                RsiExperiment(o.optString("id"), o.optString("name"), o.optDouble("baselineScore"),
                    o.optDouble("candidateScore"), o.optInt("samples"), o.optInt("holdoutSamples"),
                    o.optString("status"), o.optLong("createdAt"))
            }
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val root = JSONObject().apply {
            put("version", 1)
            put("skills", JSONArray().apply { skills.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("title", s.title); put("body", s.body); put("version", s.version)
                put("approved", s.approved); put("createdAt", s.createdAt); put("evidenceCount", s.evidenceCount)
                put("validation", s.validation)
            }) } })
            put("replays", JSONArray().apply { replays.forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("taskHash", r.taskHash); put("profileId", r.profileId)
                put("success", r.success); put("latencyMs", r.latencyMs); put("tokens", r.tokens)
                put("toolCalls", r.toolCalls); put("toolFailures", r.toolFailures); put("at", r.at)
            }) } })
            put("experiments", JSONArray().apply { experiments.forEach { e -> put(JSONObject().apply {
                put("id", e.id); put("name", e.name); put("baselineScore", e.baselineScore)
                put("candidateScore", e.candidateScore); put("samples", e.samples)
                put("holdoutSamples", e.holdoutSamples); put("status", e.status); put("createdAt", e.createdAt)
            }) } })
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private inline fun <T> JSONArray?.toObjects(read: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it)?.let(read) }
    }

    companion object {
        const val MIN_EXPERIMENT_SAMPLES = 5
        const val MIN_HOLDOUT_SAMPLES = 3
        private const val MAX_REPLAYS = 1000
        private const val MAX_SKILLS = 200
        private const val MAX_EXPERIMENTS = 300
        private const val MAX_SKILL_CHARS = 12000
    }
}
