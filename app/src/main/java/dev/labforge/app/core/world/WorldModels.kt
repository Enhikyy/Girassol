package dev.labforge.app.core.world

import kotlinx.serialization.Serializable

/** Current on-disk schema. Readers must ignore unknown fields for forward compatibility. */
const val WORLD_SCHEMA_VERSION = 1
const val WORLD_MINUTES_PER_DAY = 1_440L

@Serializable
enum class Profession { ENGINEER, RESEARCHER, DESIGNER, MANAGER, OPERATOR, EDUCATOR, ARTISAN, UNEMPLOYED }

@Serializable
enum class BuildingKind { HEADQUARTERS, OFFICE, RESEARCH_LAB, RESIDENCE, PUBLIC_SQUARE, CAFE, UNIVERSITY, DATA_CENTER, TRANSIT_HUB }

@Serializable
enum class ActionType { SLEEP, EAT, WORK, RESEARCH, LEARN, SOCIALIZE, RELAX, IDLE }

@Serializable
enum class Emotion { FULFILLED, CALM, FOCUSED, TIRED, ANXIOUS, LONELY, FRUSTRATED }

@Serializable
enum class CompanyStrategy { GROWTH, RESEARCH, PROFIT, SURVIVAL }

@Serializable
enum class ProductStage { CONCEPT, DEVELOPMENT, RELEASED, RETIRED }

@Serializable
enum class WorldEventKind {
    GENESIS, DECISION, SOCIAL, ECONOMY, RESEARCH, HIRING, BANKRUPTCY,
    USER, INTEGRATION, TEMPORAL, DIALOGUE,
}

@Serializable
enum class HappeningType { FESTIVAL, BLACKOUT, RESEARCH_GRANT, MARKET_BOOM }

@Serializable
data class Vector2State(val x: Float = 0f, val z: Float = 0f)

@Serializable
data class Personality(
    val openness: Float = 0.5f,
    val conscientiousness: Float = 0.5f,
    val extraversion: Float = 0.5f,
    val agreeableness: Float = 0.5f,
    val ambition: Float = 0.5f,
)

@Serializable
data class Appearance(
    val height: Float = 1f,
    val build: Float = 0.5f,
    val skinTone: Int = 1,
    val hairStyle: Int = 0,
    val primaryColor: Long = 0xFF4F7D8CL,
    val accentColor: Long = 0xFFF2C14EL,
)

@Serializable
data class Needs(
    val energy: Float = 80f,
    val nutrition: Float = 80f,
    val social: Float = 65f,
    val motivation: Float = 70f,
    val satisfaction: Float = 65f,
    val stress: Float = 20f,
)

@Serializable
data class MemoryState(
    val id: Long,
    val simMinute: Long,
    val kind: String,
    val text: String,
    val sentiment: Float = 0f,
    val relatedAgentId: String? = null,
)

@Serializable
data class ActivityRecord(
    val simMinute: Long,
    val action: ActionType,
    val durationMinutes: Int,
    val buildingId: String? = null,
    val outcome: String = "",
)

@Serializable
data class GoalState(
    val id: String,
    val description: String,
    val progress: Float = 0f,
    val priority: Float = 0.5f,
    val completed: Boolean = false,
)

@Serializable
data class RoutineBlock(
    val startMinute: Int,
    val endMinute: Int,
    val preferredAction: ActionType,
)

@Serializable
data class AgentAction(
    val type: ActionType,
    val remainingMinutes: Int,
    val totalMinutes: Int,
    val targetBuildingId: String? = null,
    val targetAgentId: String? = null,
    val target: Vector2State = Vector2State(),
    val startedMinute: Long = 0L,
)

@Serializable
data class AgentState(
    val id: String,
    val name: String,
    val appearance: Appearance,
    val personality: Personality,
    val goals: List<GoalState>,
    val memories: List<MemoryState> = emptyList(),
    val knowledge: Map<String, Float> = emptyMap(),
    val skills: Map<String, Float> = emptyMap(),
    val needs: Needs,
    val routine: List<RoutineBlock>,
    val profession: Profession,
    val employerId: String? = null,
    val homeId: String,
    val currentBuildingId: String? = null,
    val relationships: Map<String, Float> = emptyMap(),
    val emotion: Emotion = Emotion.CALM,
    val activityHistory: List<ActivityRecord> = emptyList(),
    val position: Vector2State,
    val action: AgentAction? = null,
    val wallet: Double = 500.0,
    val salaryPerDay: Double = 120.0,
    val lastUpdatedMinute: Long = 0L,
    val nextMemoryId: Long = 1L,
)

@Serializable
data class CityState(
    val id: String,
    val name: String,
    val center: Vector2State,
    val description: String,
)

@Serializable
data class BuildingState(
    val id: String,
    val cityId: String,
    val name: String,
    val kind: BuildingKind,
    val position: Vector2State,
    val height: Float,
    val footprintX: Float,
    val footprintZ: Float,
    val companyId: String? = null,
    val technology: Float = 0.1f,
    val condition: Float = 1f,
    val accentColor: Long = 0xFFF2C14EL,
)

