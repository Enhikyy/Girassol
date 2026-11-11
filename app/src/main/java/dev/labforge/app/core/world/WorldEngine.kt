package dev.labforge.app.core.world

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class AgentLod { DETAILED, SIMPLIFIED, STATISTICAL }

/**
 * Deterministic, UI-independent world simulation. Time only moves through [step] or
 * [catchUp]; there is no claim of Android background execution.
 */
class WorldEngine(initialState: WorldState) {
    var state: WorldState = initialState
        private set

    @Synchronized
    fun replaceState(next: WorldState) {
        validate(next)
        state = next
    }

    @Synchronized
    fun step(
        minutes: Int = 10,
        wallClockMs: Long = state.lastWallClockMs,
        detailedIds: Set<String> = emptySet(),
        visibleIds: Set<String> = emptySet(),
    ): WorldState {
        require(minutes in 1..360)
        val previous = state
        val newElapsed = previous.elapsedMinutes + minutes
        val random = WorldRandom(previous.rngState)
        var agents = previous.agents.mapIndexed { index, agent ->
            val lod = lodFor(agent.id, index, detailedIds, visibleIds)
            val sinceUpdate = newElapsed - agent.lastUpdatedMinute
            val due = when (lod) {
                AgentLod.DETAILED -> true
                AgentLod.SIMPLIFIED -> sinceUpdate >= 60
                AgentLod.STATISTICAL -> sinceUpdate >= 360
            }
            if (due) simulateAgent(agent, previous, newElapsed, sinceUpdate.coerceAtLeast(minutes.toLong()), random)
            else agent
        }
        agents = updateRelationships(agents, minutes)

        var next = previous.copy(
            tick = previous.tick + 1,
            elapsedMinutes = newElapsed,
            lastWallClockMs = maxOf(previous.lastWallClockMs, wallClockMs),
            rngState = random.state,
            agents = agents,
        )
        val oldDay = previous.elapsedMinutes / WORLD_MINUTES_PER_DAY
        val newDay = newElapsed / WORLD_MINUTES_PER_DAY
        for (day in oldDay until newDay) {
            next = processEconomyDay(next, day + 1, random)
        }
        state = next.copy(rngState = random.state)
        return state
    }

    /** Compensated time is capped at seven simulated days per resume. */
    @Synchronized
    fun catchUp(nowWallClockMs: Long): Long {
        if (!state.parameters.offlineCatchUpEnabled || nowWallClockMs <= state.lastWallClockMs) {
            state = state.copy(lastWallClockMs = maxOf(state.lastWallClockMs, nowWallClockMs))
            return 0
        }
        val wallSeconds = (nowWallClockMs - state.lastWallClockMs) / 1_000L
        val totalMinutes = wallSeconds.coerceIn(0L, MAX_OFFLINE_MINUTES)
        if (totalMinutes < 1) {
            state = state.copy(lastWallClockMs = nowWallClockMs)
            return 0
        }
        var remaining = totalMinutes
        while (remaining > 0) {
            val chunk = remaining.coerceAtMost(60L).toInt()
            // A direct internal step avoids recursively taking the monitor and uses far LOD.
            step(chunk, wallClockMs = nowWallClockMs - remaining * 1_000L)
            remaining -= chunk
        }
        val event = WorldEvent(
            id = state.nextEventId,
            simMinute = state.elapsedMinutes,
            kind = WorldEventKind.TEMPORAL,
            title = "Tempo compensado",
            description = "$totalMinutes minutos simulados na retomada. O Android não executou a simulação em segundo plano.",
        )
        state = state.withEvent(event).copy(lastWallClockMs = nowWallClockMs)
        return totalMinutes
    }

    @Synchronized
    fun setPlayback(paused: Boolean, timeScale: Int): WorldState {
        require(timeScale in PLAYBACK_SPEEDS)
        state = state.copy(paused = paused, timeScale = timeScale)
        return state
    }

    @Synchronized
    fun setParameters(demandMultiplier: Float, researchMultiplier: Float, catchUpEnabled: Boolean): WorldState {
        state = state.copy(parameters = SimulationParameters(
            demandMultiplier = demandMultiplier.coerceIn(0.25f, 3f),
            researchMultiplier = researchMultiplier.coerceIn(0.25f, 3f),
            offlineCatchUpEnabled = catchUpEnabled,
        ))
        return state
    }

    @Synchronized
    fun addDialogueMemory(agentId: String, speaker: String, text: String, isUser: Boolean): WorldState {
        val agent = state.agent(agentId) ?: error("Habitante não encontrado: $agentId")
        val updated = agent.withMemory(
            simMinute = state.elapsedMinutes,
            kind = if (isUser) "user_dialogue" else "agent_dialogue",
            text = "$speaker: $text",
            sentiment = if (isUser) 0f else 0.1f,
            relatedAgentId = "user",
        )
        val event = if (isUser) WorldEvent(
            id = state.nextEventId, simMinute = state.elapsedMinutes, kind = WorldEventKind.DIALOGUE,
            title = "Conversa com ${agent.name}", description = text.take(300), agentIds = listOf(agentId),
        ) else null
        state = state.copy(agents = state.agents.map { if (it.id == agentId) updated else it })
        if (event != null) state = state.withEvent(event)
        return state
    }

