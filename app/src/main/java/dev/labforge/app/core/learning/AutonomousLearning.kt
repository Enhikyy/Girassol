package dev.labforge.app.core.learning

import dev.labforge.app.core.research.ResearchBundle
import dev.labforge.app.core.research.ResearchDoc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * On-device memory consolidation and evidence-backed capability progression.
 * This is a small deterministic Kotlin implementation: no model, Python, server,
 * or extra native runtime is required.
 */
enum class DreamKind { CONSOLIDATION, REPLAY, FAILURE_REVIEW, EXPLORATION }

data class LearningExperience(
    val id: String,
    val createdAt: Long,
    val title: String,
    val content: String,
    val importance: Float,
    val success: Boolean,
    val tags: List<String>,
    val sourceUrl: String = "",
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        put("title", title)
        put("content", content.take(2200))
        put("importance", importance.toDouble())
        put("success", success)
        put("tags", JSONArray(tags.take(12)))
        put("sourceUrl", sourceUrl)
    }

    companion object {
        fun fromJson(o: JSONObject) = LearningExperience(
            id = o.optString("id"),
            createdAt = o.optLong("createdAt"),
            title = o.optString("title"),
            content = o.optString("content"),
            importance = o.optDouble("importance", 0.5).toFloat().coerceIn(0f, 1f),
            success = o.optBoolean("success", true),
            tags = o.optJSONArray("tags").toStringList(),
            sourceUrl = o.optString("sourceUrl"),
        )
    }
}

data class DreamTile(
    val id: String,
    val createdAt: Long,
    val kind: DreamKind,
    val content: String,
    val sourceIds: List<String>,
    val tags: List<String>,
    val novelty: Float,
    val confidence: Float,
    val importance: Float,
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        put("kind", kind.name)
        put("content", content.take(1800))
        put("sourceIds", JSONArray(sourceIds.take(20)))
        put("tags", JSONArray(tags.take(12)))
        put("novelty", novelty.toDouble())
        put("confidence", confidence.toDouble())
        put("importance", importance.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject) = DreamTile(
            id = o.optString("id"),
            createdAt = o.optLong("createdAt"),
            kind = runCatching { DreamKind.valueOf(o.optString("kind")) }.getOrDefault(DreamKind.CONSOLIDATION),
            content = o.optString("content"),
            sourceIds = o.optJSONArray("sourceIds").toStringList(),
            tags = o.optJSONArray("tags").toStringList(),
            novelty = o.optDouble("novelty", 0.0).toFloat().coerceIn(0f, 1f),
            confidence = o.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
            importance = o.optDouble("importance", 0.5).toFloat().coerceIn(0f, 1f),
        )
    }
}

data class SkillProgress(
    val id: String,
    val title: String,
    val group: String,
    val prerequisites: List<String>,
    val mastery: Float = 0f,
    val evidenceCount: Int = 0,
    val successfulEvidence: Int = 0,
    val lastEvidenceAt: Long = 0L,
    val nextReviewAt: Long = 0L,
    val evidenceIds: List<String> = emptyList(),
) {
    val mastered: Boolean get() = mastery >= 0.80f

    fun toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("group", group)
        put("prerequisites", JSONArray(prerequisites))
        put("mastery", mastery.toDouble())
        put("evidenceCount", evidenceCount)
        put("successfulEvidence", successfulEvidence)
        put("lastEvidenceAt", lastEvidenceAt)
        put("nextReviewAt", nextReviewAt)
        put("evidenceIds", JSONArray(evidenceIds.takeLast(40)))
    }

    companion object {
        fun fromJson(o: JSONObject) = SkillProgress(
            id = o.optString("id"),
            title = o.optString("title"),
            group = o.optString("group"),
            prerequisites = o.optJSONArray("prerequisites").toStringList(),
            mastery = o.optDouble("mastery", 0.0).toFloat().coerceIn(0f, 1f),
            evidenceCount = o.optInt("evidenceCount").coerceAtLeast(0),
            successfulEvidence = o.optInt("successfulEvidence").coerceAtLeast(0),
            lastEvidenceAt = o.optLong("lastEvidenceAt"),
            nextReviewAt = o.optLong("nextReviewAt"),
            evidenceIds = o.optJSONArray("evidenceIds").toStringList().takeLast(40),
        )
    }
}

data class AutonomousLearningState(
    val tiles: List<DreamTile> = emptyList(),
    val skills: List<SkillProgress> = SkillTreeEngine.initialSkills(),
    val lastCycleAt: Long = 0L,
    val processedCycles: List<String> = emptyList(),
    val failedCycles: Int = 0,
)

