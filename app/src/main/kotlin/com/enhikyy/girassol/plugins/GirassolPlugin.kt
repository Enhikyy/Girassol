package com.enhikyy.girassol.plugins

/** Contrato dos plugins nativos que ampliam chat, Mundo e Faculdade. */
interface GirassolPlugin {
    val name: String
    suspend fun status(): String
    suspend fun execute(query: String): String?
}

/** Registro central de extensões nativas; todas as execuções são explícitas. */
object PluginRegistry {
    val all: List<GirassolPlugin> by lazy {
        listOf(ResearchPlugin, WeatherPlugin, KnowledgePlugin)
    }

    fun statusLine(): String = all.joinToString(" · ") { it.name }

    suspend fun runAll(query: String): List<Pair<String, String>> =
        all.mapNotNull { plugin ->
            runCatching { plugin.execute(query) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { plugin.name to it }
        }
}
