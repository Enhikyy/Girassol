package dev.labforge.app.core.autonomy

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import dev.labforge.app.service.MemoryGuard

/** Thin wrapper around [PowerManager] thermal status, with labels for the UI. */
object ThermalGate {

    const val UNSUPPORTED = -1

    fun status(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return UNSUPPORTED
        return runCatching {
            context.getSystemService(PowerManager::class.java).currentThermalStatus
        }.getOrDefault(UNSUPPORTED)
    }

    fun label(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "leve"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderada"
        PowerManager.THERMAL_STATUS_SEVERE -> "severa"
        PowerManager.THERMAL_STATUS_CRITICAL -> "crítica"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergência"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "desligamento"
        else -> "desconhecida"
    }

    /** Moderate and above means the SoC is already throttling; do not start training. */
    @SuppressLint("InlinedApi")
    fun allowsWork(status: Int): Boolean =
        status == UNSUPPORTED || status < PowerManager.THERMAL_STATUS_MODERATE
}

data class NightPlan(
    val run: Boolean,
    val reason: String,
    val availableGb: Double,
    val batteryPct: Int,
    val charging: Boolean,
    val thermal: Int,
) {
    fun describe(): String = buildString {
        append("RAM ").append("%.1f GB".format(availableGb))
        append(" · bateria ").append(if (batteryPct < 0) "?" else "$batteryPct%")
        if (charging) append(" (carregando)")
        append(" · térmica ").append(ThermalGate.label(thermal))
    }
}

/**
 * Decides whether a night cycle may run. Pure policy, separated from WorkManager so the
 * rules are unit-testable and visible in one place.
 */
object CycleBudget {

    fun evaluate(context: Context): NightPlan {
        val available = MemoryGuard.availableGb(context)
        val battery = BatteryReader.percent(context)
        val charging = BatteryReader.isCharging(context)
        val thermal = ThermalGate.status(context)

        val reason = when {
            !ThermalGate.allowsWork(thermal) ->
                "adiado: térmica ${ThermalGate.label(thermal)}"
            battery in 0..14 && !charging ->
                "adiado: bateria $battery% e sem carregador"
            available < 0.35 ->
                "adiado: só %.2f GB livres".format(available)
            else -> "ok"
        }

        return NightPlan(
            run = reason == "ok",
            reason = reason,
            availableGb = available,
            batteryPct = battery,
            charging = charging,
            thermal = thermal,
        )
    }
}

object BatteryReader {
    private const val UNKNOWN = -1

    fun percent(context: Context): Int = runCatching {
        val manager = context.getSystemService(android.os.BatteryManager::class.java)
        manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(UNKNOWN).coerceIn(UNKNOWN, 100)

    fun isCharging(context: Context): Boolean = runCatching {
        context.getSystemService(android.os.BatteryManager::class.java).isCharging
    }.getOrDefault(false)
}
