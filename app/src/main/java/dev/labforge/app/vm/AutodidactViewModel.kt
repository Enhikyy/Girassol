package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.AppSelection
import dev.labforge.app.core.Perf
import dev.labforge.app.core.job.JobEngine
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.model.AssetKind
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.core.research.FreeResearchApis
import dev.labforge.app.core.research.ResearchBundle
import dev.labforge.app.core.research.ResearchDoc
import dev.labforge.app.core.research.ResearchEngine
import dev.labforge.app.core.research.ResearchDownloader
import dev.labforge.app.core.research.ResearchRepository
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.settings.researchKeys
import dev.labforge.app.core.train.AutoTuner
import dev.labforge.app.core.train.ChatFormat
import dev.labforge.app.core.train.DatasetBuilder
import dev.labforge.app.core.train.DatasetStats
import dev.labforge.app.core.train.TrainingPreset
import dev.labforge.app.service.MemoryGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class StudyStage(val label: String, val icon: String) {
    IDLE("Pronto", "○"),
    RESEARCH("Pesquisando", "⌕"),
    DOWNLOAD("Baixando", "↓"),
    SYNTHESIS("Sintetizando", "✦"),
    CURRICULUM("Currículo", "≡"),
    DATASET("Dataset", "▤"),
    TUNING("Ajustando", "⚙"),
    DONE("Concluído", "✓"),
    ERROR("Falhou", "!"),
}

data class StudyTopic(val title: String, val done: Boolean = false, val examples: Int = 0)

data class StudyCycleMetric(
    val at: Long,
    val documents: Int,
    val downloads: Int,
    val tokens: Long,
    val examples: Int,
)

data class StudyProject(
    val theme: String,
    val topics: List<StudyTopic>,
    val jsonl: File?,
    val txt: File?,
    val examples: Int,
    val createdAt: Long,
    val bundleId: String?,
    val preset: TrainingPreset?,
    val sources: List<ResearchDoc>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme)
        put("createdAt", createdAt)
        put("examples", examples)
        put("bundleId", bundleId ?: "")
        put("jsonl", jsonl?.absolutePath ?: "")
        put("txt", txt?.absolutePath ?: "")
        put("topics", JSONArray().apply {
            topics.forEach { put(JSONObject().put("title", it.title).put("examples", it.examples)) }
        })
        put("preset", preset?.let {
            JSONObject().put("rank", it.rank).put("epochs", it.epochs).put("lr", it.lr.toDouble())
                .put("ctx", it.ctx).put("targets", it.targets).put("notes", it.notes)
                .put("tokens", it.estimatedTokens).put("minutes", it.estimatedMinutes)
        } ?: JSONObject.NULL)
        put("sources", JSONArray().apply { sources.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): StudyProject {
            val topics = o.optJSONArray("topics") ?: JSONArray()
            val sources = o.optJSONArray("sources") ?: JSONArray()
            val p = o.optJSONObject("preset")
            return StudyProject(
                theme = o.optString("theme"),
                topics = (0 until topics.length()).map { i ->
                    val t = topics.optJSONObject(i)
                    StudyTopic(t?.optString("title").orEmpty(), true, t?.optInt("examples") ?: 0)
                },
                jsonl = o.optString("jsonl").takeIf { it.isNotBlank() }?.let(::File),
                txt = o.optString("txt").takeIf { it.isNotBlank() }?.let(::File),
                examples = o.optInt("examples"),
                createdAt = o.optLong("createdAt"),
                bundleId = o.optString("bundleId").takeIf { it.isNotBlank() },
                preset = p?.let {
                    TrainingPreset(
                        rank = it.optInt("rank", 16),
                        epochs = it.optInt("epochs", 3),
                        lr = it.optDouble("lr", 1e-4).toFloat(),
                        ctx = it.optInt("ctx", 512),
                        targets = it.optString("targets", "attn"),
                        notes = it.optString("notes"),
                        estimatedTokens = it.optLong("tokens"),
                        estimatedMinutes = it.optInt("minutes"),
                    )
                },
                sources = (0 until sources.length()).mapNotNull { sources.optJSONObject(it)?.let(ResearchDoc::fromJson) },
            )
        }
    }
}

