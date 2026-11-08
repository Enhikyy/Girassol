package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.compiler.*
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.integration.android.TermuxBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BuildViewModel(app: Application) : AndroidViewModel(app) {
    val report = BuildCoordinator.report
    val running = BuildCoordinator.running
    var project by mutableStateOf("${TermuxBridge.HOME}/girassol")
    var module by mutableStateOf(":app")
    var variant by mutableStateOf(BuildVariant.DEBUG)
    var offline by mutableStateOf(false)
    var message by mutableStateOf(""); private set
    private var job: Job? = null
    init { viewModelScope.launch {
        try {
            val cfg = SettingsRepository(app).settings.first()
            if (project == "${TermuxBridge.HOME}/girassol") project = cfg.termuxProject
            BuildCoordinator.restore(app)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            message = "Não foi possível restaurar: ${error.message}"
        }
    } }
    fun start() {
        if (job?.isActive == true || running.value) return
        job = viewModelScope.launch {
            try {
                message = ""
                val request = BuildRequest(TermuxBridge.projectPath(project), module, variant, offline)
                val result = BuildCoordinator.run(getApplication(), request)
                message = if (result.ok) "APK verificado. Caminhos e hashes estão disponíveis abaixo." else "Compilação interrompida na etapa com falha."
            } catch (cancelled: CancellationException) { message = "Espera interrompida; o processo externo pode continuar no Termux."; throw cancelled
            } catch (error: Exception) { message = error.message ?: "Falha na compilação" }
        }
    }
    fun stopWaiting() { job?.cancel() }
    val ownsRun get() = job?.isActive == true
}
