package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.core.autonomy.AutonomyStore
import dev.labforge.app.core.autonomy.BatteryReader
import dev.labforge.app.core.autonomy.SleepScheduler
import dev.labforge.app.core.autonomy.ThermalGate
import dev.labforge.app.core.device.ActionJournal
import dev.labforge.app.core.device.DeviceActionRecord
import dev.labforge.app.core.device.DeviceBridge
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.rsi.RsiEngine
import dev.labforge.app.core.rsi.RsiSnapshot
import dev.labforge.app.core.rsi.RsiResearchLab
import dev.labforge.app.core.rsi.RsiLabSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AutonomyUiState(
    val accessibility: Boolean = false,
    val toolsEnabled: Boolean = false,
    val nightEnabled: Boolean = false,
    val workLabel: String = "não agendado",
    val lastRun: AutonomyStore.LastRun = AutonomyStore.LastRun(0L, false, "", 0),
    val journal: List<DeviceActionRecord> = emptyList(),
    val thermal: Int = ThermalGate.UNSUPPORTED,
    val ramGb: Double = 0.0,
    val battery: Int = -1,
    val charging: Boolean = false,
    val busy: Boolean = false,
    val rsi: RsiSnapshot? = null,
    val rsiLab: RsiLabSnapshot? = null,
)

class AutonomyViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val rsiEngine = RsiEngine(File(app.filesDir, "labforge/agent/rsi.json"))
    private val rsiLab = RsiResearchLab(File(app.filesDir, "labforge/agent/rsi-lab.json"))

    var state by mutableStateOf(AutonomyUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val settings = runCatching { settingsRepo.settings.first() }.getOrDefault(
                dev.labforge.app.core.settings.AppSettings(),
            )
            val work = SleepScheduler.status(context)
            val rsiSnapshot = withContext(Dispatchers.IO) { rsiEngine.snapshot() }
            val rsiLabSnapshot = withContext(Dispatchers.IO) { rsiLab.snapshot() }
            val journal = withContext(Dispatchers.IO) {
                runCatching {
                    ActionJournal(
                        File(context.filesDir, "labforge/workspace/.girassol/device-journal.jsonl"),
                    ).recent(14)
                }.getOrDefault(emptyList())
            }
            state = state.copy(
                accessibility = DeviceBridge.enabledInSettings(context),
                toolsEnabled = settings.autonomyToolsEnabled,
                nightEnabled = settings.nightCycleEnabled,
                workLabel = SleepScheduler.stateLabel(work),
                lastRun = AutonomyStore.last(context),
                journal = journal,
                thermal = ThermalGate.status(context),
                ramGb = dev.labforge.app.service.MemoryGuard.availableGb(context),
                battery = BatteryReader.percent(context),
                charging = BatteryReader.isCharging(context),
                rsi = rsiSnapshot,
                rsiLab = rsiLabSnapshot,
            )
        }
    }

    fun selectRsiProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (rsiEngine.setActiveProfile(profileId)) {
                val snapshot = rsiEngine.snapshot()
                withContext(Dispatchers.Main) { state = state.copy(rsi = snapshot) }
            }
        }
    }

    fun proposeRsiSkill(title: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { rsiLab.proposeSkill(title, body, state.rsi?.recentRuns?.size ?: 0) }
            val snapshot = rsiLab.snapshot()
            withContext(Dispatchers.Main) { state = state.copy(rsiLab = snapshot) }
        }
    }

    fun approveRsiSkill(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            rsiLab.approveSkill(id)
            val snapshot = rsiLab.snapshot()
            withContext(Dispatchers.Main) { state = state.copy(rsiLab = snapshot) }
        }
    }

    fun recordRsiExperiment(name: String, baseline: Double, candidate: Double, samples: Int, holdout: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { rsiLab.recordExperiment(name, baseline, candidate, samples, holdout) }
            val snapshot = rsiLab.snapshot()
            withContext(Dispatchers.Main) { state = state.copy(rsiLab = snapshot) }
        }
    }

    fun rollbackRsiSkill() {
        viewModelScope.launch(Dispatchers.IO) {
            rsiLab.rollbackSkill()
            val snapshot = rsiLab.snapshot()
            withContext(Dispatchers.Main) { state = state.copy(rsiLab = snapshot) }
        }
    }

    fun setNightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            settingsRepo.update { it.copy(nightCycleEnabled = enabled) }
            if (enabled) SleepScheduler.schedule(context) else SleepScheduler.cancel(context)
            state = state.copy(nightEnabled = enabled)
            delay(300)
            refresh()
        }
    }

    fun setToolsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(autonomyToolsEnabled = enabled) }
            state = state.copy(toolsEnabled = enabled)
            delay(300)
            refresh()
        }
    }

    fun runNow() {
        viewModelScope.launch {
            state = state.copy(busy = true)
            SleepScheduler.runNow(getApplication())
            // The worker is short; give it a moment so the screen shows the new result.
            delay(1800)
            state = state.copy(busy = false)
            refresh()
        }
    }

    fun clearJournal() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) {
                runCatching {
                    ActionJournal(
                        File(context.filesDir, "labforge/workspace/.girassol/device-journal.jsonl"),
                    ).clear()
                }
            }
            state = state.copy(journal = emptyList())
        }
    }
}
