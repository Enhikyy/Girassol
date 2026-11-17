package com.enhikyy.girassol.hospital

import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Health
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uma ocorrência clínica registada no hospital. */
data class Diagnosis(
    val agentId: Long,
    val agentName: String,
    val illness: Health,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Terapias aplicáveis a um agente doente. */
enum class Therapy(val label: String) {
    REGROUNDING("Re-grounding"),
    ROLLBACK("Rollback de Pesos"),
}

/**
 * AI Hospital.
 *
 * Deteta três classes de doença e aplica a terapia correspondente:
 *
 * - **Alucinação** — conhecimento altíssimo com foco baixo: o agente "sabe"
 *   coisas que não verificou. Terapia: re-grounding (repõe factos, baixa a
 *   confiança para um nível defensável).
 * - **Degradação de pesos** — drives desequilibrados. Terapia: rollback para o
 *   último estado saudável guardado.
 * - **Colapso** — energia a zero. Terapia: reanimação completa.
 *
 * Cada agente tem sempre um *snapshot* saudável em [checkpoints], que é o alvo
 * do rollback. Sem snapshot, o rollback é recusado — nunca se inventa um estado.
 */
object AIHospital {

    /** Último estado saudável conhecido de cada agente, alvo do rollback. */
    private val checkpoints = HashMap<Long, Agent>()

    private val _diagnoses = MutableStateFlow<List<Diagnosis>>(emptyList())
    val diagnoses: StateFlow<List<Diagnosis>> = _diagnoses.asStateFlow()

    private val _therapiesApplied = MutableStateFlow(0)
    val therapiesApplied: StateFlow<Int> = _therapiesApplied.asStateFlow()

    /** Guarda um snapshot saudável de um agente. */
    fun checkpoint(agent: Agent) {
        if (agent.health == Health.HEALTHY) checkpoints[agent.id] = agent
    }

    /**
     * Avalia um agente e, se estiver doente, registra o diagnóstico.
     * Devolve o diagnóstico criado, ou `null` se estiver saudável.
     */
    fun evaluate(agent: Agent): Diagnosis? {
        if (agent.health == Health.HEALTHY) {
            checkpoint(agent)
            return null
        }
        val existing = _diagnoses.value.any { it.agentId == agent.id && it.illness == agent.health }
        if (existing) return null

        val detail = when (agent.health) {
            Health.HALLUCINATING ->
                "Conhecimento ${pct(agent.drives.knowledge)} com foco ${pct(agent.drives.focus)}: respostas sem verificação."
            Health.DEGRADED ->
                "Drives desequilibrados (total ${pct(agent.drives.total())}): deriva de comportamento."
            Health.COLLAPSED ->
                "Energia a ${pct(agent.drives.energy)}: agente inoperante."
            Health.HEALTHY -> ""
        }
        val diagnosis = Diagnosis(agent.id, agent.name, agent.health, detail)
        _diagnoses.value = (_diagnoses.value + diagnosis).takeLast(100)
        return diagnosis
    }

    /**
     * Aplica uma terapia e devolve o agente tratado.
     *
     * @return o agente curado, ou `null` se a terapia não for aplicável
     *         (por exemplo, rollback sem checkpoint).
     */
    fun treat(agent: Agent, therapy: Therapy): Agent? {
        val cured = when (therapy) {
            Therapy.REGROUNDING -> reground(agent)
            Therapy.ROLLBACK -> rollback(agent)
        } ?: return null

        _therapiesApplied.value = _therapiesApplied.value + 1
        _diagnoses.value = _diagnoses.value.filterNot { it.agentId == agent.id }
        checkpoint(cured)
        return cured
    }

    /** Re-grounding: corrige a confiança excessiva e reinjeta foco. */
    private fun reground(agent: Agent): Agent = agent.copy(
        drives = agent.drives.copy(
            knowledge = (agent.drives.knowledge * 0.85f),
            focus = (agent.drives.focus + 0.35f).coerceAtMost(1f),
        ),
        health = Health.HEALTHY,
    )

    /** Rollback: volta ao último snapshot saudável, mantendo identidade e posição. */
    private fun rollback(agent: Agent): Agent? {
        val snapshot = checkpoints[agent.id] ?: return null
        return agent.copy(
            drives = snapshot.drives,
            health = Health.HEALTHY,
            fitness = snapshot.fitness,
        )
    }

    /** Indica se um rollback é possível para este agente. */
    fun canRollback(agentId: Long): Boolean = checkpoints.containsKey(agentId)

    fun clear() {
        checkpoints.clear()
        _diagnoses.value = emptyList()
        _therapiesApplied.value = 0
    }

    private fun pct(value: Float): String = "${(value * 100).toInt()}%"
}
