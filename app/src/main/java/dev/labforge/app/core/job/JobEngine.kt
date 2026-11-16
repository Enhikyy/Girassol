package dev.labforge.app.core.job

import android.content.Context
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.core.native.NativeCallback
import dev.labforge.app.service.GirassolService
import dev.labforge.app.service.MemoryBooster
import dev.labforge.app.service.MemoryGuard
import dev.labforge.app.core.train.TrainingCheckpointStore
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
    val output: String? = null,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val finalLoss: Float? = null,
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
    private var lastFlushed = -1
    private var current: Job? = null

    /** Guards the check-then-set in [begin] so two callers cannot both start a job. */
    private val startLock = Any()

    init {
        scope.launch {
            while (isActive) {
                delay(260)
                val snapshot = synchronized(logBuffer) {
                    if (logBuffer.length == lastFlushed) null
                    else {
                        lastFlushed = logBuffer.length
                        logBuffer.toString()
                    }
                }
                if (snapshot != null) _state.update { it.copy(log = snapshot) }
            }
        }
    }

    fun append(line: String) {
        synchronized(logBuffer) {
            if (logBuffer.length < 400_000) logBuffer.append(line).append('\n')
        }
    }

    @Synchronized
    fun clear() {
        // Never expose a finished state while a native job is alive: doing so
        // would let a second job start on top of the first one.
        if (_state.value.running) return
        synchronized(logBuffer) { logBuffer.setLength(0) }
        lastFlushed = -1
        _state.value = JobState()
    }

    val isRunning: Boolean get() = _state.value.running

    private fun begin(context: Context, kind: String, title: String, detail: String, output: File?): Boolean {
        // Atomic claim: `if (running) return` alone is a TOCTOU race when the UI and
        // the autodidact flow both fire a job in the same frame.
        synchronized(startLock) {
            if (_state.value.running) return false
            synchronized(logBuffer) { logBuffer.setLength(0) }
            lastFlushed = -1
            _state.value = JobState(
                running = true, kind = kind, title = title, progress = 0.02f,
                startedAt = System.currentTimeMillis(), output = output?.absolutePath,
            )
        }
        if (!GirassolService.start(context, title, detail)) {
            _state.value = JobState(
                done = true, success = false,
                summary = "Não foi possível iniciar o serviço em segundo plano",
                finishedAt = System.currentTimeMillis(),
            )
            return false
        }
        return true
    }

    private fun epochProgress(epoch: Int, step: Int, total: Int, epochs: Int): Float {
        if (total <= 0 || epochs <= 0) return 0.02f
        val ratio = (step.toFloat() / total).coerceIn(0f, 1f)
        return (((epoch - 1).coerceAtLeast(0) + ratio) / epochs).coerceIn(0.02f, 0.99f)
    }

    private fun finish(success: Boolean, summary: String, output: File?, finalLoss: Float? = null) {
        val finishedAt = System.currentTimeMillis()
        val kind = _state.value.kind
        val cleanLoss = finalLoss?.takeIf { loss -> loss.isFinite() && loss >= 0f }
        val ctx = appContext
        // Persist before publishing `done`: the ViewModel refreshes its checkpoint
        // list from that signal and must never observe a partially written history.
        if (ctx != null && success && output != null && (kind == "lora" || kind == "full")) {
            runCatching { TrainingCheckpointStore(ctx).record(kind, output, finishedAt, cleanLoss) }
                .onFailure { append("[checkpoint] falha ao registrar histórico: ${it.message}") }
        }
        _state.update {
            it.copy(
                running = false, done = true, success = success, progress = 1f,
                summary = summary, finishedAt = finishedAt,
                output = output?.absolutePath ?: it.output,
                finalLoss = cleanLoss,
            )
        }
        current = null
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
        val trim = !budget.ok

        current = scope.launch {
            if (trim) MemoryGuard.trim()
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
        context: Context,
        model: File,
        output: File,
        dataset: File,
        epochs: Int,
        lr: Float,
        nCtx: Int,
        threads: Int,
        rank: Int,
        alpha: Float,
        targets: String,
    ) {
        if (isRunning) return
        init(context)
        val budget = MemoryGuard.check(context, model.length(), "lora")
        if (!begin(context, "lora", "LoRA · ${model.name}", budget.message, output)) return
        append("[ram] ${budget.message}")
        val trim = !budget.ok

        current = scope.launch {
            if (trim) MemoryGuard.trim()
            val boost = MemoryBooster.boost(context)
            append("[ram] liberados ${boost.freedMb} MB antes do LoRA")
            val cb = object : NativeCallback {
                override fun onLog(line: String) = append(line)
                override fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double) {
                    if (loss.isFinite() && loss > 0) {
                        _state.update { st -> st.copy(loss = (st.loss + loss.toFloat()).takeLast(400)) }
                    }
                    _state.update { it.copy(progress = epochProgress(epoch, step, total, epochs)) }
                    if (epoch > 0) _state.update { it.copy(title = "LoRA · epoch $epoch/$epochs") }
                }
            }
            val r = LlamaBridge.loraTrain(
                model = model.absolutePath,
                output = output.absolutePath,
                dataset = dataset.absolutePath,
                epochs = epochs,
                lr = lr,
                nCtx = nCtx,
                threads = threads,
                rank = rank,
                alpha = alpha,
                targets = targets,
                callback = cb,
            )
            val summary = if (r.ok) {
                "Adaptador LoRA salvo · ${r.tensors} tensores · loss %.4f".format(r.loss)
            } else r.error ?: "falha no treino LoRA"
            finish(r.ok, summary, output.takeIf { r.ok }, r.loss.toFloat())
        }
    }

    // ------------------------------------------------------------- full finetune

    fun startFinetune(
        context: Context,
        model: File,
        output: File,
        dataset: File,
        epochs: Int,
        lr: Float,
        nCtx: Int,
        nBatch: Int,
        nUbatch: Int,
        threads: Int,
        filter: String,
    ) {
        if (isRunning) return
        init(context)
        val budget = MemoryGuard.check(context, model.length(), "full")
        if (!begin(context, "full", "Fine-tuning · ${model.name}", budget.message, output)) return
        append("[ram] ${budget.message}")
        val trim = !budget.ok

        current = scope.launch {
            if (trim) MemoryGuard.trim()
            val boost = MemoryBooster.boost(context)
            append("[ram] liberados ${boost.freedMb} MB antes do full FT")
            val cb = object : NativeCallback {
                override fun onLog(line: String) = append(line)
                override fun onEpoch(epoch: Int, step: Int, total: Int, loss: Double, lr: Double) {
                    if (loss.isFinite() && loss > 0) {
                        _state.update { st -> st.copy(loss = (st.loss + loss.toFloat()).takeLast(400)) }
                    }
                    _state.update { it.copy(progress = epochProgress(epoch, step, total, epochs)) }
                    if (epoch > 0) _state.update { it.copy(title = "Fine-tuning · epoch $epoch/$epochs") }
                }
            }
            val r = LlamaBridge.finetune(
                model = model.absolutePath,
                output = output.absolutePath,
                dataset = dataset.absolutePath,
                epochs = epochs,
                lr = lr,
                nCtx = nCtx,
                nBatch = nBatch,
                nUbatch = nUbatch,
                threads = threads,
                filter = filter,
                callback = cb,
            )
            val summary = if (r.ok) {
                "Modelo treinado · loss final %.4f · ${r.sequences} sequências".format(r.loss)
            } else r.error ?: "falha no treino"
            finish(r.ok, summary, output.takeIf { r.ok }, r.loss.toFloat())
        }
    }
}