    @Synchronized
    fun applyHappening(type: HappeningType, description: String): WorldState {
        val cleanDescription = description.trim().take(500).ifBlank { defaultHappeningDescription(type) }
        var next = state
        next = when (type) {
            HappeningType.FESTIVAL -> next.copy(agents = next.agents.map { agent ->
                agent.copy(needs = agent.needs.copy(
                    social = (agent.needs.social + 24f).coerceAtMost(100f),
                    motivation = (agent.needs.motivation + 12f).coerceAtMost(100f),
                    satisfaction = (agent.needs.satisfaction + 18f).coerceAtMost(100f),
                    stress = (agent.needs.stress - 15f).coerceAtLeast(0f),
                ))
            })
            HappeningType.BLACKOUT -> next.copy(
                buildings = next.buildings.map { it.copy(condition = (it.condition - 0.08f).coerceAtLeast(0.2f)) },
                agents = next.agents.map { agent ->
                    agent.copy(needs = agent.needs.copy(
                        motivation = (agent.needs.motivation - 10f).coerceAtLeast(0f),
                        stress = (agent.needs.stress + 13f).coerceAtMost(100f),
                    ))
                },
            )
            HappeningType.RESEARCH_GRANT -> next.copy(companies = next.companies.map { company ->
                if (company.bankrupt) company else company.copy(
                    cash = company.cash + 8_000.0,
                    technology = (company.technology + 0.015f).coerceAtMost(1f),
                )
            })
            HappeningType.MARKET_BOOM -> next.copy(parameters = next.parameters.copy(
                demandMultiplier = (next.parameters.demandMultiplier * 1.35f).coerceAtMost(3f),
            ))
        }
        val event = WorldEvent(
            id = next.nextEventId, simMinute = next.elapsedMinutes, kind = WorldEventKind.USER,
            title = when (type) {
                HappeningType.FESTIVAL -> "Festival em Aurora"
                HappeningType.BLACKOUT -> "Interrupção de infraestrutura"
                HappeningType.RESEARCH_GRANT -> "Fomento à pesquisa"
                HappeningType.MARKET_BOOM -> "Expansão de mercado"
            },
            description = cleanDescription,
            agentIds = if (type == HappeningType.FESTIVAL || type == HappeningType.BLACKOUT) next.agents.map { it.id } else emptyList(),
            companyIds = if (type == HappeningType.RESEARCH_GRANT) next.activeCompanies.map { it.id } else emptyList(),
        )
        state = next.withEvent(event)
        return state
    }

    @Synchronized
    fun foundCompany(name: String, strategy: CompanyStrategy): WorldState {
        val cleanName = name.trim().take(60)
        require(cleanName.length >= 3) { "Informe um nome com pelo menos 3 caracteres." }
        require(state.companies.none { it.name.equals(cleanName, true) }) { "Já existe uma empresa com esse nome." }
        require(state.playerCapital >= FOUNDING_COST) { "Capital insuficiente: são necessários ₡25.000." }
        val companyId = "company_${state.nextCompanyId}"
        val buildingId = "building_company_${state.nextCompanyId}"
        val index = state.companies.size
        val angle = index * 2.399963f
        val radius = 38f + (index % 3) * 5f
        val building = BuildingState(
            id = buildingId, cityId = state.cities.first().id, name = "Sede $cleanName",
            kind = BuildingKind.HEADQUARTERS,
            position = Vector2State(cos(angle) * radius, sin(angle) * radius),
            height = 12f, footprintX = 8f, footprintZ = 8f, companyId = companyId,
            technology = 0.08f, accentColor = WorldFactory.companyAccent(index),
        )
        val company = CompanyState(
            id = companyId, name = cleanName, strategy = strategy, cash = FOUNDING_COST,
            buildingId = buildingId,
            departments = listOf(
                DepartmentState("dep_${companyId}_product", "Produto", 5_000.0, "Primeiro produto"),
                DepartmentState("dep_${companyId}_research", "Pesquisa", 3_000.0, "Pesquisa aplicada"),
            ),
            products = listOf(ProductState(
                id = "product_${state.nextCompanyId}_1", companyId = companyId,
                name = "Projeto Semente", stage = ProductStage.DEVELOPMENT,
                development = 0.05f, quality = 0.18f, innovation = 0.35f, price = 110.0,
            )),
            technology = 0.08f, foundedOnDay = state.day, userOwned = true,
        )
        val event = WorldEvent(
            id = state.nextEventId, simMinute = state.elapsedMinutes, kind = WorldEventKind.ECONOMY,
            title = "$cleanName foi fundada", description = "Capital inicial de ₡25.000 transferido da reserva do usuário.",
            companyIds = listOf(companyId), buildingIds = listOf(buildingId),
        )
        state = state.copy(
            playerCapital = state.playerCapital - FOUNDING_COST,
            companies = state.companies + company,
            buildings = state.buildings + building,
            nextCompanyId = state.nextCompanyId + 1,
            nextBuildingId = state.nextBuildingId + 1,
        ).withEvent(event)
        return state
    }