/**
 * Autonomous specialization: research free web APIs → synthesize → curriculum →
 * grounded dataset → hyperparameter prescription → (optionally) LoRA training.
 * Works without an LLM using extractive fallbacks.
 */
class AutodidactViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ModelStore(app)
    private val settingsRepo = SettingsRepository(app)
    private val repo = ResearchRepository(store.researchDir)
    private val downloader = ResearchDownloader(File(store.researchDir, "downloads"))
    private val metricsFile = File(store.researchDir, "autodidact-cycles.json")

    var theme by mutableStateOf("")
    var topicCount by mutableStateOf(6)
    var perTopic by mutableStateOf(4)
    var running by mutableStateOf(false)
        private set
    var stage by mutableStateOf(StudyStage.IDLE)
        private set
    var status by mutableStateOf("pronto")
        private set
    var progress by mutableStateOf(0f)
        private set
    var log by mutableStateOf("")
        private set
    var live by mutableStateOf("")
        private set
    var topics by mutableStateOf<List<StudyTopic>>(emptyList())
        private set
    var bundle by mutableStateOf<ResearchBundle?>(null)
        private set
    var preset by mutableStateOf<TrainingPreset?>(null)
        private set
    var project by mutableStateOf<StudyProject?>(null)
        private set
    var history by mutableStateOf<List<ResearchBundle>>(emptyList())
        private set
    var llmReady by mutableStateOf(false)
        private set
    var datasetStats by mutableStateOf<DatasetStats?>(null)
        private set
    var autoTrain by mutableStateOf(false)
    var autopilotMode by mutableStateOf(false)
    var autopilotIntervalMinutes by mutableStateOf(30)
    var autopilotRunning by mutableStateOf(false)
        private set
    var downloadedCount by mutableStateOf(0)
        private set
    var cycleHistory by mutableStateOf<List<StudyCycleMetric>>(emptyList())
        private set

    private var job: Job? = null
    private var autopilotJob: Job? = null
    private var runId = 0

    init {
        viewModelScope.launch {
            bundle = withContext(Dispatchers.IO) { repo.latest() }
            history = withContext(Dispatchers.IO) { repo.list().mapNotNull { repo.load(it) }.take(8) }
            cycleHistory = withContext(Dispatchers.IO) { loadCycleMetrics() }
            bundle?.let { b ->
                if (theme.isBlank()) theme = b.theme
                AppSelection.researchTheme = b.theme
                AppSelection.researchBundleId = b.id
            }
            project = withContext(Dispatchers.IO) { loadLatestProject() }
            project?.let {
                topics = it.topics
                preset = it.preset
                datasetStats = null
            }
            llmReady = runCatching {
                val cfg = settingsRepo.settings.first()
                cfg.apiKey.isNotBlank() || !cfg.endpoint.contains("openai.com")
            }.getOrDefault(false)
        }
    }

    fun start() {
        if (running || theme.isBlank()) return
        val myRun = ++runId
        running = true
        stage = StudyStage.RESEARCH
        progress = 0.02f
        status = "pesquisando fontes gratuitas…"
        log = ""
        live = ""
        topics = emptyList()
        bundle = null
        project = null
        preset = null
        datasetStats = null
        downloadedCount = 0
        AppSelection.datasetPath = null
        AppSelection.preset = null
        val themeValue = theme.trim()

        job = viewModelScope.launch {
            try {
                val cfg = settingsRepo.settings.first()
                val llm = if (cfg.apiKey.isNotBlank() || !cfg.endpoint.contains("openai.com")) {
                    LlmClient(cfg.endpoint, cfg.apiKey, cfg.model, cfg.temperature)
                } else null
                llmReady = llm != null

                // 1. research
                val researched = ResearchEngine(repo, cfg.researchKeys()).research(themeValue) { st ->
                    if (myRun == runId) {
                        status = "${st.label}: ${st.detail}"
                        append("${st.label} · ${st.detail}")
                    }
                }
                if (researched.docs.isEmpty()) error("nenhuma fonte encontrada para o tema")
                progress = 0.30f
                append("${researched.docs.size} fontes consolidadas de ${researched.sourceCounts.size} provedores")

                // Download and ingest readable source content before synthesis and dataset generation.
                stage = StudyStage.DOWNLOAD
                status = "baixando e incorporando conteúdo das fontes…"
                progress = 0.34f
                val enrichment = downloader.enrich(researched.docs, maxDocuments = 8)
                downloadedCount = enrichment.downloadedDocuments
                val found = researched.copy(docs = enrichment.documents)
                withContext(Dispatchers.IO) { repo.save(found) }
                bundle = found
                append("${enrichment.downloadedDocuments} conteúdos incorporados · ${enrichment.bytesWritten} bytes lidos")

                // 2. synthesis
                stage = StudyStage.SYNTHESIS
                status = if (llm != null) "sintetizando com o modelo…" else "sintetizando extrativamente (sem LLM)"
                progress = 0.38f
                val summary = if (llm != null) {
                    try {
                        synthesize(llm, found)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        append("LLM falhou (${error.message}); usando resumo extrativo")
                        found.autoSummary
                    }
                } else found.autoSummary
                append("Resumo: ${summary.take(280)}")

                // 3. curriculum
                stage = StudyStage.CURRICULUM
                progress = 0.46f
                var curriculum = if (llm != null) {
                    try {
                        llmCurriculum(llm, themeValue, summary, found)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        append("currículo via LLM falhou; derivando das fontes")
                        emptyList()
                    }
                } else emptyList()
                // An LLM may return prose instead of JSON; never abort a completed
                // study just because its curriculum was empty.
                if (curriculum.isEmpty()) curriculum = extractiveCurriculum(themeValue, found, topicCount)
                topics = curriculum.take(topicCount).map { StudyTopic(it) }
                if (topics.isEmpty()) error("não foi possível montar o currículo")
                append("Currículo: ${topics.size} tópicos")

                // 4. dataset
                stage = StudyStage.DATASET
                progress = 0.55f
                val slug = ResearchRepository.slug(themeValue)
                val jsonl = File(store.datasetsDir, "auto-$slug-${System.currentTimeMillis() / 1000}.jsonl")
                val sb = StringBuilder()
                var total = 0
                topics.forEachIndexed { index, topic ->
                    status = "gerando material: ${index + 1}/${topics.size} · ${topic.title}"
                    val retrieved = withContext(Dispatchers.IO) {
                        repo.retrieve(found, "${topic.title} $themeValue", 4)
                    }
                    val pairs = if (llm != null) {
                        try {
                            generateWithLlm(llm, themeValue, topic.title, retrieved, perTopic)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            append("Q&A via LLM falhou em '${topic.title}'; usando extrativo")
                            extractivePairs(topic.title, retrieved, perTopic)
                        }
                    } else {
                        extractivePairs(topic.title, retrieved, perTopic)
                    }
                    pairs.forEach { (q, a) ->
                        sb.append(
                            JSONObject().apply {
                                put("messages", JSONArray().apply {
                                    put(JSONObject().put("role", "user").put("content", q))
                                    put(JSONObject().put("role", "assistant").put("content", a))
                                })
                                put("topic", topic.title)
                                put("theme", themeValue)
                                put("source", "girassol-autodidact")
                            }.toString(),
                        ).append('\n')
                    }
                    total += pairs.size
                    topics = topics.mapIndexed { i, t -> if (i == index) t.copy(done = true, examples = pairs.size) else t }
                    progress = 0.55f + (index + 1f) / topics.size * 0.28f
                }
                withContext(Dispatchers.IO) { jsonl.writeText(sb.toString()) }
                append("Dataset bruto: ${jsonl.name} ($total pares)")

                status = "convertendo para texto de treino…"
                val (txt, stats) = withContext(Dispatchers.IO) {
                    val out = store.uniqueFile(AssetKind.DATASET, "auto-$slug.txt")
                    DatasetBuilder.build(listOf(jsonl), ChatFormat.CHATML, out)
                }
                datasetStats = stats
                progress = 0.9f

                // 5. tuning
                stage = StudyStage.TUNING
                status = "redefinindo parâmetros de treino…"
                val tokens = if (stats.approxTokens > 0) stats.approxTokens else (txt.length() / 3.6).toLong()
                val baseParams = withContext(Dispatchers.IO) {
                    AppSelection.modelPath?.let { p ->
                        runCatching { store.readModel(File(p)).meta?.parameterCount }.getOrNull()
                    }
                } ?: 1_000_000_000L
                val rec = AutoTuner.recommend(
                    tokens = tokens,
                    docs = found.docs.size,
                    ramGb = MemoryGuard.totalGb(getApplication()),
                    cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                    params = baseParams,
                    technical = true,
                )
                preset = rec
                AppSelection.preset = rec
                AppSelection.datasetPath = txt.absolutePath
                AppSelection.researchTheme = themeValue
                AppSelection.researchBundleId = found.id
                append("Parâmetros: rank ${rec.rank} · epochs ${rec.epochs} · lr ${rec.lr} · ctx ${rec.ctx} · ${rec.targets}")
                append(rec.notes)

                val proj = StudyProject(
                    theme = themeValue,
                    topics = topics,
                    jsonl = jsonl,
                    txt = txt,
                    examples = total,
                    createdAt = System.currentTimeMillis(),
                    bundleId = found.id,
                    preset = rec,
                    sources = found.docs,
                )
                project = proj
                val completedCycles = withContext(Dispatchers.IO) {
                    saveProject(proj)
                    appendCycleMetric(
                        StudyCycleMetric(
                            at = System.currentTimeMillis(),
                            documents = found.docs.size,
                            downloads = enrichment.downloadedDocuments,
                            tokens = tokens,
                            examples = total,
                        ),
                    )
                }
                cycleHistory = completedCycles
                history = withContext(Dispatchers.IO) { repo.list().mapNotNull { repo.load(it) }.take(8) }

                stage = StudyStage.DONE
                progress = 1f
                status = "concluído · ${total} pares · ~$tokens tokens"
                append("Pronto. Dataset e preset prontos no Laboratório.")

                if (autoTrain) {
                    val model = AppSelection.modelPath?.let(::File)?.takeIf { it.isFile }
                    val ds = proj.txt
                    val modelAllowed = model?.let { m ->
                        val meta = runCatching { store.readModel(m).meta }.getOrNull()
                        val label = meta?.quantLabel ?: ""
                        label.contains("F32") || label.contains("F16") || label.contains("BF16") ||
                            meta?.quantMix?.keys?.all { it.label in setOf("F32", "F16", "BF16") } == true
                    } ?: false
                    if (model != null && ds != null && !modelAllowed) {
                        append("Auto-treino ignorado: o modelo precisa estar em F32/F16 para o LoRA nativo.")
                    } else if (model != null && ds != null) {
                        val out = File(store.outputsDir, "${model.nameWithoutExtension}-auto-lora-r${rec.rank}.gguf")
                        append("Auto-treino: iniciando LoRA em ${model.name}")
                        JobEngine.startLora(
                            context = getApplication(),
                            model = model,
                            output = out,
                            dataset = ds,
                            epochs = rec.epochs,
                            lr = rec.lr,
                            nCtx = rec.ctx,
                            threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                            rank = rec.rank,
                            alpha = rec.rank.toFloat(),
                            targets = rec.targets,
                        )
                    } else {
                        append("Auto-treino ativo, mas nenhum modelo base foi selecionado.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (myRun == runId) {
                    stage = StudyStage.ERROR
                    status = "falhou: ${t.message}"
                    append("erro: ${t.message}")
                }
            } finally {
                if (myRun == runId) {
                    running = false
                    live = ""
                }
            }
        }
    }

    // ------------------------------------------------------------------- LLM

    private suspend fun synthesize(llm: LlmClient, b: ResearchBundle): String {
        val corpus = b.docs.take(8).joinToString("\n\n") {
            "[${it.source}] ${it.title}\n${it.snippet.take(500)}\n${it.url}"
        }
        val reply = llm.chat(
            JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "Você é um pesquisador técnico. Sintetize apenas o que está nas fontes, sem inventar. Responda em português."))
                put(JSONObject().put("role", "user").put("content", "Tema: ${b.theme}\n\nFontes:\n$corpus\n\nFaça uma síntese em 5 tópicos objetivos, citando a fonte entre parênteses."))
            },
            null,
        )
        return reply.content.take(2500)
    }

    private suspend fun llmCurriculum(llm: LlmClient, theme: String, summary: String, b: ResearchBundle): List<String> {
        val reply = llm.chat(
            JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "Você projeta currículos técnicos progressivos e específicos. Responda APENAS JSON."))
                put(JSONObject().put("role", "user").put("content",
                    "Tema: $theme\nSíntese:\n$summary\n\nCrie $topicCount tópicos progressivos, do fundamento à aplicação avançada, baseados nas fontes. " +
                        "Responda apenas um array JSON de strings."))
            },
            null,
        )
        val raw = reply.content.trim()
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start >= 0 && end > start) {
            runCatching {
                val arr = JSONArray(raw.substring(start, end + 1))
                return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.take(topicCount)
            }
        }
        return emptyList()
    }

    private suspend fun generateWithLlm(
        llm: LlmClient,
        theme: String,
        topic: String,
        docs: List<ResearchDoc>,
        count: Int,
    ): List<Pair<String, String>> {
        val corpus = docs.joinToString("\n\n") {
            "[${it.source}] ${it.title}\n${it.snippet.take(700)}\n${it.url}"
        }
        val reply = llm.chat(
            JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "Você escreve material de estudo técnico estritamente baseado nas fontes fornecidas. " +
                        "Se a fonte não cobrir algo, diga que não há base. Nunca invente fatos. Responda APENAS JSON."))
                put(JSONObject().put("role", "user").put("content",
                    "Tema: $theme\nTópico: $topic\n\nFontes:\n$corpus\n\n" +
                        "Crie $count pares {\"q\":\"...\",\"a\":\"...\"}. Perguntas concretas; respostas de 2 a 6 frases, " +
                        "citando a fonte pelo nome. Responda apenas um array JSON."))
            },
            null,
        )
        val raw = reply.content.trim()
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        val pairs = ArrayList<Pair<String, String>>()
        if (start >= 0 && end > start) {
            runCatching {
                val arr = JSONArray(raw.substring(start, end + 1))
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val q = o.optString("q").ifBlank { o.optString("question") }
                    val a = o.optString("a").ifBlank { o.optString("answer") }
                    if (q.isNotBlank() && a.isNotBlank()) pairs += q to a
                }
            }
        }
        return pairs.ifEmpty { extractivePairs(topic, docs, count) }
    }

    // ------------------------------------------------------------- extractive

    private fun extractiveCurriculum(theme: String, b: ResearchBundle, n: Int): List<String> {
        val titles = b.docs.take(8).map { cleanTitle(it.title) }.filter { it.length > 8 }
        val list = LinkedHashSet<String>()
        list += "Fundamentos de $theme"
        titles.take((n - 3).coerceAtLeast(0)).forEach { list += it }
        list += "Aplicações práticas de $theme"
        list += "Erros comuns e boas práticas em $theme"
        return list.take(n)
    }

    private fun extractivePairs(topic: String, docs: List<ResearchDoc>, count: Int): List<Pair<String, String>> {
        val d = docs.firstOrNull() ?: return emptyList()
        val sentences = d.snippet.split(Regex("(?<=[.!?])\\s+")).filter { it.length > 30 }
        val pairs = ArrayList<Pair<String, String>>()
        pairs += "O que é $topic?" to d.snippet.take(900)
        if (sentences.isNotEmpty()) {
            pairs += "Quais são os pontos principais de $topic?" to sentences.take(4).joinToString(" ")
        }
        pairs += "Como aplicar $topic na prática?" to
            "Com base em ${d.title} (${d.source}): ${d.snippet.take(700)}"
        if (docs.size > 1) {
            pairs += "Quais referências estudar sobre $topic?" to
                docs.take(4).joinToString("\n") { "• ${it.title} — ${it.url}" }
        }
        return pairs.take(count)
    }

    private fun cleanTitle(t: String): String = t
        .replace(Regex("\\s*[-–|:]\\s*arXiv.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)

    // -------------------------------------------------------------- persistence

    private fun saveProject(p: StudyProject) {
        File(store.researchDir, "project-${ResearchRepository.slug(p.theme)}-${p.createdAt}.json")
            .writeText(p.toJson().toString(2))
    }

    private fun loadLatestProject(): StudyProject? {
        val file = store.researchDir.listFiles { f ->
            f.isFile && f.name.startsWith("project-") && f.extension == "json"
        }?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { StudyProject.fromJson(JSONObject(file.readText())) }.getOrNull()
    }

    fun openHistory(b: ResearchBundle) {
        bundle = b
        theme = b.theme
        project = null
        preset = null
        datasetStats = null
        AppSelection.researchTheme = b.theme
        AppSelection.researchBundleId = b.id
        AppSelection.datasetPath = null
        AppSelection.preset = null
    }

    /** Repeats research, ingestion, dataset creation and tuning while this ViewModel is alive. */
    fun startAutopilot(intervalMinutes: Int = autopilotIntervalMinutes) {
        if (autopilotRunning || running || theme.isBlank()) return
        autopilotIntervalMinutes = intervalMinutes.coerceIn(5, 1440)
        autopilotRunning = true
        autopilotJob = viewModelScope.launch {
            try {
                while (autopilotRunning) {
                    start()
                    job?.join()
                    if (!autopilotRunning) break
                    status = "ciclo concluído · próximo ciclo em " + autopilotIntervalMinutes + " min"
                    kotlinx.coroutines.delay(autopilotIntervalMinutes * 60_000L)
                }
            } finally {
                autopilotRunning = false
            }
        }
    }

    fun stop() {
        autopilotRunning = false
        autopilotJob?.cancel()
        autopilotJob = null
        job?.cancel()
        running = false
        stage = StudyStage.IDLE
        status = "interrompido"
    }

    private fun loadCycleMetrics(): List<StudyCycleMetric> = runCatching {
        val array = JSONArray(metricsFile.readText())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            StudyCycleMetric(
                at = item.optLong("at"),
                documents = item.optInt("documents"),
                downloads = item.optInt("downloads"),
                tokens = item.optLong("tokens"),
                examples = item.optInt("examples"),
            )
        }.takeLast(30)
    }.getOrDefault(emptyList())

    private fun appendCycleMetric(metric: StudyCycleMetric): List<StudyCycleMetric> {
        val updated = (loadCycleMetrics() + metric).takeLast(30)
        metricsFile.parentFile?.mkdirs()
        metricsFile.writeText(JSONArray().apply {
            updated.forEach { item ->
                put(JSONObject().apply {
                    put("at", item.at)
                    put("documents", item.documents)
                    put("downloads", item.downloads)
                    put("tokens", item.tokens)
                    put("examples", item.examples)
                })
            }
        }.toString(2))
        return updated
    }

    fun trainingDataset(): File? = project?.txt ?: project?.jsonl

    private fun append(line: String) {
        log = (log + line + "\n").takeLast(14_000)
    }

    fun estimatedMinutes(): Int = preset?.estimatedMinutes ?: 0
    fun fmtEta(): String = Perf.fmtDuration((preset?.estimatedMinutes ?: 0) * 60.0)
}
