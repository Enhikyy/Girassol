package dev.labforge.app.core.research

import kotlin.math.ln

/** Tiny BM25 for local retrieval over saved research — no embeddings, no network. */
class Bm25(private val documents: List<String>) {

    private val tokenized: List<List<String>> = documents.map { tokenize(it) }
    private val avgLen: Double = tokenized.map { it.size }.average().takeIf { it > 0 } ?: 1.0
    private val df: Map<String, Int> = buildMap {
        val seen = HashMap<String, Int>()
        tokenized.forEach { toks -> toks.toSet().forEach { t -> seen[t] = (seen[t] ?: 0) + 1 } }
        putAll(seen)
    }
    private val n = documents.size.coerceAtLeast(1)

    fun top(query: String, k: Int = 5): List<Int> {
        val q = tokenize(query).distinct()
        if (q.isEmpty() || documents.isEmpty()) return emptyList()
        return documents.indices.map { i -> i to score(q, i) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }
    }

    private fun score(query: List<String>, doc: Int): Double {
        val toks = tokenized[doc]
        if (toks.isEmpty()) return 0.0
        val tf = toks.groupingBy { it }.eachCount()
        var s = 0.0
        for (term in query) {
            val f = tf[term] ?: continue
            val dfi = df[term] ?: 0
            val idf = ln(1.0 + (n - dfi + 0.5) / (dfi + 0.5))
            val denom = f + 1.2 * (1 - 0.75 + 0.75 * toks.size / avgLen)
            s += idf * (f * 2.2) / denom
        }
        return s
    }

    companion object {
        private val stop = setOf(
            "a", "o", "e", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas",
            "para", "por", "com", "sem", "um", "uma", "uns", "umas", "que", "se", "como",
            "the", "and", "or", "of", "to", "in", "on", "for", "with", "is", "are", "was",
            "what", "how", "why", "this", "that", "it", "be", "as", "at", "by", "from",
        )

        fun tokenize(text: String): List<String> = text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && it !in stop }
    }
}
