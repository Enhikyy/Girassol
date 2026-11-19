package com.enhikyy.girassol.plugins

import com.enhikyy.girassol.research.BraveConfig
import com.enhikyy.girassol.research.ResearchService

/** Plugin de pesquisa web: agrega todas as fontes keyless + Brave (com chave). */
object ResearchPlugin : GirassolPlugin {
    override val name: String = "Pesquisa Web"

    override suspend fun status(): String =
        if (BraveConfig.apiKey.isNullOrBlank()) "7 fontes sem chave"
        else "7 fontes + Brave"

    override suspend fun execute(query: String): String? {
        val hits = ResearchService.search(query, maxHits = 6)
        if (hits.isEmpty()) return null
        val fontes = hits.map { it.source }.distinct().joinToString(", ")
        val bloco = hits.joinToString("\n") { "• " + it.title + " — " + it.snippet.take(140) }
        return "Contexto de pesquisa (fontes: " + fontes + "):\n" + bloco
    }
}
