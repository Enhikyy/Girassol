package dev.labforge.app.vm

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.gguf.GgmlType
import dev.labforge.app.core.imatrix.ImatrixReader
import dev.labforge.app.core.model.AssetKind
import dev.labforge.app.core.model.ModelEntry
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.core.AppSelection
import dev.labforge.app.core.job.JobEngine
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.settings.AppSettings
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.term.GitRepo
import dev.labforge.app.core.term.ShellSession
import dev.labforge.app.core.train.ChatFormat
import dev.labforge.app.core.train.DatasetBuilder
import dev.labforge.app.core.train.DatasetStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class QuantOption(val ftype: String, val label: String, val bits: Double, val note: String) {
    val quality: Int get() = when {
        bits >= 15.0 -> 5
        bits >= 8.0 -> 5
        bits >= 6.0 -> 4
        bits >= 4.6 -> 3
        bits >= 3.6 -> 2
        bits >= 2.5 -> 1
        else -> 0
    }
}

val QUANT_OPTIONS = listOf(
    QuantOption("Q8_0", "Q8_0", 8.5, "quase lossless"),
    QuantOption("Q6_K", "Q6_K", 6.56, "excelente"),
    QuantOption("Q5_K_M", "Q5_K_M", 5.67, "ótimo equilíbrio"),
    QuantOption("Q5_K_S", "Q5_K_S", 5.5, "menor"),
    QuantOption("Q4_K_M", "Q4_K_M", 4.85, "recomendado"),
    QuantOption("Q4_K_S", "Q4_K_S", 4.58, "mais leve"),
    QuantOption("Q4_0", "Q4_0", 4.5, "legado"),
    QuantOption("IQ4_XS", "IQ4_XS", 4.25, "i-quant"),
    QuantOption("Q3_K_M", "Q3_K_M", 3.9, "compacto"),
    QuantOption("IQ3_M", "IQ3_M", 3.66, "compacto+"),
    QuantOption("IQ3_S", "IQ3_S", 3.44, "agressivo"),
    QuantOption("Q2_K", "Q2_K", 2.625, "muito leve"),
    QuantOption("IQ2_M", "IQ2_M", 2.7, "experimental"),
    QuantOption("IQ2_XS", "IQ2_XS", 2.31, "experimental"),
    QuantOption("F16", "F16", 16.0, "sem perdas fp16"),
    QuantOption("BF16", "BF16", 16.0, "bfloat16"),
)

data class QuantConfig(
    val model: File,
    val ftype: String,
    val threads: Int,
    val imatrix: File?,
    val outputName: String,
)

data class LoraConfig(
    val model: File,
    val dataset: File,
    val epochs: Int,
    val lr: Float,
    val nCtx: Int,
    val threads: Int,
    val rank: Int,
    val alpha: Float,
    val targets: String,
    val outputName: String,
)

data class TrainConfig(
    val model: File,
    val dataset: File,
    val epochs: Int,
    val lr: Float,
    val nCtx: Int,
    val nBatch: Int,
    val nUbatch: Int,
    val threads: Int,
    val filter: String,
    val outputName: String,
)

class LabViewModel(app: Application) : AndroidViewModel(app) {

    val store = ModelStore(app)
    val lessons = LessonStore(store.agentDir)
    val shell = ShellSession(store.workspace())
    val git = GitRepo(store.workspace())
    private val settingsRepo = SettingsRepository(app)

    var models by mutableStateOf<List<ModelEntry>>(emptyList())
        private set
    var imatrices by mutableStateOf<List<File>>(emptyList())
        private set
    var datasets by mutableStateOf<List<File>>(emptyList())
        private set
    var outputs by mutableStateOf<List<File>>(emptyList())
        private set

    var selectedModel by mutableStateOf<ModelEntry?>(null)
        private set
    var imatrixReport by mutableStateOf<ImatrixReader.Report?>(null)
        private set
    var imatrixCoverage by mutableStateOf<ImatrixReader.Coverage?>(null)
        private set
    var imatrixFile by mutableStateOf<File?>(null)
        private set
    var loadingModel by mutableStateOf(false)
        private set
    var loadingImatrix by mutableStateOf(false)
        private set