    @Synchronized
    fun syncKnowledge(count: Int, latestTitle: String): WorldState {
        val safeCount = count.coerceAtLeast(0)
        val integration = state.integration
        if (safeCount == integration.knowledgeCount && latestTitle == integration.lastKnowledgeTitle) return state
        val gained = (safeCount - integration.knowledgeCount).coerceAtLeast(0)
        val research = state.building("building_research")
        var next = state.copy(
            integration = integration.copy(knowledgeCount = safeCount, lastKnowledgeTitle = latestTitle.take(200)),
            buildings = state.buildings.map { building ->
                if (building.id == research?.id) building.copy(technology = (building.technology + gained * 0.006f).coerceAtMost(1f))
                else building
            },
            agents = state.agents.map { agent ->
                if (agent.profession == Profession.RESEARCHER && gained > 0) {
                    val topic = latestTitle.ifBlank { "Girassol" }
                    agent.copy(knowledge = agent.knowledge + (topic to ((agent.knowledge[topic] ?: 0f) + 0.05f).coerceAtMost(1f)))
                        .withMemory(state.elapsedMinutes, "girassol_knowledge", "O Girassol integrou: ${latestTitle.ifBlank { "nova lição" }}", 0.3f)
                } else agent
            },
        )
        if (gained > 0) {
            next = next.withEvent(WorldEvent(
                id = next.nextEventId, simMinute = next.elapsedMinutes, kind = WorldEventKind.INTEGRATION,
                title = "Conhecimento chegou ao Instituto",
                description = "${if (gained == 1) "Uma nova lição" else "$gained novas lições"} do caderno real do Girassol: ${latestTitle.ifBlank { "sem título" }}.",
                buildingIds = listOfNotNull(research?.id),
            ))
        }
        state = next
        return state
    }

    @Synchronized
    fun syncJob(kind: String, title: String, progress: Float, running: Boolean, summary: String, finishedAt: Long): WorldState {
        val previous = state.integration
        val isNewCompletion = finishedAt > 0L && finishedAt != previous.lastJobFinishedAt
        var next = state.copy(integration = previous.copy(
            activeJobKind = if (running) kind else "",
            activeJobTitle = if (running) title else "",
            activeJobProgress = if (running) progress.coerceIn(0f, 1f) else 0f,
            lastJobSummary = if (finishedAt > 0L) summary.take(500) else previous.lastJobSummary,
            lastJobFinishedAt = maxOf(previous.lastJobFinishedAt, finishedAt),
        ))
        if (isNewCompletion) {
            next = next.copy(buildings = next.buildings.map { building ->
                if (building.id == "building_data") building.copy(technology = (building.technology + 0.004f).coerceAtMost(1f))
                else building
            }).withEvent(WorldEvent(
                id = next.nextEventId, simMinute = next.elapsedMinutes, kind = WorldEventKind.INTEGRATION,
                title = "Laboratório concluiu uma atividade",
                description = "${title.ifBlank { kind }}: ${summary.ifBlank { "sem resumo" }}".take(500),
                buildingIds = listOf("building_data"),
            ))
        }
        state = next
        return state
    }

    internal fun lodFor(agentId: String, index: Int, detailedIds: Set<String>, visibleIds: Set<String>): AgentLod = when {
        agentId in detailedIds -> AgentLod.DETAILED
        agentId in visibleIds || index < SIMPLIFIED_RESIDENT_LIMIT -> AgentLod.SIMPLIFIED
        else -> AgentLod.STATISTICAL
    }

