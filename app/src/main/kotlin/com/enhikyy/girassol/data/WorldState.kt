package com.enhikyy.girassol.data

import com.enhikyy.girassol.cognitive.Intention
import com.enhikyy.girassol.cognitive.UtilityAI
import com.enhikyy.girassol.memory.Realm
import com.enhikyy.girassol.memory.SeveranceMemory
import com.enhikyy.girassol.sim.Simulation
import kotlin.math.cos
import kotlin.math.sin
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.core.integration.GeaDecisionBridge
import com.enhikyy.girassol.core.persistence.WorldCoreDatabase
import com.enhikyy.girassol.core.persistence.WorldCoreEventStore
import com.enhikyy.girassol.core.world.WorldActor
import com.enhikyy.girassol.core.world.DeterministicRng
import com.enhikyy.girassol.core.world.StreamSeedDeriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado observável do mundo: agentes, tick, tempo simulado e genoma.
 *
 * É a única fonte de verdade da simulação. As telas observam os `StateFlow` e o
 * [com.enhikyy.girassol.sim.GameLoop] é o único que chama [step].
 */
object WorldState {

    private const val WORLD_RADIUS = 120f
    private const val WORLD_SEED = 0x4749524153534f4cL

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents.asStateFlow()

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private val _simulatedMs = MutableStateFlow(0L)
    val simulatedMs: StateFlow<Long> = _simulatedMs.asStateFlow()

    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private val roles = listOf(
        "Explorador", "Pesquisador", "Artesão", "Guardião",
        "Diplomata", "Curador", "Engenheiro", "Filósofo",
    )

    /** Cria a população inicial. Idempotente. */
    fun initialize(population: Int = 8) {
        if (_agents.value.isNotEmpty()) return
        val restored = runCatching { WorldCoreEventStore.restoreLatestProjection() }
            .onFailure { AppLogger.log("ERROR", "WORLD", "Checkpoint/event log inválido; iniciando projeção limpa", it) }
            .getOrNull()
        if (restored != null) {
            _agents.value = restored.agents
            _tick.value = restored.tick
            _simulatedMs.value = restored.simulatedMs
            _generation.value = restored.generation
            _selectedId.value = restored.selectedId ?: restored.agents.firstOrNull()?.id
            AppLogger.log("INFO", "WORLD", "WorldCore restaurado: tick=${restored.tick}, agentes=${restored.agents.size}")
            return
        }
        val seeded = List(population) { index ->
            val id = index + 1L
            val rng = DeterministicRng(
                StreamSeedDeriver.derive(WORLD_SEED, id.toString(), 0L, "agent-initial-state")
            )
            Agent(
                id = id,
                name = "Agente ${index + 1}",
                role = roles[index % roles.size],
                drives = Drives(
                    energy = 0.45f + rng.nextDouble().toFloat() * 0.5f,
                    social = 0.2f + rng.nextDouble().toFloat() * 0.6f,
                    knowledge = 0.1f + rng.nextDouble().toFloat() * 0.5f,
                    focus = 0.3f + rng.nextDouble().toFloat() * 0.5f,
                ),
                isInnie = rng.nextInt(2) == 0,
                x = (rng.nextDouble().toFloat() - 0.5f) * WORLD_RADIUS * 2f,
                z = (rng.nextDouble().toFloat() - 0.5f) * WORLD_RADIUS * 2f,
            )
        }
        _agents.value = seeded
        _selectedId.value = seeded.firstOrNull()?.id
    }

    fun select(id: Long) {
        _selectedId.value = id
    }

    fun selected(): Agent? = _agents.value.firstOrNull { it.id == _selectedId.value }

    fun setInnie(agentId: Long, innie: Boolean) {
        _agents.value = _agents.value.map {
            if (it.id == agentId) it.copy(isInnie = innie) else it
        }
    }

    /** Substitui um único agente (usado pelas terapias do hospital). */
    fun replaceAgent(agent: Agent) {
        _agents.value = _agents.value.map {
            if (it.id == agent.id) agent else it
        }
    }

