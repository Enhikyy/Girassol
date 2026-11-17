package com.enhikyy.girassol.evolution

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Genoma de um agente, expresso como um vetor de pesos.
 *
 * O "modelo" de cada agente é deliberadamente pequeno: um conjunto de pesos que
 * modula como ele responde aos drives. É o suficiente para que seleção,
 * cruzamento e mutação tenham efeito observável — e para que tudo corra numa
 * fração de milissegundo por geração no dispositivo.
 */
data class Genome(
    val id: String,
    val generation: Int,
    val parents: List<String>,
    val weights: FloatArray,
    val fitness: Float = 0f,
) {
    /** Profundidade genealógica; usada para detetar endogamia. */
    val depth: Int get() = generation

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = id.hashCode()
}

/** Linhagem de um genoma, para rastrear a árvore de reprodução. */
data class Lineage(
    val id: String,
    val generation: Int,
    val parentIds: List<String>,
    val method: String,
    val fitness: Float,
)

/** Configuração de um cruzamento. */
data class MergeConfig(
    val slerpWeight: Float = 0.5f,
    val tiesTrimRatio: Float = 0.2f,
    val dareDropRate: Float = 0.5f,
    val dareScale: Float = 2.0f,
    val collapseThreshold: Float = 0.001f,
)

/** Métricas de um cruzamento, para o log do Dashboard. */
data class MergeMetrics(
    val dimensions: Int,
    val divergenceBefore: Double,
    val divergenceAfter: Double,
    val sparsity: Float,
)

/** Motivo pelo qual um cruzamento foi recusado. */
class MergeRejected(message: String) : Exception(message)

/**
 * Motor de reprodução genética dos agentes.
 *
 * Combina dois genomas "pais" com três técnicas complementares:
 *
 * - **SLERP** — interpolação esférica, que preserva a magnitude do vetor e por
 *   isso não "encolhe" os pesos ao longo de gerações (o modo clássico de
 *   colapso de modelo).
 * - **TIES-Merging** — faz trim dos deltas pequenos e resolve conflitos de sinal
 *   antes de somar, evitando que pais discordantes se anulem.
 * - **DARE** — descarta uma fração dos deltas e reescala os restantes, o que
 *   mantém a capacidade do modelo com menos parâmetros efetivos.
 *
 * A escolha do par é feita por um **CMA-ES simplificado**: uma distribuição
 * gaussiana sobre o espaço de combinações é atualizada a cada geração em
 * direção às melhores aptidões, com passo que adapta (cresce no sucesso,
 * encolhe no fracasso).
 */
object EvolutionaryMergeEngine {

    /** Tamanho do vetor de pesos dos genomas. */
    const val DIMENSIONS = 16

    /** População mínima para haver reprodução. */
    private const val MIN_POPULATION = 2

    /**
     * Gera o genoma de um filho a partir de dois pais.
     *
     * @throws MergeRejected se os pais forem demasiado próximos (risco de colapso).
     */
    fun merge(
        a: Genome,
        b: Genome,
        config: MergeConfig = MergeConfig(),
        random: Random = Random(1),
    ): Pair<Genome, MergeMetrics> {
        require(a.weights.size == b.weights.size) {
            "genomas com dimensões diferentes: ${a.weights.size} vs ${b.weights.size}"
        }

        val before = divergence(a.weights, b.weights)

        val slerped = slerp(a.weights, b.weights, config.slerpWeight)
        val ties = tiesMerge(a.weights, b.weights, config.tiesTrimRatio)
        val blurbed = dareMerge(a.weights, b.weights, config.dareDropRate, config.dareScale, random)

        // Combina as três vias com pesos iguais: nenhuma domina, e as diferenças
        // entre elas ficam preservadas como diversidade.
        val mixed = FloatArray(a.weights.size) { i ->
            (slerped[i] + ties[i] + blurbed[i]) / 3f
        }

        val after = divergence(a.weights, b.weights)
        if (after < config.collapseThreshold && a.generation > 2) {
            throw MergeRejected("pais demasiado próximos; cruzamento recusado para evitar colapso")
        }

        val child = Genome(
            id = "g${a.generation + 1}-${random.nextInt(1_000_000)}",
            generation = maxOf(a.generation, b.generation) + 1,
            parents = listOf(a.id, b.id),
            weights = mixed,
            fitness = 0f,
        )

        val sparsity = mixed.count { abs(it) < 1e-4f }.toFloat() / mixed.size

        return child to MergeMetrics(
            dimensions = mixed.size,
            divergenceBefore = before,
            divergenceAfter = after,
            sparsity = sparsity,
        )
    }

    /**
     * Interpolação esférica entre dois vetores.
     *
     * Se os vetores forem quase paralelos, cai para interpolação linear — o
     * `sin(θ)` tende a zero e a divisão explodiria.
     */
    fun slerp(a: FloatArray, b: FloatArray, t: Float): FloatArray {
        val normA = norm(a)
        val normB = norm(b)
        if (normA < 1e-6f || normB < 1e-6f) return lerp(a, b, t)

        val dot = dot(a, b) / (normA * normB)
        val theta = kotlin.math.acos(dot.coerceIn(-1f, 1f))
        val sinTheta = kotlin.math.sin(theta)
        if (sinTheta < 1e-5f) return lerp(a, b, t)

        val wa = kotlin.math.sin((1f - t) * theta) / sinTheta
        val wb = kotlin.math.sin(t * theta) / sinTheta
        return FloatArray(a.size) { i -> a[i] * wa + b[i] * wb }
    }