data class CycleEvidence(
    val cycleId: String,
    val documents: Int,
    val sources: Int,
    val synthesized: Boolean,
    val curriculumTopics: Int,
    val datasetExamples: Int,
    val downloads: Int,
    val loraTrainingSucceeded: Boolean = false,
    val trainingFailure: String? = null,
)

object DreamCycleEngine {
    private const val MAX_TILES = 80
    private const val CLUSTER_THRESHOLD = 0.22f

    fun experiences(bundle: ResearchBundle, now: Long = System.currentTimeMillis()): List<LearningExperience> =
        bundle.docs.take(60).mapIndexed { index, doc ->
            LearningExperience(
                id = "${bundle.id}:doc:$index",
                createdAt = now,
                title = doc.title.take(180),
                content = doc.snippet.take(1800),
                importance = ((doc.score - 0.5) / 2.5).toFloat().coerceIn(0.25f, 0.95f),
                success = true,
                tags = tags("${doc.title} ${doc.source} ${doc.snippet}"),
                sourceUrl = doc.url,
            )
        }

    fun failure(cycleId: String, message: String, now: Long = System.currentTimeMillis()) =
        LearningExperience(
            id = "$cycleId:failure",
            createdAt = now,
            title = "Falha a revisar",
            content = message.take(1200),
            importance = 0.95f,
            success = false,
            tags = listOf("falha", "diagnostico", "revisao"),
        )

    fun process(
        previous: List<DreamTile>,
        experiences: List<LearningExperience>,
        now: Long = System.currentTimeMillis(),
    ): List<DreamTile> {
        val knownIds = previous.flatMap { it.sourceIds }.toHashSet()
        val fresh = experiences.filter { it.id !in knownIds }.distinctBy { it.id }
        if (fresh.isEmpty()) return previous.takeLast(MAX_TILES)

        val result = ArrayList<DreamTile>()
        val failures = fresh.filter { !it.success }
        failures.forEach { exp ->
            result += DreamTile(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                kind = DreamKind.FAILURE_REVIEW,
                content = "Revisar causa e prevenção: ${exp.content}",
                sourceIds = listOf(exp.id),
                tags = (exp.tags + "correcao").distinct().take(12),
                novelty = 0.25f,
                confidence = 0.75f,
                importance = exp.importance,
            )
        }

        val successful = fresh.filter { it.success }
        val clusters = ArrayList<MutableList<LearningExperience>>()
        successful.forEach { exp ->
            val target = clusters.firstOrNull { cluster ->
                cluster.any { similarity(exp, it) >= CLUSTER_THRESHOLD }
            }
            if (target == null) clusters += mutableListOf(exp) else target += exp
        }

        clusters.forEach { cluster ->
            val sorted = cluster.sortedByDescending { it.importance }
            val representative = sorted.first()
            val tags = sorted.flatMap { it.tags }.distinct().take(12)
            val isCluster = sorted.size > 1
            val content = if (isCluster) {
                buildString {
                    append("Memória consolidada: ")
                    append(sorted.size).append(" fontes relacionadas sobre ")
                    append(tags.take(5).joinToString(", ")).append("\n")
                    sorted.take(5).forEach { item ->
                        append("• ").append(item.title).append(": ")
                            .append(item.content.take(240)).append("\n")
                    }
                }
            } else {
                "${representative.title}: ${representative.content}"
            }
            result += DreamTile(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                kind = if (isCluster) DreamKind.CONSOLIDATION else DreamKind.REPLAY,
                content = content.take(1800),
                sourceIds = sorted.map { it.id }.take(20),
                tags = tags,
                novelty = if (isCluster) 0.08f else 0.12f,
                confidence = (sorted.map { it.importance }.average().toFloat() + if (isCluster) 0.05f else 0f).coerceIn(0f, 1f),
                importance = representative.importance,
            )
        }

        // Cross-domain connections are explicitly labelled as hypotheses, never facts.
        val distinct = successful.sortedByDescending { it.importance }.take(12)
        outer@ for (i in distinct.indices) {
            for (j in i + 1 until distinct.size) {
                val a = distinct[i]
                val b = distinct[j]
                val overlap = similarity(a, b)
                if (overlap < 0.08f && a.tags.firstOrNull() != b.tags.firstOrNull()) {
                    result += DreamTile(
                        id = UUID.randomUUID().toString(),
                        createdAt = now,
                        kind = DreamKind.EXPLORATION,
                        content = "Hipótese para pesquisar, ainda não verificada: \"${a.title}\" pode ter uma conexão útil com \"${b.title}\". Investigar antes de incorporar como conhecimento.",
                        sourceIds = listOf(a.id, b.id),
                        tags = (a.tags + b.tags).distinct().take(12),
                        novelty = 0.65f,
                        confidence = 0.35f,
                        importance = max(a.importance, b.importance) * 0.65f,
                    )
                    break@outer
                }
            }
        }
        return (previous + result).distinctBy { it.id }.takeLast(MAX_TILES)
    }