    var settings by mutableStateOf(AppSettings())
        private set
    var nativeVersion by mutableStateOf("verificando motor…")
        private set
    var deviceRamGb by mutableStateOf(0.0)
        private set
    var deviceCores by mutableStateOf(1)
        private set

    // Job state (single concurrent job)
    var jobRunning by mutableStateOf(false)
        private set
    var jobTitle by mutableStateOf("")
        private set
    var jobLog by mutableStateOf("")
        private set
    var jobProgress by mutableStateOf(0f)
        private set
    var jobDone by mutableStateOf(false)
        private set
    var jobSuccess by mutableStateOf(false)
        private set
    var jobSummary by mutableStateOf("")
        private set
    var lossHistory by mutableStateOf<List<Float>>(emptyList())
        private set
    var jobStartedAt by mutableStateOf(0L)
        private set
    var jobKind by mutableStateOf("")
        private set

    var datasetStats by mutableStateOf<DatasetStats?>(null)
        private set
    var builtDataset by mutableStateOf<File?>(null)
        private set
    var preparingDataset by mutableStateOf(false)
        private set

    private var lastFinishedAt = 0L

    init {
        JobEngine.init(app)
        nativeVersion = LlamaBridge.version()
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        deviceRamGb = mi.totalMem / 1e9
        deviceCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        viewModelScope.launch {
            settingsRepo.settings.collect { settings = it }
        }
        viewModelScope.launch(Dispatchers.IO) { refresh() }
        viewModelScope.launch {
            JobEngine.state.collect { st ->
                jobRunning = st.running
                jobDone = st.done
                jobSuccess = st.success
                jobTitle = st.title
                jobLog = st.log
                jobProgress = st.progress
                jobSummary = st.summary
                lossHistory = st.loss
                jobStartedAt = st.startedAt
                jobKind = st.kind
                if (st.done && st.finishedAt != lastFinishedAt) {
                    lastFinishedAt = st.finishedAt
                    refresh()
                }
            }
        }
    }