    private fun simulateAgent(
        original: AgentState,
        world: WorldState,
        nowMinute: Long,
        elapsed: Long,
        random: WorldRandom,
    ): AgentState {
        var agent = original
        var remaining = elapsed.coerceAtMost(WORLD_MINUTES_PER_DAY * 2).toInt()
        var iterations = 0
        while (remaining > 0 && iterations++ < 12) {
            val urgent = agent.needs.energy < 12f || agent.needs.nutrition < 8f
            var action = agent.action
            if (action == null || (urgent && action.type != ActionType.SLEEP && action.type != ActionType.EAT)) {
                action = chooseAction(agent, world, nowMinute, random)
                agent = agent.copy(action = action)
            }
            val current = action
            val dx = current.target.x - agent.position.x
            val dz = current.target.z - agent.position.z
            val distance = sqrt(dx * dx + dz * dz)
            if (distance > 1.2f) {
                val travelNeeded = (distance / SPEED_UNITS_PER_MINUTE).toInt().coerceAtLeast(1)
                if (travelNeeded >= remaining) {
                    val travel = SPEED_UNITS_PER_MINUTE * remaining
                    val ratio = (travel / distance).coerceAtMost(1f)
                    agent = agent.copy(
                        position = Vector2State(agent.position.x + dx * ratio, agent.position.z + dz * ratio),
                        needs = passiveNeeds(agent.needs, remaining / 60f),
                    )
                    remaining = 0
                    break
                }
                agent = agent.copy(
                    position = current.target,
                    currentBuildingId = current.targetBuildingId,
                    needs = passiveNeeds(agent.needs, travelNeeded / 60f),
                )
                remaining -= travelNeeded
                if (remaining <= 0) break
            } else {
                agent = agent.copy(position = current.target, currentBuildingId = current.targetBuildingId)
            }

            val used = min(remaining, current.remainingMinutes).coerceAtLeast(1)
            agent = applyAction(agent, current, used)
            val left = current.remainingMinutes - used
            remaining -= used
            agent = if (left > 0) {
                agent.copy(action = current.copy(remainingMinutes = left))
            } else {
                var completed = agent.copy(action = null).withActivity(ActivityRecord(
                    simMinute = nowMinute,
                    action = current.type,
                    durationMinutes = current.totalMinutes,
                    buildingId = current.targetBuildingId,
                    outcome = actionOutcome(current.type),
                ))
                completed = when (current.type) {
                    ActionType.RESEARCH -> completed.withMemory(nowMinute, "research", "Concluí um ciclo de pesquisa no ${world.building(current.targetBuildingId ?: "")?.name ?: "Instituto"}.", 0.3f)
                    ActionType.LEARN -> completed.withMemory(nowMinute, "learning", "Dediquei tempo a desenvolver ${completed.skills.keys.sorted().joinToString(", ")}.", 0.25f)
                    ActionType.SOCIALIZE -> {
                        val other = world.agent(current.targetAgentId ?: "")?.name
                        completed.withMemory(nowMinute, "social", if (other != null) "Encontrei $other e compartilhamos experiências." else "Passei tempo na vida pública de Aurora.", 0.35f, current.targetAgentId)
                    }
                    else -> completed
                }
                completed
            }
        }
        val careerProgress = if (agent.skills.isEmpty()) 0f else agent.skills.values.average().toFloat()
        val goals = agent.goals.map { goal ->
            if (goal.id.endsWith("career")) goal.copy(progress = careerProgress.coerceIn(0f, 1f), completed = careerProgress >= 0.95f)
            else goal.copy(progress = ((agent.needs.satisfaction / 100f) * 0.6f + (1f - agent.needs.stress / 100f) * 0.4f).coerceIn(0f, 1f))
        }
        return agent.copy(
            needs = sanitizeNeeds(agent.needs),
            emotion = emotionFor(agent.needs),
            goals = goals,
            lastUpdatedMinute = nowMinute,
        )
    }

    internal fun chooseAction(agent: AgentState, world: WorldState, nowMinute: Long, random: WorldRandom): AgentAction {
        val needs = agent.needs
        val minute = (nowMinute % WORLD_MINUTES_PER_DAY).toInt()
        val routineAction = agent.routine.firstOrNull { minute >= it.startMinute && minute < it.endMinute }?.preferredAction
        val employed = agent.employerId?.let { world.company(it) }?.takeUnless { it.bankrupt } != null
        val scores = mutableMapOf(
            ActionType.SLEEP to (100f - needs.energy) * 1.35f + needs.stress * 0.2f,
            ActionType.EAT to (100f - needs.nutrition) * 1.2f,
            ActionType.SOCIALIZE to (100f - needs.social) * (0.55f + agent.personality.extraversion * 0.7f),
            ActionType.WORK to if (employed || agent.profession == Profession.EDUCATOR || agent.profession == Profession.ARTISAN)
                28f + needs.motivation * 0.55f + agent.personality.conscientiousness * 28f - needs.stress * 0.32f else -100f,
            ActionType.RESEARCH to 14f + agent.personality.openness * 30f + agent.personality.ambition * 18f +
                (if (agent.profession == Profession.RESEARCHER) 26f else 0f) - needs.stress * 0.2f,
            ActionType.LEARN to 20f + agent.personality.openness * 25f + agent.personality.ambition * 24f -
                (if (agent.skills.isEmpty()) 0f else agent.skills.values.average().toFloat() * 12f),
            ActionType.RELAX to needs.stress * 0.95f + (100f - needs.satisfaction) * 0.32f,
            ActionType.IDLE to 5f,
        )
        if (needs.energy < 22f) scores[ActionType.SLEEP] = scores.getValue(ActionType.SLEEP) + 100f
        if (needs.nutrition < 18f) scores[ActionType.EAT] = scores.getValue(ActionType.EAT) + 100f
        routineAction?.let { preferred ->
            if (preferred in scores) scores[preferred] = scores.getValue(preferred) +
                if (preferred == ActionType.SLEEP || preferred == ActionType.WORK) 30f else 14f
            if (preferred == ActionType.SLEEP) {
                scores[ActionType.WORK] = scores.getValue(ActionType.WORK) * 0.2f
                scores[ActionType.RESEARCH] = scores.getValue(ActionType.RESEARCH) * 0.35f
            }
        }
        val type = scores.maxByOrNull { it.value + random.nextFloat() * 2f }?.key ?: ActionType.IDLE
        val workplace = agent.employerId?.let { world.company(it)?.takeUnless { company -> company.bankrupt }?.buildingId }
            ?: if (agent.profession == Profession.EDUCATOR) "building_university" else "building_square"
        val buildingId = when (type) {
            ActionType.SLEEP -> agent.homeId
            ActionType.EAT -> "building_cafe"
            ActionType.WORK -> workplace
            ActionType.RESEARCH -> "building_research"
            ActionType.LEARN -> "building_university"
            ActionType.SOCIALIZE -> if (random.nextFloat() < 0.35f) "building_cafe" else "building_square"
            ActionType.RELAX, ActionType.IDLE -> "building_square"
        }
        val targetAgent = if (type == ActionType.SOCIALIZE && world.agents.size > 1) {
            val candidates = world.agents.filter { it.id != agent.id }
            candidates[random.nextInt(candidates.size)].id
        } else null
        val building = world.building(buildingId) ?: world.buildings.first()
        val spread = if (building.kind == BuildingKind.PUBLIC_SQUARE) 5.5f else
            maxOf(building.footprintX, building.footprintZ) * 0.35f + 0.8f
        val targetAngle = random.range(0f, (Math.PI * 2).toFloat())
        val target = Vector2State(
            building.position.x + cos(targetAngle) * spread,
            building.position.z + sin(targetAngle) * spread,
        )
        val duration = when (type) {
            ActionType.SLEEP -> 360
            ActionType.EAT -> 45
            ActionType.WORK, ActionType.RESEARCH -> 180
            ActionType.LEARN -> 120
            ActionType.SOCIALIZE, ActionType.RELAX -> 90
            ActionType.IDLE -> 30
        }
        return AgentAction(type, duration, duration, building.id, targetAgent, target, nowMinute)
    }

