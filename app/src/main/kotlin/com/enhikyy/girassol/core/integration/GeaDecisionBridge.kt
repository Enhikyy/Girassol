package com.enhikyy.girassol.core.integration

import com.enhikyy.girassol.cognitive.Decision
import com.enhikyy.girassol.cognitive.Intention
import com.enhikyy.girassol.cognitive.UtilityAI
import com.enhikyy.girassol.core.cognition.ActiveInferencePolicyEvaluator
import com.enhikyy.girassol.core.cognition.AllostaticThermalCoupling
import com.enhikyy.girassol.core.cognition.GlobalWorkspaceStage
import com.enhikyy.girassol.core.cognition.HomeostaticVariable
import com.enhikyy.girassol.core.cognition.PerceptualSignal
import com.enhikyy.girassol.core.evolution.SemanticCollapseGuard
import com.enhikyy.girassol.core.memory.AssociativeMemoryNode
import com.enhikyy.girassol.core.memory.DomainBetaBelief
import com.enhikyy.girassol.core.memory.HybridMemoryRetriever
import com.enhikyy.girassol.core.memory.MemoryLayer
import com.enhikyy.girassol.core.memory.ConsolidatableMemory
import com.enhikyy.girassol.core.memory.HippocampalSleepConsolidator
import com.enhikyy.girassol.core.memory.SubjectiveOpinion
import com.enhikyy.girassol.core.social.DirectedSocialTie
import com.enhikyy.girassol.core.world.AffordanceAdvertisement
import com.enhikyy.girassol.core.world.DeterministicRng
import com.enhikyy.girassol.core.world.SpatialStigmergyAndAffordanceGrid
import com.enhikyy.girassol.core.world.StreamSeedDeriver
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.memory.SeveranceMemory
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapta os motores GEA definidos em Completo.md ao estado observável atual do mundo.
 *
 * Todas as decisões de um tick são calculadas a partir do mesmo snapshot antes
 * de WorldState publicar qualquer mutação. O WorldState continua sendo o
 * projection/UI facade durante a migração; este bridge concentra inferência,
 * memória, estigmergia, confiança social e pressão anti-colapso.
 */
object GeaDecisionBridge {
    private const val WORLD_SEED = 0x4749524153534f4cL
    private const val GRID_OFFSET = 128f
    private const val MEMORY_CONSOLIDATION_INTERVAL = 512L
    private const val PHEROMONE_UPDATE_INTERVAL = 10L

    private val workspace = GlobalWorkspaceStage(capacity = 3)
    private val memoryRetriever = HybridMemoryRetriever()
    private val sleepConsolidator = HippocampalSleepConsolidator(maxNodesPerAgent = 512)
    private val affordanceGrid = SpatialStigmergyAndAffordanceGrid(128, 128, 2f)
    private val socialTies = ConcurrentHashMap<Pair<Long, Long>, DirectedSocialTie>()
    private val domainBeliefs = ConcurrentHashMap<Pair<Long, String>, DomainBetaBelief>()
    private val actionCounts = ConcurrentHashMap<String, Int>()
    private val consolidationReports = ConcurrentHashMap<Long, String>()

    @Volatile
    private var thermalTier = AllostaticThermalCoupling.ThermalTier.NONE_OR_LIGHT

    @Volatile
    var neuralEscalationRequests: Long = 0L
        private set

    init {
        publishDefaultAffordances()
    }

    fun updateThermalTier(tier: AllostaticThermalCoupling.ThermalTier) {
        thermalTier = tier
    }

    fun latestConsolidationSummary(agentId: Long): String? = consolidationReports[agentId]

