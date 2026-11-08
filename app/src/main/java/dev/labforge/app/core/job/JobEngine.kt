package dev.labforge.app.core.job

import android.content.Context
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.native.NativeCallback
import dev.labforge.app.service.GirassolService
import dev.labforge.app.service.MemoryBooster
import dev.labforge.app.service.MemoryGuard
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JobState(
    val running: Boolean = false,
    val done: Boolean = false,
    val success: Boolean = false,
    val kind: String = "",
    val title: String = "",
    val log: String = "",
    val progress: Float = 0f,
    val summary: String = "",
    val loss: List<Float> = emptyList(),
    val ramAvailableGb: List<Float> = emptyList(),
    val output: String? = null,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
)

/**
 * Process-wide job runner. Lives outside the ViewModel so a configuration change
 * (or leaving the screen) never kills a running quantization/training job.
 */
object JobEngine {

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        append("[erro] ${error.message}")
        _state.update {
            it.copy(
                running = false, done = true, success = false,
                summary = "Falha inesperada: ${error.message}", finishedAt = System.currentTimeMillis(),
            )
        }
        appContext?.let { GirassolService.stop(it) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + errorHandler)
    private val _state = MutableStateFlow(JobState())
    val state: StateFlow<JobState> = _state

    private val logBuffer = StringBuilder()
    private val logClock = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var logRevision = 0L
    private var lastFlushedRevision = -1L
    private var current: Job? = null

    private companion object {
        const val MAX_LOG_CHARS = 300_000
    }

    /** Guards the check-then-set in [begin] so two callers cannot both start a job. */
    private val startLock = Any()

    init {
        scope.launch {
            while (isActive) {
                delay(260)
                val snapshot = synchronized(logBuffer) {
                    if (logRevision == lastFlushedRevision) null
                    else {
                        lastFlushedRevision = logRevision
                        logBuffer.toString()
                    }
                }
                if (snapshot != null) _state.update { it.copy(log = snapshot) }
            }
        }
    }

    fun append(line: String) {
        val lower = line.lowercase(Locale.ROOT)
        val level = when {
            lower.contains("error") || lower.contains("fatal") || lower.contains("falha") -> "ERROR"
            lower.contains("warn") || lower.contains("pressão crítica") -> "WARN"
            lower.startsWith("[ram]") -> "RAM"
            lower.startsWith("[train]") || lower.startsWith("[lora]") -> "TRAIN"
            lower.startsWith("[auto]") -> "AUTO"
            else -> "INFO"
        }
        val timestamp = synchronized(logClock) { logClock.format(Date()) }
        val record = "[$timestamp][$level][${Thread.currentThread().name}] $line\n"
        synchronized(logBuffer) {
            logBuffer.append(record)
            if (logBuffer.length > MAX_LOG_CHARS) {
                val excess = logBuffer.length - MAX_LOG_CHARS
                val boundary = logBuffer.indexOf("\n", excess.coerceAtMost(logBuffer.length - 1))
                if (boundary >= 0) logBuffer.delete(0, boundary + 1)
                else logBuffer.delete(0, excess)
            }
            logRevision++
        }
    }

    fun clear() {
        synchronized(logBuffer) { logBuffer.setLength(0); logRevision++ }
        lastFlushedRevision = -1L
        _state.value = JobState()
    }

    val isRunning: Boolean get() = _state.value.running

    private fun begin(context: Context, kind: String, title: String, detail: String, output: File?): Boolean {
        // Atomic claim: `if (running) return` alone is a TOCTOU race when the UI and
        // the autodidact flow both fire a job in the same frame.
        synchronized(startLock) {
            if (_state.value.running) return false
            synchronized(logBuffer) { logBuffer.setLength(0) }
            lastFlushedRevision = -1L
            _state.value = JobState(
                running = true, kind = kind, title = title, progress = 0.02f,
                startedAt = System.currentTimeMillis(), output = output?.absolutePath,
            )
        }
        GirassolService.start(context, title, detail)
        return true
    }