    private fun applyAction(agent: AgentState, action: AgentAction, minutes: Int): AgentState {
        val hours = minutes / 60f
        val needs = agent.needs
        val changed = when (action.type) {
            ActionType.SLEEP -> needs.copy(
                energy = needs.energy + 10f * hours, nutrition = needs.nutrition - 0.8f * hours,
                social = needs.social - 0.35f * hours, stress = needs.stress - 5f * hours,
                motivation = needs.motivation + 1.2f * hours,
            )
            ActionType.EAT -> needs.copy(
                energy = needs.energy + 0.8f * hours, nutrition = needs.nutrition + 65f * hours,
                social = needs.social + 2f * hours, satisfaction = needs.satisfaction + 2f * hours,
                stress = needs.stress - 1.5f * hours,
            )
            ActionType.WORK -> needs.copy(
                energy = needs.energy - 3.2f * hours, nutrition = needs.nutrition - 1.8f * hours,
                social = needs.social - 0.5f * hours, motivation = needs.motivation - 0.8f * hours,
                satisfaction = needs.satisfaction + 1.1f * hours, stress = needs.stress + 2.2f * hours,
            )
            ActionType.RESEARCH -> needs.copy(
                energy = needs.energy - 2.6f * hours, nutrition = needs.nutrition - 1.6f * hours,
                social = needs.social - 0.65f * hours, motivation = needs.motivation + 0.4f * hours,
                satisfaction = needs.satisfaction + 1.8f * hours, stress = needs.stress + 1.1f * hours,
            )
            ActionType.LEARN -> needs.copy(
                energy = needs.energy - 1.7f * hours, nutrition = needs.nutrition - 1.5f * hours,
                social = needs.social - 0.5f * hours, motivation = needs.motivation + 0.8f * hours,
                satisfaction = needs.satisfaction + 1.2f * hours, stress = needs.stress - 0.4f * hours,
            )
            ActionType.SOCIALIZE -> needs.copy(
                energy = needs.energy - 1.1f * hours, nutrition = needs.nutrition - 1.3f * hours,
                social = needs.social + 20f * hours, motivation = needs.motivation + 1.2f * hours,
                satisfaction = needs.satisfaction + 3f * hours, stress = needs.stress - 2.5f * hours,
            )
            ActionType.RELAX, ActionType.IDLE -> needs.copy(
                energy = needs.energy + 1.5f * hours, nutrition = needs.nutrition - 1.4f * hours,
                social = needs.social - 0.3f * hours, motivation = needs.motivation + 0.8f * hours,
                satisfaction = needs.satisfaction + 2f * hours, stress = needs.stress - 8f * hours,
            )
        }
        var updated = agent.copy(needs = changed)
        if (action.type in setOf(ActionType.WORK, ActionType.RESEARCH, ActionType.LEARN)) {
            val skillName = when (action.type) {
                ActionType.RESEARCH -> "research"
                ActionType.LEARN -> agent.skills.minByOrNull { it.value }?.key ?: "learning"
                else -> agent.skills.keys.sorted().firstOrNull() ?: "learning"
            }
            val rate = 0.0012f + agent.personality.openness * 0.0012f
            updated = updated.copy(skills = updated.skills + (skillName to ((updated.skills[skillName] ?: 0.05f) + rate * hours).coerceAtMost(1f)))
            if (action.type == ActionType.RESEARCH) {
                updated = updated.copy(knowledge = updated.knowledge + ("Pesquisa aplicada" to ((updated.knowledge["Pesquisa aplicada"] ?: 0f) + 0.004f * hours).coerceAtMost(1f)))
            }
        }
        if (action.type == ActionType.EAT) updated = updated.copy(wallet = (updated.wallet - minutes * 0.22).coerceAtLeast(0.0))
        return updated
    }