    /**
     * Decide com G(pi), histerese e softmax determinístico.
     * Não despacha o LLM diretamente: o CognitiveGate apenas contabiliza a
     * necessidade de escalonamento até o adaptador neural único ser ligado.
     */
    fun decide(
        agent: Agent,
        neighbours: List<Agent>,
        tick: Long,
        dtSeconds: Float
    ): Decision {
        val agentKey = agent.id.toString()
        val worldX = agent.x + GRID_OFFSET
        val worldZ = agent.z + GRID_OFFSET
        val reachable = affordanceGrid.queryReachable(
            agentId = agentKey,
            agentX = worldX,
            agentZ = worldZ,
            perceptionRadius = 72f,
            currentTick = tick
        )
        val reachableActions = reachable.mapTo(mutableSetOf()) { it.first.actionType }

        val memoryNodes = SeveranceMemory.allFor(agent.id).map { memory ->
            val tags = memory.content.lowercase()
                .split(Regex("[^a-z0-9áéíóúãõç]+"))
                .filter { it.length >= 3 }
                .toSet()
            AssociativeMemoryNode(
                id = memory.id,
                ownerAgentId = agentKey,
                layer = MemoryLayer.EPISODIC,
                content = memory.content,
                tags = tags,
                entityIds = emptySet(),
                salience = memory.importance.toDouble(),
                confidence = memory.importance.toDouble(),
                sourceEventId = null,
                sourceAgentId = null,
                createdTick = memory.worldTick
            )
        }
        val queryTags = setOf(agent.role.lowercase(), agent.lastIntention.lowercase())
            .filter { it.length >= 3 }
            .toSet()
        val recalled = memoryRetriever.retrieve(
            nodes = memoryNodes,
            currentTick = tick,
            queryTags = queryTags,
            queryEntities = emptySet(),
            activeWorkingMemoryIds = emptySet(),
            topK = 3
        )
        val memoryEvidence = recalled.firstOrNull()?.let {
            (it.salience * it.confidence).coerceIn(0.0, 1.0)
        } ?: 0.0

        val distressed = neighbours.count { it.drives.total() < 0.35f }
        val signals = listOf(
            PerceptualSignal("ENERGY", "déficit energético", agentKey, (1.0 - agent.drives.energy).toDouble()),
            PerceptualSignal("SOCIAL", "necessidade de vínculo", agentKey, (1.0 - agent.drives.social).toDouble()),
            PerceptualSignal("KNOWLEDGE", "incerteza epistêmica", agentKey, (1.0 - agent.drives.knowledge).toDouble()),
            PerceptualSignal("SOCIAL_DISTRESS", "agentes próximos em necessidade", null, (distressed / 4.0).coerceIn(0.0, 1.0)),
            PerceptualSignal("MEMORY", recalled.firstOrNull()?.content ?: "sem memória recuperada", null, memoryEvidence)
        )
        val winningSignals = workspace.broadcastWinningSignals(signals)
        val winningFocus = winningSignals.firstOrNull()?.sourceModule

        val currentIntention = Intention.entries.firstOrNull { it.label == agent.lastIntention }?.name
        val actionSpace = Intention.entries
        val priorEntropy = 0.6931471805599453
        val posteriorEntropy = (priorEntropy - memoryEvidence * 0.15).coerceAtLeast(0.0)

        val freeEnergies = actionSpace.map { intention ->
            val projected = projectDrives(agent, intention, dtSeconds)
            val pragmaticRisk = homeostaticCost(projected) +
                if (intention.name in setOf("REST", "EAT", "STUDY", "SOCIALIZE") &&
                    intention.name !in reachableActions
                ) 0.12 else 0.0

            val epistemicWeight = when (intention) {
                Intention.STUDY -> 0.78
                Intention.EXPLORE -> 0.92
                Intention.TEACH -> 0.35
                Intention.SOCIALIZE -> 0.28
                else -> 0.18
            }
            val epistemicPrior = priorEntropy + (1.0 - agent.drives.knowledge) * 0.25
            val epistemicPosterior = (posteriorEntropy + agent.drives.knowledge * 0.12)
                .coerceAtMost(epistemicPrior)
            var freeEnergy = ActiveInferencePolicyEvaluator.computeExpectedFreeEnergy(
                predictedPragmaticRisk = pragmaticRisk,
                priorEntropy = epistemicPrior,
                posteriorExpectedEntropy = epistemicPosterior,
                epistemicWeight = epistemicWeight
            )

            val cooperation = intention == Intention.HELP ||
                intention == Intention.TEACH || intention == Intention.SOCIALIZE
            if (cooperation && neighbours.isNotEmpty()) {
                val trust = neighbours.map { neighbour ->
                    socialTies[agent.id to neighbour.id]?.domainTrust("cooperation") ?: 0.5
                }.average()
                freeEnergy -= 0.08 * trust
            }

            val pheromone = affordanceGrid.samplePheromone(
                channel = "action:${intention.name}",
                worldX = worldX,
                worldZ = worldZ
            ).toDouble()
            freeEnergy -= (pheromone * 0.015).coerceIn(0.0, 0.08)

            val counts = actionCounts.toMap()
            if (counts.isNotEmpty()) {
                freeEnergy -= SemanticCollapseGuard.noveltyEpistemicBonus(
                    actionType = intention.name,
                    actionCounts = counts
                )
            }
            freeEnergy
        }

        val rng = DeterministicRng(
            StreamSeedDeriver.derive(WORLD_SEED, agentKey, tick, "active-inference-policy")
        )
        val selectedIndex = ActiveInferencePolicyEvaluator.applyHysteresisAndSoftmax(
            policyKeys = actionSpace.map(Intention::name),
            expectedFreeEnergies = freeEnergies,
            currentIntentionKey = currentIntention,
            switchCost = 0.12,
            precisionGamma = 3.2,
            uniformSample = rng.nextDouble()
        ).coerceAtLeast(0)
        val selected = actionSpace[selectedIndex]
        val sortedUtilities = freeEnergies.map { -it }.sortedDescending()
        val topUtility = sortedUtilities.firstOrNull() ?: 0.0
        val secondUtility = sortedUtilities.getOrNull(1) ?: topUtility

        val socialNegotiation = selected == Intention.HELP ||
            selected == Intention.TEACH || selected == Intention.SOCIALIZE
        val escalate = AllostaticThermalCoupling.shouldInvokeLocalLlm(
            topActionUtility = topUtility,
            secondActionUtility = secondUtility,
            tieThreshold = 0.08,
            minUrgency = 0.45,
            consecutiveHtnFailures = 0,
            socialNegotiationActive = socialNegotiation,
            tier = thermalTier,
            tsetlinReflexConfidence = 0.0
        )
        if (escalate) neuralEscalationRequests += 1L

        val baseline = UtilityAI.decide(agent, neighbours)
        val score = (-freeEnergies[selectedIndex]).toFloat()
        val rationale = buildString {
            append("GEA G(pi)=")
            append("%.4f".format(freeEnergies[selectedIndex]))
            append("; baseline=")
            append(baseline.rationale)
            append("; workspace=")
            append(winningFocus ?: "none")
            append("; memories=")
            append(recalled.size)
            append("; reachable=")
            append(reachable.size)
            append("; thermal=")
            append(thermalTier.name)
            if (escalate) append("; neural-escalation=pending-adapter")
        }
        return Decision(selected, score, rationale)
    }

