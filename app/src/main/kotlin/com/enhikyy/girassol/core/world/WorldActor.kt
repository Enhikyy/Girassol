package com.enhikyy.girassol.core.world

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.EmptyCoroutineContext
import java.util.concurrent.Executors

/**
 * Escritor lógico único do Girassol.
 *
 * Todo commit de tick é executado em uma thread dedicada. Inferência, UI e
 * renderização podem calcular/ler em paralelo, mas a projeção canônica e a
 * gravação SQLite entram por este ator.
 */
object WorldActor : Closeable {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Girassol-WorldActor").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    suspend fun <T> execute(block: () -> T): T = withContext(dispatcher) {
        block()
    }

    /** Enfileira mutações de UI/serviços no mesmo escritor serial do mundo. */
    fun dispatch(block: () -> Unit) {
        dispatcher.dispatch(EmptyCoroutineContext, Runnable(block))
    }

    override fun close() {
        dispatcher.close()
    }
}