    private fun updateRelationships(agents: List<AgentState>, minutes: Int): List<AgentState> {
        val social = agents.filter { it.action?.type == ActionType.SOCIALIZE && it.currentBuildingId == it.action?.targetBuildingId }
        if (social.size < 2) return agents
        val affinity = mutableMapOf<String, MutableMap<String, Float>>()
        social.groupBy { it.currentBuildingId }.values.forEach { group ->
            group.sortedBy { it.id }.zipWithNext().forEach { (a, b) ->
                if (a.id == b.id) return@forEach
                val compatibility = 0.35f + (1f - kotlin.math.abs(a.personality.extraversion - b.personality.extraversion)) * 0.65f
                val delta = minutes * 0.0008f * compatibility
                affinity.getOrPut(a.id) { mutableMapOf() }[b.id] = delta
                affinity.getOrPut(b.id) { mutableMapOf() }[a.id] = delta
            }
        }
        if (affinity.isEmpty()) return agents
        return agents.map { agent ->
            val changes = affinity[agent.id] ?: return@map agent
            val relationships = agent.relationships.toMutableMap()
            changes.forEach { (other, delta) -> relationships[other] = ((relationships[other] ?: 0f) + delta).coerceIn(-1f, 1f) }
            agent.copy(relationships = relationships)
        }
    }

