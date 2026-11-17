package com.enhikyy.girassol.cognitive

import com.enhikyy.girassol.data.Agent
import kotlin.math.abs

/** Ações que o Sistema 1 pode escolher num tick. */
enum class Intention(val label: String) {
    REST("Descansar"),
    EAT("Comer"),
    SOCIALIZE("Socializar"),
    STUDY("Estudar"),
    TEACH("Ensinar"),
    EXPLORE("Explorar"),
    HELP("Ajudar"),
}

/** Resultado de uma decisão do Sistema 1, com o porquê auditável. */
data class Decision(
    val intention: Intention,
    val score: Float,
    val rationale: String,
)

/**
 * Sistema 1 — instinto.
 *
 * Utility AI puro em Kotlin: cada intenção recebe uma pontuação a partir dos
 * drives do agente (energia, social, conhecimento) e do contexto espacial.
 *
 * A sociedade é **coletivista**: ações cooperativas ([Intention.HELP],
 * [Intention.TEACH], [Intention.SOCIALIZE]) recebem um bónus explícito de
 * [COOPERATION_BONUS], enquanto decisões puramente egoístas (comer sozinho com
 * o grupo em necessidade) são penalizadas. O objetivo é que o bónus domine o
 * equilíbrio sempre que houver alguém por perto em pior estado.
 */
object UtilityAI {

    /** Bónus aplicado a qualquer intenção cooperativa. */
    const val COOPERATION_BONUS = 0.22f

    /** Penalidade por ignorar um vizinho em necessidade. */
    const val SELFISH_PENALTY = 0.30f

    /**
     * Escolhe a intenção de maior utilidade.
     *
     * @param agent agente que decide.
     * @param neighbours agentes no mesmo raio social.
     */
    fun decide(agent: Agent, neighbours: List<Agent>): Decision {
        val inNeed = neighbours.filter { it.drives.total() < 0.35f }
        val someoneWorse = inNeed.any { it.drives.energy < agent.drives.energy }
        val crowd = neighbours.size

        val scores = LinkedHashMap<Intention, Double>()

        // Sobrevivência: quanto mais baixa a energia, mais urgente dormir/comer.
        scores[Intention.REST] = (1.0 - agent.drives.energy) * 1.15
        scores[Intention.EAT] = (1.0 - agent.drives.energy) * 0.95

        // Conhecimento: estudar compensa quando a energia permite.
        scores[Intention.STUDY] =
            (1.0 - agent.drives.knowledge) * 0.85 * agent.drives.energy

        // Social: quanto mais isolado, mais peso — e cresce com a multidão.
        val loneliness = 1.0 - agent.drives.social
        scores[Intention.SOCIALIZE] = loneliness * (0.6 + 0.08 * crowd)

        // Ensinar: conhecimento alto + alguém para receber.
        scores[Intention.TEACH] =
            if (crowd > 0) agent.drives.knowledge * 0.7 + COOPERATION_BONUS else 0.05

        // Ajudar: só existe se alguém está pior.
        scores[Intention.HELP] =
            if (someoneWorse) 0.55 + COOPERATION_BONUS * 1.5 else 0.0

        scores[Intention.EXPLORE] = 0.35 * agent.drives.energy * (1.0 - agent.drives.knowledge)

        // Penaliza egoísmo: comer/descansar enquanto há gente em necessidade
        // perde valor, porque o grupo importa mais que o indivíduo.
        if (inNeed.isNotEmpty()) {
            scores[Intention.EAT] = (scores[Intention.EAT] ?: 0.0) - SELFISH_PENALTY
            scores[Intention.REST] = (scores[Intention.REST] ?: 0.0) - SELFISH_PENALTY * 0.5
        }

        // O foco do agente amplifica a intenção que ele já tende a escolher.
        val best = scores.maxByOrNull { it.value } ?: return Decision(Intention.REST, 0f, "sem drives")
        val boosted = best.value * (1.0 + 0.25 * agent.drives.focus)

        return Decision(
            intention = best.key,
            score = boosted.toFloat(),
            rationale = rationale(best.key, agent, inNeed.size, crowd),
        )
    }

    /** Explicação legível da decisão, para o log do Dashboard. */
    private fun rationale(
        intention: Intention,
        agent: Agent,
        inNeed: Int,
        crowd: Int,
    ): String = when (intention) {
        Intention.REST -> "energia em ${pct(agent.drives.energy)}"
        Intention.EAT -> "fome acumulada"
        Intention.STUDY -> "conhecimento em ${pct(agent.drives.knowledge)}"
        Intention.SOCIALIZE -> "isolamento com $crowd vizinhos"
        Intention.TEACH -> "a partilhar conhecimento com $crowd"
        Intention.HELP -> "$inNeed vizinhos em necessidade"
        Intention.EXPLORE -> "à procura de estímulo novo"
    }

    private fun pct(value: Float): String = "${(value * 100).toInt()}%"

    /**
     * Peso de cooperação de uma ação, em 0..1.
     *
     * Usado pelo motor evolutivo como parte da aptidão: agentes que cooperam
     * acumulam mais aptidão ao longo da vida.
     */
    fun cooperationWeight(intention: Intention): Float = when (intention) {
        Intention.HELP -> 1.0f
        Intention.TEACH -> 0.85f
        Intention.SOCIALIZE -> 0.6f
        Intention.EXPLORE -> 0.3f
        Intention.STUDY -> 0.2f
        Intention.EAT -> 0.1f
        Intention.REST -> 0.05f
    }

    /** Distância a partir da qual dois agentes deixam de se considerar vizinhos. */
    const val SOCIAL_RADIUS = 35f

    /** Distância espacial entre dois agentes no plano do mundo. */
    fun distance(a: Agent, b: Agent): Float = abs(a.x - b.x) + abs(a.z - b.z)
}
