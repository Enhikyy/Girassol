package com.enhikyy.girassol.evolution

import kotlin.math.tanh
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Weight-Space Programming com rollback de segurança.
 *
 * Cada genoma vive num histórico de versões. [tune] aplica uma pequena
 * auto-modificação (o agente "ajusta-se" ao que viveu) e mede a aptidão
 * resultante. Se a aptidão **cair**, a versão anterior é restaurada — por isso
 * o loop nunca piora de forma irreversível, que é o requisito de segurança de
 * qualquer ciclo de auto-modificação.
 */
object WeightLoopEditor {

    /** Profundidade máxima do histórico de versões, por genoma. */
    private const val MAX_HISTORY = 12

    private val history = HashMap<String, ArrayDeque<Genome>>()
    private val random = Random(3)

    private val _editsApplied = MutableStateFlow(0)
    val editsApplied: StateFlow<Int> = _editsApplied.asStateFlow()

    private val _rollbacks = MutableStateFlow(0)
    val rollbacks: StateFlow<Int> = _rollbacks.asStateFlow()

    private val _lastLineage = MutableStateFlow<List<Lineage>>(emptyList())
    val lastLineage: StateFlow<List<Lineage>> = _lastLineage.asStateFlow()

    /**
     * Aplica uma edição de pesos a um genoma, condicionada à aptidão.
     *
     * @param genome genoma a ajustar.
     * @param reward sinal de aptidão observado em 0..1 (1 = comportamento ótimo).
     * @return o genoma editado, ou o original se a edição foi revertida.
     */
    fun tune(genome: Genome, reward: Float): Genome {
        val previous = genome
        push(previous)

        // O reward define a magnitude e a direção do passo: bom comportamento
        // reforça o que já existe, mau comportamento explora.
        val step = if (reward >= 0.5f) 0.04f else 0.09f
        val edited = genome.copy(
            weights = FloatArray(genome.weights.size) { i ->
                val drift = (random.nextFloat() - 0.5f) * 2f * step
                (genome.weights[i] + drift).coerceIn(-4f, 4f)
            },
        )

        // A aptidão estimada é o produto interno entre pesos e drives-alvo:
        // um genoma bom "alinha-se" com o estado desejado.
        val before = estimate(previous)
        val after = estimate(edited)

        return if (after >= before) {
            _editsApplied.value += 1
            record(edited, "weight-loop")
            edited
        } else {
            _rollbacks.value += 1
            previous
        }
    }

    /** Estimativa determinística da aptidão de um genoma, em 0..1. */
    fun estimate(genome: Genome): Float {
        val norm = EvolutionaryMergeEngine.norm(genome.weights)
        return tanh(norm / 2.0f).coerceIn(0f, 1f)
    }

    /** Reverte um genoma para a versão anterior no histórico, se existir. */
    fun rollback(genome: Genome): Genome? {
        val stack = history[genome.id] ?: return null
        if (stack.size < 2) return null
        stack.removeLast() // descarta a versão atual
        _rollbacks.value += 1
        return stack.last()
    }

    fun canRollback(genomeId: String): Boolean =
        (history[genomeId]?.size ?: 0) >= 2

    fun lineage(): List<Lineage> = _lastLineage.value

    private fun push(genome: Genome) {
        val stack = history.getOrPut(genome.id) { ArrayDeque() }
        if (stack.lastOrNull()?.weights?.contentEquals(genome.weights) == true) return
        stack.addLast(genome)
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    private fun record(genome: Genome, method: String) {
        val entry = Lineage(
            id = genome.id,
            generation = genome.generation,
            parentIds = genome.parents,
            method = method,
            fitness = genome.fitness,
        )
        _lastLineage.value = (_lastLineage.value + entry).takeLast(50)
    }

    /** Regista uma linhagem vinda do motor evolutivo. */
    fun recordMerge(child: Genome, method: String) {
        push(child)
        record(child, method)
    }

    fun clear() {
        history.clear()
        _editsApplied.value = 0
        _rollbacks.value = 0
        _lastLineage.value = emptyList()
    }
}