    private fun processEconomyDay(world: WorldState, day: Long, random: WorldRandom): WorldState {
        var events = world.events
        var nextEventId = world.nextEventId
        val dailyMetrics = mutableMapOf<String, CompanyDayMetric>()
        var companies = world.companies.map { company ->
            if (company.bankrupt) return@map company
            val employees = world.agents.filter { it.employerId == company.id }
            val productivity = productivityOf(employees)
            val payroll = employees.sumOf { it.salaryPerDay }
            val operations = 360.0 + employees.size * 32.0
            val researchBudget = when (company.strategy) {
                CompanyStrategy.RESEARCH -> 950.0
                CompanyStrategy.GROWTH -> 620.0
                CompanyStrategy.PROFIT -> 280.0
                CompanyStrategy.SURVIVAL -> 100.0
            } * world.parameters.researchMultiplier
            var revenue = 0.0
            var products = company.products.map { product ->
                var updated = product
                if (product.stage == ProductStage.CONCEPT || product.stage == ProductStage.DEVELOPMENT) {
                    val researchWorkers = employees.count { it.profession == Profession.RESEARCHER || it.profession == Profession.ENGINEER }
                    val gain = (0.012f + researchWorkers * 0.0028f) * productivity * world.parameters.researchMultiplier
                    updated = updated.copy(
                        stage = ProductStage.DEVELOPMENT,
                        development = (product.development + gain).coerceAtMost(1f),
                        quality = (product.quality + gain * 0.28f).coerceAtMost(1f),
                        innovation = (product.innovation + gain * 0.08f).coerceAtMost(1f),
                    )
                    if (updated.development >= 1f) {
                        updated = updated.copy(stage = ProductStage.RELEASED, releasedOnDay = day)
                        events = listOf(WorldEvent(
                            id = nextEventId++, simMinute = day * WORLD_MINUTES_PER_DAY, kind = WorldEventKind.ECONOMY,
                            title = "${company.name} lançou ${updated.name}",
                            description = "O produto concluiu o desenvolvimento com qualidade ${(updated.quality * 100).roundToLong()}% e entrou no mercado.",
                            companyIds = listOf(company.id),
                        )) + events
                    }
                }
                if (updated.stage == ProductStage.RELEASED) {
                    val qualityDemand = 12.0 + updated.quality * 85.0 + updated.innovation * 42.0 + company.technology * 65.0
                    val pricePressure = (updated.price / 180.0).coerceIn(0.25, 3.0)
                    val competition = activeProductCount(world) * 0.035 + 1.0
                    val expected = qualityDemand * world.parameters.demandMultiplier / (pricePressure * competition)
                    val units = (expected * random.range(0.86f, 1.14f)).roundToLong().coerceAtLeast(0)
                    val productRevenue = units * updated.price
                    revenue += productRevenue
                    updated = updated.copy(unitsSold = updated.unitsSold + units, totalRevenue = updated.totalRevenue + productRevenue)
                }
                updated
            }
            val expenses = payroll + operations + researchBudget
            val cash = company.cash + revenue - expenses
            val negativeDays = if (cash < 0) company.negativeCashDays + 1 else 0
            val technology = (company.technology + researchBudget / 1_000_000.0 * productivity).toFloat().coerceAtMost(1f)
            val bankrupt = negativeDays >= 3 || cash < -25_000.0
            val strategy = when {
                bankrupt -> CompanyStrategy.SURVIVAL
                cash < 8_000.0 -> CompanyStrategy.SURVIVAL
                company.strategy == CompanyStrategy.SURVIVAL && cash > 30_000.0 -> CompanyStrategy.PROFIT
                else -> company.strategy
            }
            dailyMetrics[company.id] = CompanyDayMetric(
                day = day, revenue = revenue, expenses = expenses, profit = revenue - expenses,
                cash = cash, productivity = productivity, employees = employees.size,
                technology = technology,
            )
            if (bankrupt) {
                products = products.map { if (it.stage == ProductStage.RELEASED) it.copy(stage = ProductStage.RETIRED) else it }
                events = listOf(WorldEvent(
                    id = nextEventId++, simMinute = day * WORLD_MINUTES_PER_DAY, kind = WorldEventKind.BANKRUPTCY,
                    title = "${company.name} encerrou as operações",
                    description = "O caixa permaneceu negativo por $negativeDays dias. ${employees.size} vínculos foram encerrados.",
                    companyIds = listOf(company.id), agentIds = employees.map { it.id },
                )) + events
            }
            company.copy(
                strategy = strategy, cash = cash, products = products, technology = technology,
                negativeCashDays = negativeDays, bankrupt = bankrupt,
                metrics = (company.metrics + dailyMetrics.getValue(company.id)).takeLast(240),
            )
        }

        var agents = world.agents.map { agent ->
            val company = companies.firstOrNull { it.id == agent.employerId }
            when {
                company == null && agent.employerId != null -> agent.copy(employerId = null)
                company?.bankrupt == true -> agent.copy(employerId = null, needs = agent.needs.copy(stress = (agent.needs.stress + 12f).coerceAtMost(100f)))
                company != null -> agent.copy(wallet = agent.wallet + agent.salaryPerDay)
                else -> agent
            }
        }

        // Autonomous hiring is based on cash, strategy, productivity and real unemployed candidates.
        companies = companies.map { company ->
            if (company.bankrupt) return@map company
            val metric = dailyMetrics[company.id] ?: return@map company
            val currentEmployees = agents.count { it.employerId == company.id }
            val wantsGrowth = company.strategy == CompanyStrategy.GROWTH || company.strategy == CompanyStrategy.RESEARCH
            val canHire = company.cash > 28_000.0 && currentEmployees < 24 &&
                (wantsGrowth || metric.profit > 0) && metric.productivity > 0.45f
            if (!canHire) return@map company
            val openings = if (wantsGrowth && company.cash > 60_000.0) 2 else 1
            val candidates = agents.filter { it.employerId == null && it.profession != Profession.EDUCATOR && it.profession != Profession.ARTISAN }
                .sortedByDescending { it.skills.values.average() }
                .take(openings)
            if (candidates.isEmpty()) return@map company
            val ids = candidates.map { it.id }.toSet()
            agents = agents.map { agent -> if (agent.id in ids) agent.copy(employerId = company.id) else agent }
            events = listOf(WorldEvent(
                id = nextEventId++, simMinute = day * WORLD_MINUTES_PER_DAY, kind = WorldEventKind.HIRING,
                title = "${company.name} abriu ${candidates.size} ${if (candidates.size == 1) "vaga" else "vagas"}",
                description = "Contratou ${candidates.joinToString { it.name }} após avaliar caixa, produtividade e estratégia.",
                companyIds = listOf(company.id), agentIds = candidates.map { it.id },
            )) + events
            company
        }

        val totalRevenue = dailyMetrics.values.sumOf { it.revenue }.coerceAtLeast(0.0001)
        companies = companies.map { company ->
            val metric = dailyMetrics[company.id] ?: return@map company
            val withShare = metric.copy(
                marketShare = (metric.revenue / totalRevenue).toFloat().coerceIn(0f, 1f),
                employees = agents.count { it.employerId == company.id },
            )
            company.copy(metrics = company.metrics.dropLast(1) + withShare)
        }
        val daily = dailyMetrics.values.toList()
        val economy = EconomyDayMetric(
            day = day,
            revenue = daily.sumOf { it.revenue },
            expenses = daily.sumOf { it.expenses },
            profit = daily.sumOf { it.profit },
            productivity = if (daily.isEmpty()) 0f else daily.map { it.productivity }.average().toFloat(),
            employees = agents.count { agent -> companies.any { it.id == agent.employerId && !it.bankrupt } },
            companies = companies.count { !it.bankrupt },
            technology = if (companies.isEmpty()) 0f else companies.filterNot { it.bankrupt }.map { it.technology }.average().toFloat(),
        )
        return world.copy(
            companies = companies,
            agents = agents,
            buildings = world.buildings.map { building ->
                val company = companies.firstOrNull { it.id == building.companyId }
                if (company != null) building.copy(technology = company.technology, condition = if (company.bankrupt) 0.65f else building.condition)
                else building
            },
            events = events.take(400),
            nextEventId = nextEventId,
            economyHistory = (world.economyHistory + economy).takeLast(240),
        )
    }