    // ------------------------------------------------------------------ assets

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val loadedModels = store.models().map { store.readModel(it) }
        val loadedIm = store.imatrices()
        val loadedDs = store.datasets()
        val loadedOut = store.outputs()
        withContext(Dispatchers.Main) {
            models = loadedModels
            imatrices = loadedIm
            datasets = loadedDs
            outputs = loadedOut
            if (selectedModel == null) selectedModel = loadedModels.firstOrNull()
            AppSelection.modelPath = selectedModel?.file?.absolutePath
            if (imatrixFile == null) {
                imatrixFile = loadedIm.firstOrNull()
                imatrixFile?.let { parseImatrix(it) }
            }
        }
    }

    fun importAsset(uri: Uri, kind: AssetKind, onDone: (File?, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { store.copyFromUri(uri, kind) }
            }
            result.onSuccess { file ->
                refresh()
                when (kind) {
                    AssetKind.MODEL -> file?.let { f ->
                        val entry = withContext(Dispatchers.IO) { store.readModel(f) }
                        selectModel(entry)
                    }
                    AssetKind.IMATRIX -> file?.let { parseImatrix(it) }
                    else -> Unit
                }
                onDone(file, null)
            }.onFailure { onDone(null, it.message ?: "falha ao importar") }
        }
    }

    fun selectModel(entry: ModelEntry) {
        selectedModel = entry
        AppSelection.modelPath = entry.file.absolutePath
    }

    fun parseImatrix(file: File) {
        viewModelScope.launch {
            loadingImatrix = true
            val parsed = runCatching { withContext(Dispatchers.IO) { ImatrixReader.read(file) } }
            loadingImatrix = false
            parsed.onSuccess { report ->
                imatrixReport = report
                imatrixFile = file
                imatrixCoverage = selectedModel?.meta?.let { report.coverageAgainst(it) }
            }.onFailure {
                imatrixReport = null
                imatrixCoverage = null
                jobSummary = "Falha ao ler imatrix: ${it.message}"
            }
        }
    }

    fun linkedModelForImatrix() {
        val model = selectedModel?.meta ?: return
        imatrixCoverage = imatrixReport?.coverageAgainst(model)
    }

    fun deleteAsset(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(file) }
            if (selectedModel?.file == file) selectedModel = null
            if (imatrixFile == file) { imatrixFile = null; imatrixReport = null; imatrixCoverage = null }
            refresh()
        }
    }

    // ------------------------------------------------------------------ dataset

    fun prepareDataset(
        files: List<File>,
        format: ChatFormat,
        name: String,
        onDone: (File?) -> Unit = {},
    ) {
        viewModelScope.launch {
            preparingDataset = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val out = store.uniqueFile(AssetKind.DATASET, "$name.txt")
                    DatasetBuilder.build(files, format, out)
                }
            }
            preparingDataset = false
            result.onSuccess { (file, stats) ->
                datasetStats = stats
                builtDataset = file
                AppSelection.datasetPath = file.absolutePath
                refresh()
                onDone(file)
            }.onFailure {
                jobSummary = "Falha ao preparar dataset: ${it.message}"
                onDone(null)
            }
        }
    }

    fun inspectDataset(file: File, nCtx: Int = 512) {
        val model = selectedModel?.meta ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val text = runCatching {
                val buf = CharArray(4_000_000)
                val n = file.reader().use { it.read(buf) }
                String(buf, 0, n.coerceAtLeast(0))
            }.getOrDefault("")
            val stats = LlamaBridge.tokenStats(model.file.absolutePath, text, nCtx)
            withContext(Dispatchers.Main) {
                datasetStats = DatasetStats(
                    documents = 1,
                    characters = text.length.toLong(),
                    approxTokens = if (stats.tokens > 0) stats.tokens else (text.length / 3.6).toLong(),
                    skippedLines = 0,
                    detectedFormat = "tokenizado (${stats.vocab} tokens de vocabulário)",
                )
                builtDataset = file
            }
        }
    }

    // ------------------------------------------------------------------ jobs

    fun runQuantize(config: QuantConfig) {
        if (jobRunning) return
        val out = File(store.outputsDir, safeOutputName(config.outputName) {
            "${config.model.nameWithoutExtension}-${config.ftype}.gguf"
        })
        JobEngine.startQuantize(
            context = getApplication(),
            input = config.model,
            output = out,
            ftype = config.ftype,
            imatrix = config.imatrix,
            threads = config.threads,
        )
    }

    fun runLora(config: LoraConfig) {
        if (jobRunning) return
        val out = File(store.outputsDir, safeOutputName(config.outputName) {
            "${config.model.nameWithoutExtension}-lora-r${config.rank}.gguf"
        })
        JobEngine.startLora(
            context = getApplication(),
            model = config.model,
            output = out,
            dataset = config.dataset,
            epochs = config.epochs,
            lr = config.lr,
            nCtx = config.nCtx,
            threads = config.threads,
            rank = config.rank,
            alpha = config.alpha,
            targets = config.targets,
        )
    }

    fun runFinetune(config: TrainConfig) {
        if (jobRunning) return
        val out = File(store.outputsDir, safeOutputName(config.outputName) {
            "${config.model.nameWithoutExtension}-finetuned.gguf"
        })
        JobEngine.startFinetune(
            context = getApplication(),
            model = config.model,
            output = out,
            dataset = config.dataset,
            epochs = config.epochs,
            lr = config.lr,
            nCtx = config.nCtx,
            nBatch = config.nBatch,
            nUbatch = config.nUbatch,
            threads = config.threads,
            filter = config.filter,
        )
    }

    fun clearJob() {
        JobEngine.clear()
    }

    private fun safeOutputName(raw: String, fallback: () -> String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallback() else cleaned
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(block) }
    }

    suspend fun currentSettings(): AppSettings = settingsRepo.settings.first()

    fun refreshLessons() = Unit

    companion object {
        fun estimateSize(model: ModelEntry, bits: Double): Long {
            val meta = model.meta ?: return 0L
            var total = 0.0
            for (t in meta.tensors) {
                total += if (t.dims.size <= 1) t.elements * 4.0
                else t.elements * bits / 8.0
            }
            return total.toLong()
        }

        fun ramAdvice(ramGb: Double, params: Long): String = when {
            params == 0L -> ""
            params > 7e9 && ramGb < 12 -> "Modelo grande para este aparelho: use um modelo menor ou quantização Q4."
            ramGb < 6 -> "RAM limitada (${"%.1f".format(ramGb)} GB). Treino só para modelos < 100M."
            else -> "RAM ok para treino de modelos pequenos (até ~0.5B com filtro de tensores)."
        }
    }
}
