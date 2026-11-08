package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.evolution.EvidenceJournal
import dev.labforge.app.core.evolution.InferenceStudy
import dev.labforge.app.core.evolution.SkillProgress
import dev.labforge.app.core.evolution.SkillTree
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.core.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class EvolutionViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ModelStore(app)
    private val journal = EvidenceJournal(store.agentDir)
    private val settings = SettingsRepository(app)
    private val reportFile = File(store.runsDir, "inference-benchmark.json")
    var skills by mutableStateOf<List<SkillProgress>>(emptyList()); private set
    var nextPractice by mutableStateOf(""); private set
    var report by mutableStateOf<JSONObject?>(null); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    private var job: Job? = null
    init { refresh() }
    fun refresh() { viewModelScope.launch {
        try {
            val rows = withContext(Dispatchers.IO) { journal.all() }
            skills = SkillTree.progress(rows)
            nextPractice = SkillTree.next(rows)?.skill?.title.orEmpty()
            report = withContext(Dispatchers.IO) { if (reportFile.isFile) JSONObject(reportFile.readText()) else null }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            status = "Não foi possível ler os registros: ${error.message}"
        }
    } }
    fun benchmark() {
        if (busy) return
        busy = true
        job = viewModelScope.launch {
            try {
                check(!dev.labforge.app.core.job.JobEngine.isRunning) { "Aguarde o treino/quantização terminar" }
                status = "Medindo 1, metade e todas as CPUs. Mantenha o aparelho ocioso."
                val cfg = settings.settings.first()
                report = InferenceStudy.run(cfg, reportFile)
                status = if (report!!.isNull("recommendedThreads")) "Nenhuma configuração teve medições válidas" else "Medição concluída; confira as amostras antes de aplicar."
            } catch (cancelled: CancellationException) { status = "Medição cancelada"; throw cancelled
            } catch (error: Exception) { status = error.message ?: "Falha na medição"
            } finally { busy = false }
        }
    }
    fun cancel() { job?.cancel() }
    fun applyRecommendation() { viewModelScope.launch {
        try {
            val result = report ?: error("Execute o benchmark primeiro")
            withContext(Dispatchers.IO) {
                settings.update { cfg ->
                    require(InferenceStudy.matches(result, cfg)) { "Modelo, adaptador ou contexto mudou; refaça o benchmark" }
                    val threads = result.getInt("recommendedThreads")
                    require(threads in 1..Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                    cfg.copy(localThreads = threads)
                }
            }
            status = "Quantidade de threads aplicada à inferência local"
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { status = error.message ?: "Falha ao aplicar" }
    } }
}
