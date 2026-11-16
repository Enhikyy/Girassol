package dev.labforge.app.core.world

import dev.labforge.app.core.agent.AgentTool
import dev.labforge.app.core.agent.ToolResult
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Read-only, bounded access to the real simulation state for the ReAct agent. */
class WorldSummaryTool(
    workspace: File,
    private val runtime: WorldRuntime,
) : AgentTool(workspace) {
    override val name = "world_summary"
    override val description = "Consulta o estado atual e persistido do MUNDO: tempo, habitantes, empresas, economia e integrações reais."
    override fun parameters() = schema(JSONObject())

    override suspend fun execute(args: JSONObject): ToolResult = runCatching {
        val world = runtime.loadedState()
        val companies = JSONArray().apply {
            world.companies.sortedBy { it.id }.forEach { company ->
                put(JSONObject().apply {
                    put("id", company.id)
                    put("name", company.name)
                    put("bankrupt", company.bankrupt)
                    put("cash", company.cash)
                    put("employees", world.agents.count { it.employerId == company.id })
                    put("technology", company.technology)
                    put("released_products", company.products.count { it.stage == ProductStage.RELEASED })
                    put("latest_metric", company.metrics.lastOrNull()?.let(::metricJson) ?: JSONObject.NULL)
                })
            }
        }
        val output = JSONObject().apply {
            put("world_id", world.id)
            put("name", world.name)
            put("cities", JSONArray(world.cities.map { it.name }))
            put("day", world.day + 1)
            put("minute_of_day", world.minuteOfDay)
            put("tick", world.tick)
            put("paused", world.paused)
            put("time_scale", world.timeScale)
            put("residents", world.agents.size)
            put("employed_residents", world.employedAgents.size)
            put("buildings", world.buildings.size)
            put("active_companies", world.activeCompanies.size)
            put("player_capital", world.playerCapital)
            put("latest_economy", world.economyHistory.lastOrNull()?.let { metric ->
                JSONObject().apply {
                    put("day", metric.day)
                    put("revenue", metric.revenue)
                    put("expenses", metric.expenses)
                    put("profit", metric.profit)
                    put("productivity", metric.productivity)
                    put("employees", metric.employees)
                    put("companies", metric.companies)
                    put("technology", metric.technology)
                }
            } ?: JSONObject.NULL)
            put("integration", JSONObject().apply {
                put("knowledge_count", world.integration.knowledgeCount)
                put("latest_knowledge_title", world.integration.lastKnowledgeTitle)
                put("active_job_kind", world.integration.activeJobKind)
                put("active_job_title", world.integration.activeJobTitle)
                put("active_job_progress", world.integration.activeJobProgress)
                put("last_job_summary", world.integration.lastJobSummary)
            })
            put("companies", companies)
        }
        ToolResult(true, output.toString())
    }.getOrElse { ToolResult(false, it.message ?: "falha ao consultar o mundo") }
}

class WorldAgentTool(
    workspace: File,
    private val runtime: WorldRuntime,
) : AgentTool(workspace) {
    override val name = "world_agent"
    override val description = "Busca até quatro habitantes reais por ID, nome, profissão ou atividade e retorna necessidades, objetivos, vínculos e memórias persistidas."
    override fun parameters() = schema(JSONObject().apply {
        put("query", prop("string", "ID, nome, profissão ou atividade do habitante"))
    }, listOf("query"))

    override suspend fun execute(args: JSONObject): ToolResult = runCatching {
        val world = runtime.loadedState()
        val query = args.getString("query").trim()
        require(query.isNotEmpty()) { "Informe uma busca não vazia." }
        val direct = world.agent(query)
        val matches = if (direct != null) listOf(direct) else world.agents.filter { agent ->
            agent.name.contains(query, true) || agent.id.contains(query, true) ||
                agent.profession.displayName().contains(query, true) ||
                agent.action?.type?.displayName()?.contains(query, true) == true
        }.sortedBy { it.id }.take(4)
        if (matches.isEmpty()) {
            ToolResult(true, "Nenhum habitante encontrado para \"$query\".")
        } else {
            ToolResult(true, JSONArray().apply { matches.forEach { put(agentJson(it, world)) } }.toString())
        }
    }.getOrElse { ToolResult(false, it.message ?: "falha ao consultar habitantes") }
}

class WorldEventsTool(
    workspace: File,
    private val runtime: WorldRuntime,
) : AgentTool(workspace) {
    override val name = "world_events"
    override val description = "Consulta acontecimentos realmente registrados no MUNDO, com filtros opcionais por texto e tipo; não gera histórico artificial."
    override fun parameters() = schema(JSONObject().apply {
        put("query", prop("string", "Texto opcional no título ou descrição"))
        put("kind", prop("string", "Tipo opcional, como ECONOMY, SOCIAL, HIRING ou INTEGRATION"))
        put("limit", prop("integer", "Quantidade máxima, de 1 a 30 (padrão 12)"))
    })

    override suspend fun execute(args: JSONObject): ToolResult = runCatching {
        val world = runtime.loadedState()
        val query = args.optString("query").trim()
        val kindText = args.optString("kind").trim()
        val kind = if (kindText.isBlank()) null else WorldEventKind.valueOf(kindText.uppercase())
        val limit = args.optInt("limit", 12).coerceIn(1, 30)
        val events = world.events.asSequence()
            .filter { kind == null || it.kind == kind }
            .filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }
            .take(limit)
            .toList()
        if (events.isEmpty()) {
            ToolResult(true, "Nenhum acontecimento corresponde aos filtros.")
        } else {
            ToolResult(true, JSONArray().apply {
                events.forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id)
                        put("day", event.simMinute / WORLD_MINUTES_PER_DAY + 1)
                        put("minute_of_day", event.simMinute % WORLD_MINUTES_PER_DAY)
                        put("kind", event.kind.name)
                        put("title", event.title)
                        put("description", event.description)
                        put("agent_ids", JSONArray(event.agentIds))
                        put("company_ids", JSONArray(event.companyIds))
                        put("building_ids", JSONArray(event.buildingIds))
                    })
                }
            }.toString())
        }
    }.getOrElse { ToolResult(false, it.message ?: "falha ao consultar acontecimentos") }
}

