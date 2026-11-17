package com.enhikyy.girassol.ai

/**
 * Interface abstrata para motores de inferência LLM.
 *
 * Implementações:
 * - [NativeLlmEngine]: Motor 100% nativo via JNI (recomendado)
 * - [NullLlmEngine]: No-op para quando não há modelo carregado
 */
interface LlmEngine {
    /** Modelo está carregado e pronto. */
    val isLoaded: Boolean

    /** Caminho do modelo carregado (para logs). */
    val modelPath: String?

    /**
     * Carrega um modelo GGUF do caminho especificado.
     * @return true se carregou com sucesso
     * @throws ModelLoadException se falhar
     */
    suspend fun load(modelPath: String): Boolean

    /**
     * Gera texto a partir do prompt, chamando onToken para cada token.
     * @return true se gerou com sucesso
     * @throws GenerationException se falhar
     */
    suspend fun generate(prompt: String, onToken: (String) -> Unit): Boolean

    /** Libera recursos do modelo. */
    fun close()
}

/** Exceção de falha de carregamento do modelo. */
class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Exceção de falha de geração. */
class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Implementação nula (no-op) para quando não há modelo. */
class NullLlmEngine : LlmEngine {
    override val isLoaded: Boolean get() = false
    override val modelPath: String? get() = null
    override suspend fun load(modelPath: String): Boolean = false
    override suspend fun generate(prompt: String, onToken: (String) -> Unit): Boolean = false
    override fun close() {}
}
