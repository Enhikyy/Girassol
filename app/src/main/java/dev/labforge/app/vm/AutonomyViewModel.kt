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
)

class AutonomyViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)

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
            )
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
