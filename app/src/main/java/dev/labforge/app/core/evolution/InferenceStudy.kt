package dev.labforge.app.core.evolution

import dev.labforge.app.core.native.GenerationCallback
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** End-to-end throughput: loading + prompt processing + generation, not isolated decode speed. */
data class InferenceTrial(val threads: Int, val tokens: Int, val nanos: Long, val error: String? = null) {
    val throughput: Double get() = if (error == null && tokens > 0 && nanos > 0) tokens * 1e9 / nanos else 0.0
}
object InferenceStudy {
    private val runLock = Mutex()
    private fun fileStamp(path: String): JSONObject = File(path).let { file ->
        require(file.isFile) { "Arquivo local não encontrado: $path" }
        JSONObject().put("path", file.canonicalPath).put("bytes", file.length()).put("modified", file.lastModified())
    }
    internal fun adaptersStamp(spec: String): String {
        val paths = if (spec.isBlank()) emptyList() else if (spec.trim().startsWith("[")) {
            val list = JSONArray(spec)
            (0 until list.length()).map { list.getJSONObject(it).getString("path") }
        } else listOf(spec)
        return JSONArray().apply { paths.forEach { put(fileStamp(it)) } }.toString()
    }

    suspend fun measure(candidates: List<Int>, repetitions: Int = 2, evaluate: suspend (Int) -> InferenceTrial): List<InferenceTrial> {
        require(candidates.isNotEmpty() && candidates.size <= 8 && candidates.all { it in 1..256 })
        require(repetitions in 1..3)
        val trials = mutableListOf<InferenceTrial>()
        // Interleave configurations so one candidate does not get every cold/late sample.
        repeat(repetitions) { round ->
            for (threads in if (round % 2 == 0) candidates.distinct() else candidates.distinct().reversed()) {
                currentCoroutineContext().ensureActive()
                val trial = try {
                    evaluate(threads).also { require(it.threads == threads && it.tokens > 0 && it.nanos > 0) { "Medição sem tokens/tempo válidos" } }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { InferenceTrial(threads, 0, 0, error.message ?: "Falha") }
                trials += trial
            }
        }
        return trials
    }
    fun best(trials: List<InferenceTrial>): Int? = trials.groupBy { it.threads }.filterValues { rows -> rows.all { it.error == null && it.throughput > 0 } }
        .maxWithOrNull(compareBy<Map.Entry<Int, List<InferenceTrial>>> { median(it.value.map { t -> t.throughput }) }.thenBy { -it.key })?.key
    private fun median(values: List<Double>): Double = values.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }

    suspend fun run(cfg: AppSettings, report: File): JSONObject = withContext(Dispatchers.IO) { runLock.withLock {
        val model = File(cfg.localModelPath)
        require(model.isFile) { "Selecione um modelo GGUF local antes de medir" }
        val modelBefore = fileStamp(cfg.localModelPath).toString()
        val adaptersBefore = adaptersStamp(cfg.localAdapterPath)
        val coroutine = currentCoroutineContext()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val candidates = listOf(1, (cores / 2).coerceAtLeast(1), cores).distinct()
        val trials = measure(candidates) { threads ->
            val begin = System.nanoTime()
            val response = LlamaBridge.generate(cfg.localModelPath,
                """[{"role":"user","content":"Explique em português como testar uma função de soma, com um exemplo."}]""",
                cfg.localAdapterPath, cfg.localContext, 0f, object : GenerationCallback {
                    override fun onToken(bytes: ByteArray) = coroutine.isActive
                }, threads = threads, maxTokens = 64)
            coroutine.ensureActive()
            val json = JSONObject(response)
            check(!json.has("error")) { json.optString("error") }
            InferenceTrial(threads, json.getInt("completion_tokens"), System.nanoTime() - begin)
        }
        coroutine.ensureActive()
        check(modelBefore == fileStamp(cfg.localModelPath).toString() && adaptersBefore == adaptersStamp(cfg.localAdapterPath)) { "Modelo/adaptadores mudaram durante a medição; execute novamente" }
        val result = JSONObject().put("adapterFiles", adaptersBefore).put("version", 1).put("model", model.canonicalPath).put("bytes", model.length())
            .put("modified", model.lastModified()).put("adapter", cfg.localAdapterPath).put("context", cfg.localContext)
            .put("at", System.currentTimeMillis()).put("recommendedThreads", best(trials) ?: JSONObject.NULL)
            .put("metric", "tokens/s incluindo carga e prompt").put("trials", JSONArray().apply {
                trials.forEach { t -> put(JSONObject().put("threads", t.threads).put("tokens", t.tokens)
                    .put("nanos", t.nanos).put("tokensPerSecond", t.throughput).put("error", t.error ?: JSONObject.NULL)) }
            })
        report.parentFile?.mkdirs()
        val tmp = File.createTempFile("benchmark-", ".part", report.parentFile)
        try { tmp.writeText(result.toString(2)); check(tmp.renameTo(report)) } finally { tmp.delete() }
        result
    } }
    fun matches(report: JSONObject, cfg: AppSettings): Boolean = runCatching {
        val file = File(cfg.localModelPath)
        report.optInt("version") == 1 && file.isFile && report.optString("model") == file.canonicalPath && report.optLong("bytes") == file.length() &&
            report.optLong("modified") == file.lastModified() && report.optString("adapter") == cfg.localAdapterPath &&
            report.optInt("context") == cfg.localContext && report.optString("adapterFiles") == adaptersStamp(cfg.localAdapterPath)
    }.getOrDefault(false)
}
