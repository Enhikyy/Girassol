package com.enhikyy.girassol

import android.app.Application
import android.os.Build
import android.os.PowerManager
import com.enhikyy.girassol.core.cognition.AllostaticThermalCoupling
import com.enhikyy.girassol.core.integration.GeaDecisionBridge
import com.enhikyy.girassol.ai.LlamatikEngine
import com.enhikyy.girassol.ai.LlmEngine
import com.enhikyy.girassol.ai.NullLlmEngine

class MyApplication : Application() {

    companion object {
        @Volatile
        private var INSTANCE: MyApplication? = null

        fun getInstance(): MyApplication = INSTANCE!!
    }

    /** Instância única do motor LLM (inicia como nula). */
    var llmEngine: LlmEngine = NullLlmEngine()
        private set

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        AppLogger.init(this)
        DebugFlags.logState()
        registerThermalTelemetry()
        // Motor real será instanciado quando o usuário carregar um modelo
    }

    /**
     * Liga a telemetria térmica Android ao custo metabólico do CognitiveGate.
     * A simulação recebe somente a categoria térmica, nunca consulta hardware
     * dentro da função determinística de decisão.
     */
    private fun registerThermalTelemetry() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            val tier = when {
                status >= PowerManager.THERMAL_STATUS_SEVERE ->
                    AllostaticThermalCoupling.ThermalTier.SEVERE_OR_CRITICAL
                status >= PowerManager.THERMAL_STATUS_MODERATE ->
                    AllostaticThermalCoupling.ThermalTier.MODERATE
                else -> AllostaticThermalCoupling.ThermalTier.NONE_OR_LIGHT
            }
            GeaDecisionBridge.updateThermalTier(tier)
            AppLogger.log("INFO", "THERMAL", "CognitiveGate tier=$tier status=$status")
        }
        powerManager.addThermalStatusListener(mainExecutor, listener)
        listener.onThermalStatusChanged(powerManager.currentThermalStatus)
    }

    /** Cria/instancia o motor real e substitui o nulo. */
    fun ensureRealEngine() {
        if (llmEngine !is LlamatikEngine) {
            llmEngine = LlamatikEngine()
        }
    }
}