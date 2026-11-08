package dev.labforge.app.core.research

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class ResearchRepository(private val dir: File) {

    init { dir.mkdirs() }

    fun save(bundle: ResearchBundle): File {
        val slug = slug(bundle.theme)
        val file = File(dir, "$slug-${bundle.createdAt}.json")
        bundle.save(file)
        bundle.save(File(dir, "latest.json"))
        return file
    }

    fun list(): List<File> = dir.listFiles { f -> f.isFile && f.extension == "json" && f.name != "latest.json" }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun latest(): ResearchBundle? = File(dir, "latest.json").takeIf { it.exists() }?.let { ResearchBundle.load(it) }

    fun load(file: File): ResearchBundle? = ResearchBundle.load(file)

    fun retrieve(bundle: ResearchBundle, query: String, k: Int = 6): List<ResearchDoc> {
        val index = Bm25(bundle.docs.map { it.title + "\n" + it.snippet })
        val ids = index.top(query, k)
        return if (ids.isEmpty()) bundle.docs.take(k) else ids.map { bundle.docs[it] }
    }

    companion object {
        fun slug(theme: String): String = theme.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-').take(48).ifBlank { "tema" }
    }
}

/**
 * Autonomous research: query several free APIs in parallel, rank the material,
 * persist it and expose a local BM25 index for the rest of the app.
 */
class ResearchEngine(
    private val repo: ResearchRepository,
    private val keys: ResearchKeys = ResearchKeys(),
) {

    data class Stage(val label: String, val detail: String)

    suspend fun research(
        theme: String,
        onStage: (Stage) -> Unit = {},
    ): ResearchBundle = coroutineScope {
        val clean = theme.trim()
        require(clean.length >= 3) { "tema muito curto" }

        onStage(Stage("Pesquisando", (if (keys.brave.isNotBlank()) "Brave Search, " else "") + "GitHub, Wikipedia, OpenAlex, arXiv, Stack Overflow, DuckDuckGo"))
        val jobs = listOf(
            async { runCatching { FreeResearchApis.wikipedia(clean) }.getOrDefault(emptyList()) },
            async { runCatching { FreeResearchApis.wikipedia(clean, "en") }.getOrDefault(emptyList()) },
            async { runCatching { FreeResearchApis.openAlex(clean) }.getOrDefault(emptyList()) },
            async { runCatching { FreeResearchApis.arxiv(clean) }.getOrDefault(emptyList()) },
            async { runCatching { FreeResearchApis.stackExchange(clean) }.getOrDefault(emptyList()) },
            async { runCatching { FreeResearchApis.duckDuckGo(clean) }.getOrDefault(emptyList()) },
            async { runCatching { FreeResearchApis.semanticScholar(clean) }.getOrDefault(emptyList()) },
            async { runCatching { KeyedResearchApis.githubRepos(clean, keys.github) }.getOrDefault(emptyList()) },
            async { runCatching { KeyedResearchApis.brave(clean, keys.brave) }.getOrDefault(emptyList()) },
        )
        var docs = jobs.awaitAll().flatten()

        if (docs.size < 5) {
            onStage(Stage("Ampliando busca", "poucos resultados, tentando variações do tema"))
            val extra = listOf(
                async { runCatching { FreeResearchApis.wikipedia("$clean tutorial") }.getOrDefault(emptyList()) },
                async { runCatching { FreeResearchApis.duckDuckGo("$clean fundamentos") }.getOrDefault(emptyList()) },
                async { runCatching { FreeResearchApis.openAlex("$clean review") }.getOrDefault(emptyList()) },
            ).awaitAll().flatten()
            docs = docs + extra
        }

        onStage(Stage("Consolidando", "deduplicando e ranqueando ${docs.size} resultados"))
        val terms = Bm25.tokenize(clean)
        val deduped = docs
            .filter { it.title.isNotBlank() && it.url.isNotBlank() && it.snippet.length > 40 }
            .distinctBy { it.url.substringBefore('?').lowercase() }
            .distinctBy { it.title.lowercase().trim() }
            .map { d ->
                var s = d.score
                val title = d.title.lowercase()
                terms.forEach { if (title.contains(it)) s += 0.8 }
                if (d.snippet.length > 300) s += 0.2
                d.copy(score = s)
            }
            .sortedByDescending { it.score }
            .take(28)

        val summary = deduped.take(4).joinToString("\n") { "• ${it.title}: ${it.snippet.take(220)}" }
        val bundle = ResearchBundle(
            theme = clean,
            queries = listOf(clean, "$clean fundamentos", "$clean tutorial"),
            docs = deduped,
            autoSummary = summary,
        )

        onStage(Stage("Salvando", "${deduped.size} fontes no caderno de pesquisa"))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.save(bundle) }
        bundle
    }

    fun latest() = repo.latest()
}