    fun recall(tiles: List<DreamTile>, query: String, limit: Int = 5): List<DreamTile> {
        val q = tokens(query)
        if (q.isEmpty()) return tiles.takeLast(limit)
        return tiles.map { tile ->
            val words = tokens(tile.content + " " + tile.tags.joinToString(" "))
            val overlap = if (words.isEmpty()) 0f else q.count { it in words }.toFloat() / q.size
            tile to (overlap * 0.75f + tile.confidence * 0.15f + tile.importance * 0.1f)
        }.filter { it.second > 0.03f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun similarity(a: LearningExperience, b: LearningExperience): Float {
        val wa = tokens(a.title + " " + a.content)
        val wb = tokens(b.title + " " + b.content)
        val union = wa union wb
        val wordScore = if (union.isEmpty()) 0f else (wa intersect wb).size.toFloat() / union.size
        val ta = a.tags.toSet()
        val tb = b.tags.toSet()
        val tagUnion = ta union tb
        val tagScore = if (tagUnion.isEmpty()) 0f else (ta intersect tb).size.toFloat() / tagUnion.size
        return wordScore * 0.65f + tagScore * 0.35f
    }

    private fun tags(value: String): List<String> = tokens(value).take(10)

    private fun tokens(value: String): Set<String> =
        value.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 && it !in STOP_WORDS }
            .toSet()

    private val STOP_WORDS = setOf(
        "para", "com", "como", "essa", "esse", "isso", "sobre", "from", "this",
        "that", "with", "when", "what", "into", "from", "using", "their", "have",
        "will", "your", "also", "mais", "este", "esta", "entre", "fontes",
    )
}

object SkillTreeEngine {
    fun initialSkills() = listOf(
        SkillProgress("source-discovery", "Descoberta de fontes", "Pesquisa", emptyList()),
        SkillProgress("source-validation", "Validação de evidências", "Pesquisa", listOf("source-discovery")),
        SkillProgress("evidence-synthesis", "Síntese fundamentada", "Pesquisa", listOf("source-validation")),
        SkillProgress("curriculum-design", "Currículo progressivo", "Aprendizado", listOf("evidence-synthesis")),
        SkillProgress("memory-consolidation", "Memória e consolidação", "Aprendizado", listOf("evidence-synthesis")),
        SkillProgress("dataset-engineering", "Engenharia de datasets", "Treinamento", listOf("curriculum-design")),
        SkillProgress("native-lora", "Treinamento LoRA nativo", "Treinamento", listOf("dataset-engineering")),
        SkillProgress("training-evaluation", "Avaliação do treino", "Treinamento", listOf("native-lora")),
        SkillProgress("failure-analysis", "Análise de falhas", "Autonomia", listOf("source-validation")),
        SkillProgress("autonomous-orchestration", "Orquestração autônoma", "Autonomia", listOf("dataset-engineering", "memory-consolidation")),
    )

    fun recordCycle(
        current: List<SkillProgress>,
        evidence: CycleEvidence,
        now: Long = System.currentTimeMillis(),
    ): List<SkillProgress> {
        val updates = linkedMapOf<String, Float>()
        updates["source-discovery"] = when {
            evidence.documents >= 10 -> 0.95f
            evidence.documents >= 5 -> 0.8f
            evidence.documents >= 2 -> 0.65f
            else -> 0.25f
        }
        updates["source-validation"] = if (evidence.sources >= 3 && evidence.documents >= 4) 0.82f else 0.4f
        updates["evidence-synthesis"] = if (evidence.synthesized) 0.85f else 0.2f
        updates["curriculum-design"] = when {
            evidence.curriculumTopics >= 6 -> 0.9f
            evidence.curriculumTopics >= 3 -> 0.72f
            else -> 0.2f
        }
        updates["memory-consolidation"] = 0.8f
        updates["dataset-engineering"] = when {
            evidence.datasetExamples >= 12 -> 0.9f
            evidence.datasetExamples >= 5 -> 0.75f
            evidence.datasetExamples >= 1 -> 0.6f
            else -> 0.15f
        }
        if (evidence.loraTrainingSucceeded) {
            updates["native-lora"] = 0.9f
            updates["training-evaluation"] = 0.82f
        }
        if (!evidence.trainingFailure.isNullOrBlank()) updates["failure-analysis"] = 0.45f
        updates["autonomous-orchestration"] = if (
            evidence.documents > 0 && evidence.synthesized && evidence.curriculumTopics > 0 && evidence.datasetExamples > 0
        ) 0.9f else 0.3f

        return current.map { skill ->
            val score = updates[skill.id] ?: return@map skill
            if (evidence.cycleId in skill.evidenceIds) return@map skill
            val count = skill.evidenceCount + 1
            val mastery = if (skill.evidenceCount == 0) score else
                (skill.mastery * 0.72f + score * 0.28f).coerceIn(0f, 1f)
            val intervalDays = when {
                mastery < 0.4f -> 1L
                mastery < 0.6f -> 3L
                mastery < 0.8f -> 7L
                else -> 21L
            }
            skill.copy(
                mastery = mastery,
                evidenceCount = count,
                successfulEvidence = skill.successfulEvidence + if (score >= 0.6f) 1 else 0,
                lastEvidenceAt = now,
                nextReviewAt = now + intervalDays * 24L * 60L * 60L * 1000L,
                evidenceIds = (skill.evidenceIds + evidence.cycleId).takeLast(40),
            )
        }
    }