    /** Substitui a população inteira (usado pelo motor evolutivo). */
    fun replaceAll(agents: List<Agent>) {
        _agents.value = agents
        if (agents.none { it.id == _selectedId.value }) {
            _selectedId.value = agents.firstOrNull()?.id
        }
    }

    fun setGeneration(generation: Int) {
        _generation.value = generation
    }

    /**
     * Avança a simulação em [dtMs] de tempo simulado.
     *
     * Um passo faz, por agente: decide a intenção (Sistema 1), aplica o efeito
     * nos drives, move-se em direção ao alvo da intenção, e ocasionalmente grava
     * uma memória no reino correspondente à sua posição.
     */
    suspend fun step(dtMs: Long) {
        WorldActor.execute { stepOnWorldActor(dtMs) }
    }

    private fun stepOnWorldActor(dtMs: Long) {
        val current = _agents.value
        if (current.isEmpty()) return

        val dt = (dtMs / 1000f).coerceIn(0f, 30f)
        val nextTick = _tick.value + 1L

        // Estágio de propostas: todos os agentes decidem sobre o mesmo snapshot S_t.
        // Nenhuma mutação do mundo é publicada até que todas as propostas existam.
        val proposals = current.map { agent ->
            val neighbours = current.filter {
                it.id != agent.id &&
                    UtilityAI.distance(it, agent) <= UtilityAI.SOCIAL_RADIUS
            }
            val decision = GeaDecisionBridge.decide(agent, neighbours, nextTick, dt)
            Triple(agent, neighbours, decision)
        }

        // Estágio de commit/projeção: aplica as propostas calculadas no snapshot comum.
        val updated = proposals.map { (agent, neighbours, decision) ->
            val nextDrives = applyIntention(agent.drives, decision.intention, dt)
            val (nx, nz) = move(agent, decision.intention, dt, nextTick)

            val cooperation = UtilityAI.cooperationWeight(decision.intention)
            val fitness = agent.fitness +
                dt * (0.01f * cooperation + 0.02f * (1f - nextDrives.imbalance() / 2f))
            val realm = if (agent.isInnie) Realm.INNIE else Realm.OUTIE
            val health = diagnose(nextDrives, decision.intention)

            val memoryRng = DeterministicRng(
                StreamSeedDeriver.derive(WORLD_SEED, agent.id.toString(), nextTick, "memory-write")
            )
            if (memoryRng.nextDouble() < 0.02 * dt) {
                SeveranceMemory.remember(
                    agentId = agent.id,
                    realm = realm,
                    content = memoryFor(decision.intention, agent),
                    importance = decision.score.coerceIn(0.05f, 1f),
                    x = nx,
                    z = nz,
                    worldTick = nextTick,
                )
            }

            val reward = (0.6 * cooperation + 0.4 * (1f - nextDrives.imbalance() / 2f)).toDouble()
            GeaDecisionBridge.recordCommittedDecision(
                agent = agent,
                neighbours = neighbours,
                intention = decision.intention,
                reward = reward,
                tick = nextTick,
            )

            agent.copy(
                drives = nextDrives,
                x = nx,
                z = nz,
                fitness = fitness.coerceAtMost(10f),
                health = health,
                lastIntention = decision.intention.label,
            )
        }

        GeaDecisionBridge.finishTick(nextTick, updated)
        val nextSimulatedMs = _simulatedMs.value + dtMs
        val persisted = WorldCoreEventStore.commitTick(
            tick = nextTick,
            agents = updated,
            simulatedMs = nextSimulatedMs,
            generation = _generation.value,
            selectedId = _selectedId.value,
            worldSeed = WORLD_SEED,
        )
        check(persisted || !WorldCoreDatabase.isInitialized) {
            "WorldCore recusou o commit do tick $nextTick; projeção não publicada"
        }
        _agents.value = updated
        _tick.value = nextTick
        _simulatedMs.value = nextSimulatedMs
    }