    /** Registra resultados somente depois de todas as propostas do tick terem sido calculadas. */
    fun recordCommittedDecision(
        agent: Agent,
        neighbours: List<Agent>,
        intention: Intention,
        reward: Double,
        tick: Long
    ) {
        actionCounts.compute(intention.name) { _, old -> (old ?: 0) + 1 }
        val positive = reward > 0.45
        val belief = domainBeliefs.computeIfAbsent(agent.id to "cooperation") {
            DomainBetaBelief(
                subject = agent.id.toString(),
                predicate = "cooperation-is-beneficial",
                domain = "cooperation"
            )
        }
        belief.updateFromDirectObservation(positive, tick)
        val subjective = SubjectiveOpinion.fromBeta(belief.alpha, belief.beta)
        val cooperative = intention == Intention.HELP ||
            intention == Intention.TEACH || intention == Intention.SOCIALIZE

        neighbours.forEach { neighbour ->
            val tie = socialTies.computeIfAbsent(agent.id to neighbour.id) {
                DirectedSocialTie(agent.id.toString(), neighbour.id.toString())
            }
            tie.familiarity = (tie.familiarity + 0.002).coerceAtMost(1.0)
            if (cooperative) {
                tie.affinity = (tie.affinity + if (positive) 0.015 else -0.005).coerceIn(-1.0, 1.0)
                tie.recordTrustOutcome("cooperation", positive)
            }
            // A incerteza subjetiva permanece explícita; não se converte confiança
            // social em observação direta sem evidência de resultado.
            tie.opinionValue = subjective.projectedProbability()
        }

        val channel = "action:${intention.name}"
        affordanceGrid.depositPheromone(
            channel = channel,
            worldX = agent.x + GRID_OFFSET,
            worldZ = agent.z + GRID_OFFSET,
            amount = if (cooperative) 0.55f else 0.25f
        )
    }