@Serializable
data class DepartmentState(
    val id: String,
    val name: String,
    val budget: Double,
    val focus: String,
)

@Serializable
data class ProductState(
    val id: String,
    val companyId: String,
    val name: String,
    val stage: ProductStage = ProductStage.CONCEPT,
    val development: Float = 0f,
    val quality: Float = 0.25f,
    val innovation: Float = 0.3f,
    val price: Double = 100.0,
    val unitsSold: Long = 0L,
    val totalRevenue: Double = 0.0,
    val releasedOnDay: Long? = null,
)

@Serializable
data class CompanyDayMetric(
    val day: Long,
    val revenue: Double,
    val expenses: Double,
    val profit: Double,
    val cash: Double,
    val productivity: Float,
    val employees: Int,
    val technology: Float,
    val marketShare: Float = 0f,
)

@Serializable
data class CompanyState(
    val id: String,
    val name: String,
    val strategy: CompanyStrategy,
    val cash: Double,
    val buildingId: String,
    val departments: List<DepartmentState>,
    val products: List<ProductState> = emptyList(),
    val metrics: List<CompanyDayMetric> = emptyList(),
    val technology: Float = 0.1f,
    val negativeCashDays: Int = 0,
    val bankrupt: Boolean = false,
    val foundedOnDay: Long = 0L,
    val userOwned: Boolean = false,
    val nextProductId: Long = 1L,
)

@Serializable
data class EconomyDayMetric(
    val day: Long,
    val revenue: Double,
    val expenses: Double,
    val profit: Double,
    val productivity: Float,
    val employees: Int,
    val companies: Int,
    val technology: Float,
)

@Serializable
data class WorldEvent(
    val id: Long,
    val simMinute: Long,
    val kind: WorldEventKind,
    val title: String,
    val description: String,
    val agentIds: List<String> = emptyList(),
    val companyIds: List<String> = emptyList(),
    val buildingIds: List<String> = emptyList(),
)

@Serializable
data class IntegrationState(
    val knowledgeCount: Int = 0,
    val lastKnowledgeTitle: String = "",
    val activeJobKind: String = "",
    val activeJobTitle: String = "",
    val activeJobProgress: Float = 0f,
    val lastJobSummary: String = "",
    val lastJobFinishedAt: Long = 0L,
)

@Serializable
data class SimulationParameters(
    val demandMultiplier: Float = 1f,
    val researchMultiplier: Float = 1f,
    val offlineCatchUpEnabled: Boolean = true,
)

@Serializable
data class WorldState(
    val schemaVersion: Int = WORLD_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val seed: Long,
    val rngState: Long,
    val tick: Long = 0L,
    val elapsedMinutes: Long = 0L,
    val lastWallClockMs: Long,
    val timeScale: Int = 1,
    val paused: Boolean = false,
    val playerCapital: Double = 100_000.0,
    val cities: List<CityState>,
    val buildings: List<BuildingState>,
    val agents: List<AgentState>,
    val companies: List<CompanyState>,
    val events: List<WorldEvent>,
    val economyHistory: List<EconomyDayMetric> = emptyList(),
    val parameters: SimulationParameters = SimulationParameters(),
    val integration: IntegrationState = IntegrationState(),
    val nextAgentId: Long = 1L,
    val nextCompanyId: Long = 1L,
    val nextBuildingId: Long = 1L,
    val nextEventId: Long = 2L,
)

val WorldState.day: Long get() = elapsedMinutes / WORLD_MINUTES_PER_DAY
val WorldState.minuteOfDay: Int get() = (elapsedMinutes % WORLD_MINUTES_PER_DAY).toInt()
val WorldState.activeCompanies: List<CompanyState> get() = companies.filterNot { it.bankrupt }
val WorldState.employedAgents: List<AgentState> get() = agents.filter { agent ->
    agent.employerId != null && companies.any { it.id == agent.employerId && !it.bankrupt }
}

fun WorldState.agent(id: String): AgentState? = agents.firstOrNull { it.id == id }
fun WorldState.company(id: String): CompanyState? = companies.firstOrNull { it.id == id }
fun WorldState.building(id: String): BuildingState? = buildings.firstOrNull { it.id == id }

fun AgentState.withMemory(
    simMinute: Long,
    kind: String,
    text: String,
    sentiment: Float = 0f,
    relatedAgentId: String? = null,
): AgentState {
    val memory = MemoryState(nextMemoryId, simMinute, kind, text.take(600), sentiment.coerceIn(-1f, 1f), relatedAgentId)
    return copy(memories = (listOf(memory) + memories).take(40), nextMemoryId = nextMemoryId + 1)
}

fun AgentState.withActivity(record: ActivityRecord): AgentState =
    copy(activityHistory = (listOf(record) + activityHistory).take(32))

fun WorldState.withEvent(event: WorldEvent): WorldState =
    copy(events = (listOf(event) + events).take(400), nextEventId = maxOf(nextEventId, event.id + 1))
