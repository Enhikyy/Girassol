package com.enhikyy.girassol.sim

import com.enhikyy.girassol.AppLogger

/** Qualquer sistema que avança em passos lógicos de tamanho fixo. */
interface Simulation {
    /** Avança a simulação em [dtMs] milissegundos simulados. */
    suspend fun step(dtMs: Long)
}

/**
 * Persistência do instante de parede da última iteração.
 *
 * Sem isto, o processo morto pelo sistema faria a simulação recomeçar de zero
 * (ou saltar horas) no arranque seguinte.
 */
interface WallClockStore {
    fun load(): Long?
    fun save(wallMs: Long)
}

/**
 * Loop baseado em Delta Time com passo fixo e catch-up.
 *
 * Converte o tempo de parede decorrido em passos lógicos de [stepMs]. Se o atraso
 * ultrapassar [maxCatchUpSteps], o restante é aplicado em um único passo grande,
 * para que a CPU não fique presa recalculando horas de simulação de uma vez.
 *
 * @param store onde guardar o instante da última iteração (opcional).
 * @param persistEveryN iterações entre gravações; evita I/O por tick.
 */
class GameLoop(
    private val simulation: Simulation,
    private val stepMs: Long = 100L,
    private val maxCatchUpSteps: Int = 6_000,
    private val store: WallClockStore? = null,
    private val persistEveryN: Int = 100,
) {
    init {
        require(stepMs > 0) { "stepMs deve ser positivo" }
        require(maxCatchUpSteps > 0) { "maxCatchUpSteps deve ser positivo" }
        require(persistEveryN > 0) { "persistEveryN deve ser positivo" }
    }

    /**
     * Resultado de um [advance].
     * @property steps passos fixos executados, mais 1 se houve passo grande.
     * @property simulatedMs tempo simulado total aplicado.
     * @property coarse true quando o atraso excedeu o limite e foi aplicado em um passo grande.
     */
    data class Report(val steps: Int, val simulatedMs: Long, val coarse: Boolean) {
        companion object {
            val NONE = Report(steps = 0, simulatedMs = 0L, coarse = false)
        }
    }

    private var lastWallMs: Long? = null
    private var accumulatorMs: Long = 0L
    private var iterations = 0

    /**
     * Define o instante de referência. O próximo [advance] mede o tempo
     * decorrido a partir dele.
     *
     * Se [wallMs] não for dado, recupera o último instante gravado no [store];
     * na ausência de ambos usa o relógio atual.
     */
    fun reset(wallMs: Long? = null) {
        val restored = wallMs ?: store?.load()
        val now = System.currentTimeMillis()
        lastWallMs = restored ?: now
        accumulatorMs = 0L
        iterations = 0
        if (restored != null) {
            val gap = (now - restored).coerceAtLeast(0L)
            AppLogger.log("INFO", "LOOP", "GameLoop retomado; atraso=${gap}ms")
        }
    }

    /** Calcula quanto tempo passou desde a última chamada e executa os passos devidos. */
    suspend fun advance(nowWallMs: Long): Report {
        val last = lastWallMs
        lastWallMs = nowWallMs
        if (last == null) return Report.NONE

        accumulatorMs += (nowWallMs - last).coerceAtLeast(0L)
        val due = accumulatorMs / stepMs
        val fixedSteps = minOf(due, maxCatchUpSteps.toLong()).toInt()

        repeat(fixedSteps) { simulation.step(stepMs) }
        accumulatorMs -= fixedSteps.toLong() * stepMs

        var steps = fixedSteps
        var simulated = fixedSteps.toLong() * stepMs
        var coarse = false

        // Atraso enorme (processo morto, aparelho desligado): aplica o resto de
        // uma vez em vez de executar milhares de passos finos.
        if (due > maxCatchUpSteps && accumulatorMs > 0L) {
            val rest = accumulatorMs
            simulation.step(rest)
            steps += 1
            simulated += rest
            accumulatorMs = 0L
            coarse = true
            AppLogger.log("WARN", "LOOP", "catch-up grosso: ${rest}ms num passo")
        }

        if (++iterations % persistEveryN == 0) {
            store?.save(nowWallMs)
        }
        return Report(steps, simulated, coarse)
    }
}