    fun finishTick(tick: Long, agents: List<Agent>) {
        affordanceGrid.purgeExpiredReservations(tick)
        if (tick % PHEROMONE_UPDATE_INTERVAL == 0L) {
            affordanceGrid.stepDiffusionAndEvaporation()
        }
        if (tick > 0L && tick % MEMORY_CONSOLIDATION_INTERVAL == 0L) {
            consolidateMemories(agents, tick)
        }
        if (tick % 256L == 0L && tick > 0L) {
            // Mantém a janela de diversidade limitada e comparável entre épocas.
            actionCounts.clear()
        }
    }

    private fun consolidateMemories(agents: List<Agent>, tick: Long) {
        agents.forEach { agent ->
            val memories = SeveranceMemory.allFor(agent.id)
            val candidates = memories.map { memory ->
                ConsolidatableMemory(
                    id = memory.id,
                    content = memory.content,
                    tags = memory.content.lowercase()
                        .split(Regex("[^a-z0-9áéíóúãõç]+"))
                        .filter { it.length >= 3 }
                        .toSet(),
                    isSemantic = false,
                    qUtility = memory.importance.toDouble(),
                    tdPredictionError = 0.0,
                    accessTicks = emptyList(),
                    createdTick = memory.worldTick,
                    verifiedByWorldCore = false
                )
            }
            val report = sleepConsolidator.consolidate(candidates, tick)
            if (report.prunedNodeIds.isNotEmpty()) {
                SeveranceMemory.removeByIds(report.prunedNodeIds.toSet())
            }
            consolidationReports[agent.id] =
                "tick=$tick replay=${report.replayedNodeIds.size} pruned=${report.prunedNodeIds.size} " +
                    "promoted=${report.promotedSemanticSummaries.size}"
        }
    }

    private fun projectDrives(agent: Agent, intention: Intention, dt: Float) = when (intention) {
        Intention.REST -> agent.drives.plus(energy = 0.06f * dt, focus = -0.01f * dt)
        Intention.EAT -> agent.drives.plus(energy = 0.10f * dt, focus = 0.005f * dt)
        Intention.SOCIALIZE -> agent.drives.plus(social = 0.08f * dt, energy = -0.02f * dt)
        Intention.STUDY -> agent.drives.plus(knowledge = 0.05f * dt, energy = -0.03f * dt, focus = 0.03f * dt)
        Intention.TEACH -> agent.drives.plus(social = 0.04f * dt, knowledge = 0.02f * dt, energy = -0.02f * dt)
        Intention.HELP -> agent.drives.plus(social = 0.07f * dt, energy = -0.03f * dt)
        Intention.EXPLORE -> agent.drives.plus(knowledge = 0.03f * dt, energy = -0.04f * dt, social = 0.02f * dt)
    }

    private fun homeostaticCost(drives: com.enhikyy.girassol.data.Drives): Double =
        HomeostaticVariable("energy", drives.energy.toDouble(), 0.70, 1.2, 0.7).driveCost() +
            HomeostaticVariable("social", drives.social.toDouble(), 0.60, 0.65, 0.35).driveCost() +
            HomeostaticVariable("knowledge", drives.knowledge.toDouble(), 0.50, 0.45, 0.2).driveCost() +
            HomeostaticVariable("focus", drives.focus.toDouble(), 0.65, 0.25, 0.4).driveCost()

    private fun publishDefaultAffordances() {
        val definitions = listOf(
            Triple("rest-area", "REST", 128f, 128f),
            Triple("canteen", "EAT", 112f, 128f),
            Triple("study-hall", "STUDY", 140f, 140f),
            Triple("commons-plaza", "SOCIALIZE", 160f, 160f),
            Triple("help-desk", "HELP", 150f, 140f),
            Triple("teaching-hall", "TEACH", 136f, 144f)
        )
        definitions.forEach { (id, action, x, z) ->
            affordanceGrid.publishAdvertisement(
                AffordanceAdvertisement(
                    sourceEntityId = id,
                    actionType = action,
                    x = x,
                    z = z,
                    broadcastRadius = 180f,
                    advertisedRelief = mapOf("homeostasis" to 0.1),
                    maxConcurrentUsers = 8
                )
            )
        }
    }
}
