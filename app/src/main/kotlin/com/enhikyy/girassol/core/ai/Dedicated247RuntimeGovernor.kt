package com.enhikyy.girassol.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

data class InferenceBudget(
    val allowParallelSessions: Boolean,
    val maxOutputTokens: Int,
    val tickIntervalMs: Long,
    val thermalStatusLabel: String,
    val availableMemoryMb: Long,
    val contextLength: Int,
    val numThreads: Int,
    val batchSize: Int,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = true
)

/**
 * Orçamento dinâmico do runtime dedicado. Não reserva RAM artificialmente:
 * mede a memória disponível e reduz concorrência/contexto antes da pressão do LMK.
 */
class Dedicated247RuntimeGovernor(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun evaluateCurrentBudget(): InferenceBudget {
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val availableMb = memory.availMem / (1024L * 1024L)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }

        return when {
            thermal >= PowerManager.THERMAL_STATUS_SEVERE || memory.lowMemory || availableMb < 1200L ->
                InferenceBudget(
                    allowParallelSessions = false,
                    maxOutputTokens = 96,
                    tickIntervalMs = 500L,
                    thermalStatusLabel = "THROTTLED_SEVERE",
                    availableMemoryMb = availableMb,
                    contextLength = 2048,
                    numThreads = 2,
                    batchSize = 128,
                    flashAttention = false
                )
            thermal >= PowerManager.THERMAL_STATUS_MODERATE || availableMb < 2200L ->
                InferenceBudget(
                    allowParallelSessions = false,
                    maxOutputTokens = 160,
                    tickIntervalMs = 250L,
                    thermalStatusLabel = "BALANCED_MODERATE",
                    availableMemoryMb = availableMb,
                    contextLength = 3072,
                    numThreads = 3,
                    batchSize = 256,
                    flashAttention = true
                )
            else ->
                InferenceBudget(
                    allowParallelSessions = true,
                    maxOutputTokens = 256,
                    tickIntervalMs = 100L,
                    thermalStatusLabel = "OPTIMAL_ACTIVE_COOLING",
                    availableMemoryMb = availableMb,
                    contextLength = 4096,
                    numThreads = 4,
                    batchSize = 384,
                    flashAttention = true
                )
        }
    }
}