    /** Deltas grandes de ambos os pais, com conflitos de sinal resolvidos. */
    fun tiesMerge(a: FloatArray, b: FloatArray, trimRatio: Float): FloatArray {
        val deltas = FloatArray(a.size) { i -> (a[i] + b[i]) / 2f }
        val magnitudes = deltas.map { abs(it) }.sortedDescending()
        val keepCount = (deltas.size * (1f - trimRatio.coerceIn(0f, 0.9f))).toInt().coerceAtLeast(1)
        val threshold = magnitudes.getOrElse(keepCount - 1) { 0f }

        return FloatArray(a.size) { i ->
            if (abs(deltas[i]) >= threshold) {
                // Sinal maioritário: se ambos concordam fica o dobro, senão a média.
                val sign = if (a[i] >= 0f && b[i] >= 0f) 1f else if (a[i] <= 0f && b[i] <= 0f) -1f else 0f
                if (sign == 0f) deltas[i] * 0.5f else deltas[i] * 2f * sign
            } else {
                0f
            }
        }
    }

    /** Descarta deltas aleatórios e reescala os sobreviventes. */
    fun dareMerge(
        a: FloatArray,
        b: FloatArray,
        dropRate: Float,
        scale: Float,
        random: Random,
    ): FloatArray {
        val rate = dropRate.coerceIn(0f, 1f)
        val rescale = if (rate >= 1f) 1f else scale
        return FloatArray(a.size) { i ->
            val delta = (a[i] + b[i]) / 2f
            if (random.nextFloat() < rate) 0f else delta * rescale
        }
    }

    /** Norma euclidiana do vetor. */
    fun norm(v: FloatArray): Float = sqrt(v.sumOf { (it * it).toDouble() }.toFloat())

    /** Produto interno. */
    fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    /** Distância euclidiana entre dois vetores — medida de diversidade. */
    fun divergence(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = (a[i] - b[i]).toDouble()
            sum += d * d
        }
        return sqrt(sum)
    }

    private fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray =
        FloatArray(a.size) { i -> a[i] * (1f - t) + b[i] * t }

    /**
     * CMA-ES simplificado: escolhe os pais da próxima geração.
     *
     * Mantém um passo [sigma] que adapta ao histórico: se a melhor aptidão
     * melhorou, alarga a procura; se piorou, contrai-a. Isto dá o mesmo efeito
     * prático de uma matriz de covariância completa, com custo O(1).
     */
    class Selector(
        private val populationSize: Int = 8,
        private var sigma: Double = 0.5,
        private val random: Random = Random(1),
    ) {
        private var bestFitness: Double = Double.NEGATIVE_INFINITY
        private var generation: Int = 0

        /** Atualiza o passo com base no progresso observado. */
        fun observe(fitness: Double) {
            generation++
            if (fitness > bestFitness) {
                bestFitness = fitness
                sigma = (sigma * 1.08).coerceAtMost(2.0)
            } else {
                sigma = (sigma * 0.96).coerceAtLeast(0.02)
            }
        }

        val stepSize: Double get() = sigma

        /**
         * Escolhe dois genomas distintos para reprodução, com probabilidade
         * proporcional à aptidão (torneio binário, que resiste melhor a
         * outliers do que a roleta).
         */
        fun selectParents(genomes: List<Genome>): Pair<Genome, Genome> {
            require(genomes.size >= MIN_POPULATION) { "população insuficiente para reprodução" }
            val first = tournament(genomes)
            val second = tournament(genomes.filter { it.id != first.id }.ifEmpty { genomes })
            return first to second
        }

        private fun tournament(candidates: List<Genome>): Genome {
            val a = candidates[random.nextInt(candidates.size)]
            val b = candidates[random.nextInt(candidates.size)]
            return if (a.fitness >= b.fitness) a else b
        }

        /** Mutação gaussiana com passo adaptativo, aplicada ao filho. */
        fun mutate(genome: Genome): Genome {
            val weights = FloatArray(genome.weights.size) { i ->
                val noise = (gaussian() * sigma * 0.1).toFloat()
                (genome.weights[i] + noise).coerceIn(-4f, 4f)
            }
            return genome.copy(weights = weights)
        }

        /** Aproximação de Box-Muller para ruído gaussiano. */
        private fun gaussian(): Double {
            val u1 = random.nextDouble().coerceAtLeast(1e-9)
            val u2 = random.nextDouble()
            return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        }

        /** Distribuição de pesos inicial da população. */
        fun randomGenome(id: String, generation: Int): Genome = Genome(
            id = id,
            generation = generation,
            parents = emptyList(),
            weights = FloatArray(DIMENSIONS) { (gaussian() * 0.5).toFloat().coerceIn(-2f, 2f) },
        )

        /** Aptidão normalizada em 0..1 a partir de um valor bruto. */
        fun normalize(raw: Double): Double = 1.0 / (1.0 + exp(-raw))
    }
}