    private fun productivityOf(employees: List<AgentState>): Float {
        if (employees.isEmpty()) return 0.2f
        return employees.map { agent ->
            val skills = if (agent.skills.isEmpty()) 0.1f else agent.skills.values.average().toFloat()
            (skills * 0.35f + agent.needs.energy / 100f * 0.15f + agent.needs.motivation / 100f * 0.2f +
                agent.needs.satisfaction / 100f * 0.1f + (1f - agent.needs.stress / 100f) * 0.2f)
                .coerceIn(0.05f, 1f)
        }.average().toFloat()
    }

    companion object {
        const val MAX_OFFLINE_MINUTES = 7L * WORLD_MINUTES_PER_DAY
        const val FOUNDING_COST = 25_000.0
        const val SIMPLIFIED_RESIDENT_LIMIT = 128
        private const val SPEED_UNITS_PER_MINUTE = 0.35f
        val PLAYBACK_SPEEDS = setOf(1, 4, 16)

        fun validate(world: WorldState) {
            require(world.schemaVersion <= WORLD_SCHEMA_VERSION) { "Snapshot de uma versão futura não suportada." }
            require(world.agents.map { it.id }.distinct().size == world.agents.size) { "IDs de habitantes duplicados." }
            require(world.companies.map { it.id }.distinct().size == world.companies.size) { "IDs de empresas duplicados." }
            require(world.buildings.map { it.id }.distinct().size == world.buildings.size) { "IDs de edifícios duplicados." }
            require(world.cities.isNotEmpty() && world.buildings.isNotEmpty()) { "Mundo sem cidade ou edifícios." }
            require(world.timeScale in PLAYBACK_SPEEDS) { "Escala temporal inválida." }
            require(world.elapsedMinutes >= 0 && world.tick >= 0) { "Relógio da simulação inválido." }
            world.agents.forEach { agent ->
                val n = agent.needs
                require(listOf(n.energy, n.nutrition, n.social, n.motivation, n.satisfaction, n.stress).all { it.isFinite() }) {
                    "Necessidades inválidas para ${agent.id}."
                }
            }
            world.companies.forEach { company -> require(company.cash.isFinite()) { "Caixa inválido em ${company.id}." } }
        }
    }
}

private fun passiveNeeds(needs: Needs, hours: Float): Needs = needs.copy(
    energy = needs.energy - 1.6f * hours,
    nutrition = needs.nutrition - 1.5f * hours,
    social = needs.social - 0.5f * hours,
    stress = needs.stress - 0.25f * hours,
)

private fun sanitizeNeeds(needs: Needs): Needs = Needs(
    energy = needs.energy.coerceIn(0f, 100f),
    nutrition = needs.nutrition.coerceIn(0f, 100f),
    social = needs.social.coerceIn(0f, 100f),
    motivation = needs.motivation.coerceIn(0f, 100f),
    satisfaction = needs.satisfaction.coerceIn(0f, 100f),
    stress = needs.stress.coerceIn(0f, 100f),
)

private fun emotionFor(needs: Needs): Emotion = when {
    needs.stress > 78f -> Emotion.ANXIOUS
    needs.energy < 22f -> Emotion.TIRED
    needs.social < 20f -> Emotion.LONELY
    needs.satisfaction < 25f -> Emotion.FRUSTRATED
    needs.satisfaction > 78f && needs.stress < 35f -> Emotion.FULFILLED
    needs.motivation > 70f -> Emotion.FOCUSED
    else -> Emotion.CALM
}

private fun actionOutcome(type: ActionType): String = when (type) {
    ActionType.SLEEP -> "Energia e motivação recuperadas"
    ActionType.EAT -> "Nutrição recuperada"
    ActionType.WORK -> "Contribuição registrada na produtividade da empresa"
    ActionType.RESEARCH -> "Conhecimento e pesquisa aplicada ampliados"
    ActionType.LEARN -> "Habilidade praticada"
    ActionType.SOCIALIZE -> "Vínculos sociais fortalecidos"
    ActionType.RELAX -> "Estresse reduzido"
    ActionType.IDLE -> "Tempo de observação"
}

private fun activeProductCount(world: WorldState): Int = world.companies.sumOf { company ->
    company.products.count { it.stage == ProductStage.RELEASED }
}

private fun defaultHappeningDescription(type: HappeningType): String = when (type) {
    HappeningType.FESTIVAL -> "Encontro público na Praça do Girassol; necessidades sociais e emocionais foram alteradas."
    HappeningType.BLACKOUT -> "Uma falha de infraestrutura reduziu a condição dos edifícios e elevou o estresse."
    HappeningType.RESEARCH_GRANT -> "Empresas ativas receberam recursos reais de caixa e avanço tecnológico."
    HappeningType.MARKET_BOOM -> "A demanda usada no próximo fechamento econômico foi ampliada."
}