    fun dueReviews(skills: List<SkillProgress>, now: Long = System.currentTimeMillis()): List<SkillProgress> =
        skills.filter { it.nextReviewAt > 0L && it.nextReviewAt <= now }
            .sortedWith(compareBy<SkillProgress> { it.mastery }.thenBy { it.nextReviewAt })
}

class AutonomousLearningStore(private val file: File) {
    @Synchronized
    fun load(): AutonomousLearningState = runCatching {
        val root = JSONObject(file.readText())
        val tileArray = root.optJSONArray("tiles") ?: JSONArray()
        val skillArray = root.optJSONArray("skills") ?: JSONArray()
        AutonomousLearningState(
            tiles = (0 until tileArray.length()).mapNotNull { tileArray.optJSONObject(it)?.let(DreamTile::fromJson) }.takeLast(80),
            skills = if (skillArray.length() == 0) SkillTreeEngine.initialSkills() else
                (0 until skillArray.length()).mapNotNull { skillArray.optJSONObject(it)?.let(SkillProgress::fromJson) },
            lastCycleAt = root.optLong("lastCycleAt"),
            processedCycles = root.optJSONArray("processedCycles").toStringList().takeLast(40),
            failedCycles = root.optInt("failedCycles").coerceAtLeast(0),
        )
    }.getOrDefault(AutonomousLearningState())

    @Synchronized
    fun save(state: AutonomousLearningState) {
        file.parentFile?.mkdirs()
        val json = JSONObject().apply {
            put("tiles", JSONArray().apply { state.tiles.takeLast(80).forEach { put(it.toJson()) } })
            put("skills", JSONArray().apply { state.skills.forEach { put(it.toJson()) } })
            put("lastCycleAt", state.lastCycleAt)
            put("processedCycles", JSONArray(state.processedCycles.takeLast(40)))
            put("failedCycles", state.failedCycles)
        }.toString(2)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (file.exists()) file.delete()
        check(tmp.renameTo(file)) { "não foi possível persistir as memórias autônomas" }
    }

    fun recordCycle(
        bundle: ResearchBundle,
        evidence: CycleEvidence,
        failureMessage: String? = null,
        now: Long = System.currentTimeMillis(),
    ): AutonomousLearningState {
        val current = load()
        if (evidence.cycleId in current.processedCycles) return current
        var experiences = DreamCycleEngine.experiences(bundle, now)
        if (!failureMessage.isNullOrBlank()) {
            experiences = experiences + DreamCycleEngine.failure(evidence.cycleId, failureMessage, now)
        }
        val tiles = DreamCycleEngine.process(current.tiles, experiences, now)
        val skills = SkillTreeEngine.recordCycle(current.skills, evidence, now)
        val updated = current.copy(
            tiles = tiles,
            skills = skills,
            lastCycleAt = now,
            processedCycles = (current.processedCycles + evidence.cycleId).takeLast(40),
            failedCycles = current.failedCycles + if (failureMessage.isNullOrBlank()) 0 else 1,
        )
        save(updated)
        return updated
    }

    fun recall(query: String, limit: Int = 5): List<DreamTile> =
        DreamCycleEngine.recall(load().tiles, query, limit)
}

private fun JSONArray?.toStringList(): List<String> = this?.let { a ->
    (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() && it != "null" } }
}.orEmpty()