    private fun finish(success: Boolean, summary: String, output: File?) {
        _state.update {
            it.copy(
                running = false, done = true, success = success, progress = 1f,
                summary = summary, finishedAt = System.currentTimeMillis(),
                output = output?.absolutePath ?: it.output,
            )
        }
        append("[job] finalizado success=$success; duração=${if (_state.value.startedAt > 0) System.currentTimeMillis() - _state.value.startedAt else 0} ms; resumo=$summary")
        current = null
        val ctx = appContext
        if (ctx != null) GirassolService.stop(ctx)
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ------------------------------------------------------------------ quantize

    fun startQuantize(
        context: Context,
        input: File,
        output: File,
        ftype: String,
        imatrix: File?,
        threads: Int,
    ) {
        if (isRunning) return
        init(context)
        val budget = MemoryGuard.check(context, input.length(), "quantize")
        if (!begin(context, "quantize", "Quantizando ${input.name}", budget.message, output)) return
        append("[ram] ${budget.message}")
        if (!budget.ok) MemoryGuard.trim()

        current = scope.launch {
            val boost = MemoryBooster.boost(context)
            append("[ram] liberados ${boost.freedMb} MB (${boost.appsTouched} apps, ${boost.cachesMb} MB de cache)")
            val cb = object : NativeCallback {
                override fun onLog(line: String) = append(line)
                override fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double) = Unit
            }
            _state.update { it.copy(progress = 0.08f) }
            val r = LlamaBridge.quantize(
                input = input.absolutePath,
                output = output.absolutePath,
                ftype = ftype,
                imatrix = imatrix?.absolutePath,
                threads = threads,
                callback = cb,
            )
            val summary = if (r.ok) {
                "Concluído · ${dev.labforge.app.ui.components.humanBytes(r.bytes)} → ${output.name}"
            } else r.error ?: "falha na quantização"
            finish(r.ok, summary, output.takeIf { r.ok })
        }
    }

    // --------------------------------------------------------------- LoRA train

