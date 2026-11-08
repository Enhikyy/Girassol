package dev.labforge.app.core.sleep

import dev.labforge.app.core.research.ResearchBundle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A cycle publishes only a completed, nonempty research dossier. */
internal suspend fun runSleepCycle(
    research: suspend () -> ResearchBundle,
    enrich: suspend (ResearchBundle) -> ResearchBundle,
    publish: suspend (ResearchBundle) -> Unit,
) {
    val found = research()
    check(found.docs.isNotEmpty()) { "nenhuma fonte encontrada" }
    val enriched = enrich(found)
    check(enriched.docs.isNotEmpty()) { "nenhuma fonte incorporada" }
    currentCoroutineContext().ensureActive()
    publish(enriched)
}