    /** Aplica o efeito de uma intenção nos drives, em função de [dt] segundos. */
    private fun applyIntention(drives: Drives, intention: Intention, dt: Float): Drives =
        when (intention) {
            Intention.REST -> drives.plus(energy = 0.06f * dt, focus = -0.01f * dt)
            Intention.EAT -> drives.plus(energy = 0.10f * dt, focus = 0.005f * dt)
            Intention.SOCIALIZE -> drives.plus(social = 0.08f * dt, energy = -0.02f * dt)
            Intention.STUDY -> drives.plus(knowledge = 0.05f * dt, energy = -0.03f * dt, focus = 0.03f * dt)
            Intention.TEACH -> drives.plus(social = 0.04f * dt, knowledge = 0.02f * dt, energy = -0.02f * dt)
            Intention.HELP -> drives.plus(social = 0.07f * dt, energy = -0.03f * dt)
            Intention.EXPLORE -> drives.plus(knowledge = 0.03f * dt, energy = -0.04f * dt, social = 0.02f * dt)
        }

    /** Desloca o agente rumo ao alvo da intenção. */
    private fun move(agent: Agent, intention: Intention, dt: Float, tick: Long): Pair<Float, Float> {
        val (targetX, targetZ) = targetFor(intention, agent, tick)
        val dx = targetX - agent.x
        val dz = targetZ - agent.z
        val distance = kotlin.math.hypot(dx, dz)
        if (distance < 1f) return agent.x to agent.z

        val speed = when (intention) {
            Intention.EXPLORE -> 6f
            Intention.SOCIALIZE, Intention.HELP, Intention.TEACH -> 4.5f
            Intention.REST, Intention.EAT -> 3f
            Intention.STUDY -> 2.5f
        }
        val step = (speed * dt).coerceAtMost(distance)
        return (agent.x + dx / distance * step) to (agent.z + dz / distance * step)
    }

    /** Destino espacial associado a cada intenção. */
    private fun targetFor(intention: Intention, agent: Agent, tick: Long): Pair<Float, Float> = when (intention) {
        Intention.REST, Intention.EAT -> 0f to 0f
        Intention.STUDY, Intention.TEACH -> 12f to 12f
        Intention.SOCIALIZE, Intention.HELP -> 40f to 40f
        Intention.EXPLORE -> {
            val angle = agent.id * 0.7f + tick * 0.01f
            (cos(angle) * 100f) to (sin(angle) * 100f)
        }
    }

    /** Doenças genéticas detetadas a partir dos drives. */
    private fun diagnose(drives: Drives, intention: Intention): Health = when {
        drives.energy <= 0.02f -> Health.COLLAPSED
        drives.total() < 0.15f -> Health.DEGRADED
        drives.knowledge > 0.95f && drives.focus < 0.2f -> Health.HALLUCINATING
        intention == Intention.REST && drives.energy > 0.9f -> Health.DEGRADED
        else -> Health.HEALTHY
    }

    /** Frase de memória adequada à intenção. Substitui um LLM na ausência de modelo. */
    private fun memoryFor(intention: Intention, agent: Agent): String = when (intention) {
        Intention.REST -> "Recuperei energia perto da praça."
        Intention.EAT -> "Comi algo e senti o foco voltar."
        Intention.SOCIALIZE -> "Conversei com vizinhos sobre ${agent.role.lowercase()}."
        Intention.STUDY -> "Estudei um padrão novo e anotei o essencial."
        Intention.TEACH -> "Ensinei o que sei a quem estava ao lado."
        Intention.HELP -> "Ajudar alguém deixou o dia melhor."
        Intention.EXPLORE -> "Encontrei um canto do mundo que não conhecia."
    }

    /** Adaptador do mundo para o [Simulation] consumido pelo GameLoop. */
    val simulation: Simulation = object : Simulation {
        override suspend fun step(dtMs: Long) = WorldState.step(dtMs)
    }
}
