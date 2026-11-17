package com.enhikyy.girassol

import android.app.Application
import com.enhikyy.girassol.ai.LlmEngine
import com.enhikyy.girassol.ai.NativeLlmEngine
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
        AppLogger.log("INFO", "APP", "Girassol iniciado | minSdk=31 targetSdk=34 arm64-v8a")
    }

    /** Cria/instancia o motor real e substitui o nulo. */
    fun ensureRealEngine() {
        if (llmEngine !is NativeLlmEngine) {
            llmEngine = NativeLlmEngine()
            AppLogger.log("INFO", "APP", "NativeLlmEngine instanciado (100% nativo via JNI)")
        }
    }
}