    fun startLora(
        context: Context, model: File, output: File, dataset: File, epochs: Int, lr: Float,
        nCtx: Int, threads: Int, rank: Int, alpha: Float, targets: String,
    ) {
        if (isRunning) return
        init(context)
        val initial = MemoryGuard.check(context, model.length(), "lora", nCtx, rank)
        if (!begin(context, "lora", "LoRA · ${model.name}", initial.message, output)) return
        append("[ram] ${initial.message}")

        current = scope.launch(Dispatchers.IO) {
            try {
                val boost = runCatching { MemoryBooster.boost(context) }.getOrNull()
                if (boost != null) append("[ram] liberados ${boost.freedMb} MB antes do LoRA")
                else append("[ram] limpeza de memória indisponível; fazendo nova avaliação")

                val plan = MemoryGuard.loraPlan(context, model.length(), nCtx, rank, threads, targets)
                append("[ram] ${plan.budget.message}")
                if (!plan.budget.ok) {
                    finish(false, plan.budget.message, null)
                    return@launch
                }
                if (plan.adjusted) {
                    append("[auto] perfil móvel aplicado: ctx=${plan.nCtx}, rank=${plan.rank}, threads=${plan.threads}, alvos=${plan.targets}")
                }

                val monitor = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        val live = MemoryGuard.check(context, model.length(), "lora", plan.nCtx, plan.rank)
                        _state.update { state ->
                            state.copy(ramAvailableGb = (state.ramAvailableGb + live.availableGb.toFloat()).takeLast(40))
                        }
                        if (live.availableGb < live.reserveGb + 0.12) {
                            append("[ram] pressão crítica detectada (${"%.2f".format(live.availableGb)} GB livres); solicitando parada segura após a época atual")
                            LlamaBridge.requestTrainingStop()
                            break
                        }
                        delay(3500)
                    }
                }
                val result = try {
                    LlamaBridge.loraTrain(
                        model = model.absolutePath, output = output.absolutePath, dataset = dataset.absolutePath,
                        epochs = epochs.coerceIn(1, 5), lr = lr, nCtx = plan.nCtx, threads = plan.threads,
                        rank = plan.rank, alpha = plan.rank.toFloat(), targets = plan.targets, callback = object : NativeCallback {
                            override fun onLog(line: String) = append(line)
                            override fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double) {
                                if (loss.isFinite() && loss > 0) _state.update { st -> st.copy(loss = (st.loss + loss.toFloat()).takeLast(400)) }
                                if (total > 0) _state.update { it.copy(progress = (step.toFloat() / total).coerceIn(0f, 1f)) }
                                if (epoch > 0) _state.update { it.copy(title = "LoRA · epoch $epoch/${epochs.coerceIn(1, 5)}") }
                            }
                        },
                    )
                } finally { monitor.cancel() }
                val summary = if (result.ok) {
                    "Adaptador LoRA salvo · ${result.tensors} tensores · loss %.4f".format(result.loss)
                } else result.error ?: "falha no treino LoRA; verifique o checkpoint parcial"
                finish(result.ok, summary, output.takeIf { result.ok })
            } catch (error: Throwable) {
                append("[erro] LoRA exception: ${error.stackTraceToString()}")
                finish(false, "Falha no treinamento LoRA: ${error.message ?: error.javaClass.simpleName}", null)
            }
        }
    }
    // ------------------------------------------------------------- full finetune

    fun startFinetune(
        context: Context, model: File, output: File, dataset: File, epochs: Int, lr: Float,
        nCtx: Int, nBatch: Int, nUbatch: Int, threads: Int, filter: String,
    ) {
        if (isRunning) return
        init(context)
        val budget = MemoryGuard.check(context, model.length(), "full", nCtx)
        if (!begin(context, "full", "Fine-tuning · ${model.name}", budget.message, output)) return
        append("[ram] ${budget.message}")
        if (!budget.ok) {
            finish(false, budget.message, null)
            return
        }

        current = scope.launch(Dispatchers.IO) {
            try {
                val boost = runCatching { MemoryBooster.boost(context) }.getOrNull()
                if (boost != null) append("[ram] liberados ${boost.freedMb} MB antes do full FT")
                val afterBoost = MemoryGuard.check(context, model.length(), "full", nCtx)
                if (!afterBoost.ok) {
                    finish(false, afterBoost.message, null)
                    return@launch
                }
                val result = LlamaBridge.finetune(
                    model = model.absolutePath, output = output.absolutePath, dataset = dataset.absolutePath,
                    epochs = epochs.coerceIn(1, 3), lr = lr, nCtx = nCtx, nBatch = minOf(nBatch, 64),
                    nUbatch = minOf(nUbatch, 64), threads = threads.coerceIn(1, 4), filter = filter,
                    callback = object : NativeCallback {
                        override fun onLog(line: String) = append(line)
                        override fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double) {
                            if (loss.isFinite() && loss > 0) _state.update { st -> st.copy(loss = (st.loss + loss.toFloat()).takeLast(400)) }
                            if (total > 0) _state.update { it.copy(progress = (step.toFloat() / total).coerceIn(0f, 1f)) }
                            if (epoch > 0) _state.update { it.copy(title = "Fine-tuning · epoch $epoch/${epochs.coerceIn(1, 3)}") }
                        }
                    },
                )
                val summary = if (result.ok) {
                    "Modelo treinado · loss final %.4f · ${result.sequences} sequências".format(result.loss)
                } else result.error ?: "falha no treino"
                finish(result.ok, summary, output.takeIf { result.ok })
            } catch (error: Throwable) {
                append("[erro] fine-tuning exception: ${error.stackTraceToString()}")
                finish(false, "Falha no fine-tuning: ${error.message ?: error.javaClass.simpleName}", null)
            }
        }
    }
}