class WorldHappeningTool(
    workspace: File,
    private val runtime: WorldRuntime,
) : AgentTool(workspace) {
    override val name = "world_happening"
    override val description = "Aplica um acontecimento permitido ao estado real do MUNDO. Tipos: FESTIVAL, BLACKOUT, RESEARCH_GRANT ou MARKET_BOOM."
    override fun parameters() = schema(JSONObject().apply {
        put("type", JSONObject().apply {
            put("type", "string")
            put("description", "Tipo de acontecimento")
            put("enum", JSONArray(HappeningType.entries.map { it.name }))
        })
        put("description", prop("string", "Descrição factual do acontecimento, até 500 caracteres"))
    }, listOf("type", "description"))

    override suspend fun execute(args: JSONObject): ToolResult {
        return try {
            require(!runtime.status.value.loading) { "O mundo ainda está sendo carregado; tente novamente." }
            val type = HappeningType.valueOf(args.getString("type").trim().uppercase())
            val description = args.getString("description").trim()
            require(description.isNotEmpty()) { "Descreva o acontecimento." }
            val world = runtime.applyHappening(type, description).getOrThrow()
            ToolResult(true, JSONObject().apply {
                put("applied", true)
                put("type", type.name)
                put("event", world.events.first().title)
                put("day", world.day + 1)
                put("minute_of_day", world.minuteOfDay)
            }.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            ToolResult(false, failure.message ?: "falha ao aplicar acontecimento")
        }
    }
}

fun worldTools(workspace: File, runtime: WorldRuntime?): List<AgentTool> =
    if (runtime == null) emptyList() else listOf(
        WorldSummaryTool(workspace, runtime),
        WorldAgentTool(workspace, runtime),
        WorldEventsTool(workspace, runtime),
        WorldHappeningTool(workspace, runtime),
    )

private fun WorldRuntime.loadedState(): WorldState {
    check(!status.value.loading) { "O mundo ainda está sendo carregado; tente novamente." }
    return state.value
}

private fun agentJson(agent: AgentState, world: WorldState): JSONObject = JSONObject().apply {
    put("id", agent.id)
    put("name", agent.name)
    put("profession", agent.profession.name)
    put("profession_label", agent.profession.displayName())
    put("employer", agent.employerId?.let { world.company(it)?.name } ?: JSONObject.NULL)
    put("emotion", agent.emotion.name)
    put("action", agent.action?.type?.name ?: JSONObject.NULL)
    put("action_minutes_remaining", agent.action?.remainingMinutes ?: 0)
    put("current_building", agent.currentBuildingId?.let { world.building(it)?.name } ?: JSONObject.NULL)
    put("wallet", agent.wallet)
    put("salary_per_day", agent.salaryPerDay)
    put("needs", JSONObject().apply {
        put("energy", agent.needs.energy)
        put("nutrition", agent.needs.nutrition)
        put("social", agent.needs.social)
        put("motivation", agent.needs.motivation)
        put("satisfaction", agent.needs.satisfaction)
        put("stress", agent.needs.stress)
    })
    put("skills", JSONObject(agent.skills))
    put("knowledge", JSONObject(agent.knowledge))
    put("goals", JSONArray().apply {
        agent.goals.forEach { goal ->
            put(JSONObject().apply {
                put("description", goal.description)
                put("progress", goal.progress)
                put("priority", goal.priority)
                put("completed", goal.completed)
            })
        }
    })
    put("relationships", JSONArray().apply {
        agent.relationships.entries.sortedByDescending { it.value }.take(6).forEach { (id, affinity) ->
            put(JSONObject().apply {
                put("agent_id", id)
                put("name", world.agent(id)?.name ?: id)
                put("affinity", affinity)
            })
        }
    })
    put("memories", JSONArray().apply {
        agent.memories.take(5).forEach { memory ->
            put(JSONObject().apply {
                put("day", memory.simMinute / WORLD_MINUTES_PER_DAY + 1)
                put("kind", memory.kind)
                put("text", memory.text)
                put("sentiment", memory.sentiment)
            })
        }
    })
}

private fun metricJson(metric: CompanyDayMetric): JSONObject = JSONObject().apply {
    put("day", metric.day)
    put("revenue", metric.revenue)
    put("expenses", metric.expenses)
    put("profit", metric.profit)
    put("productivity", metric.productivity)
    put("employees", metric.employees)
    put("market_share", metric.marketShare)
}
