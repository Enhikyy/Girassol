package com.enhikyy.girassol.llm

import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.MyApplication
import com.enhikyy.girassol.ai.NativeLlmEngine
import com.enhikyy.girassol.ai.GenerationException

/**
 * Backend de inferência que usa o NativeLlmEngine (100% nativo via JNI).
 *
 * Conecta o PromptQueue ao motor LLM nativo, permitindo geração real
 * de texto quando um modelo GGUF está carregado.
 */
class NativeLlmBackend : InferenceBackend {

    private val tag = "NativeLlmBackend"

    override val isModelLoaded: Boolean
        get() = MyApplication.getInstance().llmEngine.let { 
            it is NativeLlmEngine && it.isLoaded 
        }

    override suspend fun generate(request: PromptRequest): String {
        val engine = MyApplication.getInstance().llmEngine

        if (engine !is NativeLlmEngine || !engine.isLoaded) {
            AppLogger.log("WARN", tag, "Modelo não carregado, usando fallback heurístico")
            return LocalHeuristicBackend().generate(request)
        }

        return try {
            val fullPrompt = buildString {
                request.systemPrompt?.let {
                    append("Sistema: ").append(it).append("\n\n")
                }
                append("Usuário: ").append(request.prompt)
                append("\n\nAssistente: ")
            }

            val result = StringBuilder()
            engine.generate(fullPrompt) { token ->
                result.append(token)
            }

            result.toString()
        } catch (e: GenerationException) {
            AppLogger.log("ERROR", tag, "Falha na geração: ${e.message}", e)
            "Erro na geração: ${e.message}"
        } catch (e: Exception) {
            AppLogger.log("ERROR", tag, "Exceção inesperada: ${e.message}", e)
            "Erro inesperado: ${e.message}"
        }
    }
}

/**
 * Backend híbrido: usa NativeLlmEngine quando disponível,
 * fallback para LocalHeuristicBackend quando não há modelo.
 */
class HybridLlmBackend : InferenceBackend {

    private val nativeBackend = NativeLlmBackend()
    private val heuristicBackend = LocalHeuristicBackend()

    override val isModelLoaded: Boolean
        get() = nativeBackend.isModelLoaded

    override suspend fun generate(request: PromptRequest): String {
        return if (nativeBackend.isModelLoaded) {
            nativeBackend.generate(request)
        } else {
            heuristicBackend.generate(request)
        }
    }
}
