package com.enhikyy.girassol

import android.app.Application
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
        // Motor real será instanciado quando o usuário carregar um modelo
    }

    /** Cria/instancia o motor real e substitui o nulo. */
    fun ensureRealEngine() {
        if (llmEngine !is LlamatikEngine) {
            llmEngine = LlamatikEngine()
        }
    }
}