package com.enhikyy.girassol.core

import com.enhikyy.girassol.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Estado global de execução autónoma. */
enum class RunState { RUNNING, PAUSED }

/**
 * Interruptor mestre da autonomia.
 *
 * Todo loop longo (simulação, treino, LLM, coder) chama [awaitRunning] antes de
 * cada iteração. Sem isto, um loop descontrolado consome o aparelho ao ponto de
 * não ser possível abrir a app para o corrigir.
 */
object AutonomyController {
    private val _state = MutableStateFlow(RunState.RUNNING)
    val state: StateFlow<RunState> = _state.asStateFlow()

    fun pause(reason: String) {
        _state.value = RunState.PAUSED
        AppLogger.log("WARN", "AUTONOMY", "PAUSADO: $reason")
    }

    fun resume(reason: String) {
        _state.value = RunState.RUNNING
        AppLogger.log("INFO", "AUTONOMY", "RETOMADO: $reason")
    }

    /** Alterna o estado; devolve o estado resultante. */
    fun toggle(reason: String): RunState {
        if (_state.value == RunState.RUNNING) pause(reason) else resume(reason)
        return _state.value
    }

    /**
     * Suspende a corrotina enquanto o estado for [RunState.PAUSED].
     *
     * É cancelável: se o scope for cancelado durante a pausa, a corrotina
     * termina em vez de ficar pendurada.
     */
    suspend fun awaitRunning() {
        if (_state.value == RunState.RUNNING) return
        _state.first { it == RunState.RUNNING }
    }
}
