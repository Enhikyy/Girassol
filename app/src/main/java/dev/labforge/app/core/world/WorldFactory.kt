package dev.labforge.app.core.world

import kotlin.math.cos
import kotlin.math.sin

object WorldFactory {
    const val DEFAULT_POPULATION = 72

    private val givenNames = listOf(
        "Ada", "Alice", "Ayla", "Bruno", "Caio", "Clara", "Dante", "Elisa", "Enzo", "Esther",
        "Felipe", "Gaia", "Heitor", "Iris", "Iago", "Joana", "Kai", "Lia", "Lucas", "Luna",
        "Maya", "Miguel", "Nina", "Noah", "Olivia", "Otávio", "Paula", "Ravi", "Rita", "Rui",
        "Sofia", "Téo", "Valentina", "Vicente", "Yasmin", "Zara", "André", "Beatriz", "Cecília", "Davi",
    )
    private val surnames = listOf(
        "Almeida", "Barbosa", "Cardoso", "Duarte", "Esteves", "Freitas", "Gomes", "Lima",
        "Martins", "Moreira", "Nogueira", "Oliveira", "Pires", "Queiroz", "Ribeiro", "Santos",
    )
    private val companyNames = listOf(
        "Hélio Sistemas", "Núcleo Vetorial", "Aurora Dados",
        "Síntese Norte", "Malha Cognitiva", "Órbita Industrial",
    )
    private val productNames = listOf("Atlas Local", "Prisma", "Caderno Neural", "Vetor", "Teia", "Forja")
    private val skillsByProfession = mapOf(
        Profession.ENGINEER to listOf("software", "systems", "mathematics"),
        Profession.RESEARCHER to listOf("research", "machine_learning", "writing"),
        Profession.DESIGNER to listOf("design", "communication", "creativity"),
        Profession.MANAGER to listOf("leadership", "finance", "communication"),
        Profession.OPERATOR to listOf("operations", "systems", "logistics"),
        Profession.EDUCATOR to listOf("teaching", "communication", "research"),
        Profession.ARTISAN to listOf("craft", "creativity", "operations"),
        Profession.UNEMPLOYED to listOf("communication", "learning"),
    )

    fun create(seed: Long = 7_241_153L, nowWallClockMs: Long = 0L, population: Int = DEFAULT_POPULATION): WorldState {
        require(population in 8..4_096)
        val random = WorldRandom(seed)
        val startMinute = 9L * 60L
        val city = CityState(
            id = "city_aurora",
            name = "Aurora",
            center = Vector2State(),
            description = "Cidade-laboratório organizada em torno de conhecimento, software e vida pública.",
        )
        val buildings = mutableListOf<BuildingState>()
        val companies = mutableListOf<CompanyState>()

        fun addBuilding(building: BuildingState) {
            buildings += building
        }

        companyNames.forEachIndexed { index, name ->
            val angle = index.toFloat() / companyNames.size * (Math.PI * 2).toFloat()
            val radius = if (index % 2 == 0) 27f else 34f
            val position = Vector2State(cos(angle) * radius, sin(angle) * radius)
            val companyId = "company_${index + 1}"
            val buildingId = "building_company_${index + 1}"
            val product = ProductState(
                id = "product_${index + 1}_1",
                companyId = companyId,
                name = productNames[index],
                stage = ProductStage.DEVELOPMENT,
                development = random.range(0.12f, 0.38f),
                quality = random.range(0.22f, 0.38f),
                innovation = random.range(0.3f, 0.65f),
                price = 90.0 + index * 24.0,
            )
            addBuilding(
                BuildingState(
                    id = buildingId,
                    cityId = city.id,
                    name = if (index == 0) "Monolito Hélio" else "Sede $name",
                    kind = if (index == 0) BuildingKind.HEADQUARTERS else BuildingKind.OFFICE,
                    position = position,
                    height = random.range(10f, 25f) + if (index == 0) 10f else 0f,
                    footprintX = random.range(7f, 10f),
                    footprintZ = random.range(7f, 10f),
                    companyId = companyId,
                    technology = 0.12f + index * 0.015f,
                    accentColor = companyAccent(index),
                ),
            )
            companies += CompanyState(
                id = companyId,
                name = name,
                strategy = CompanyStrategy.entries[index % CompanyStrategy.entries.size],
                cash = 65_000.0 + index * 9_000.0,
                buildingId = buildingId,
                departments = listOf(
                    DepartmentState("dep_${index + 1}_product", "Produto", 8_000.0, "Desenvolvimento e qualidade"),
                    DepartmentState("dep_${index + 1}_research", "Pesquisa", 5_000.0, "Pesquisa aplicada"),
                    DepartmentState("dep_${index + 1}_operations", "Operações", 4_000.0, "Operações e suporte"),
                ),
                products = listOf(product),
                technology = 0.12f + index * 0.015f,
            )
        }

        addBuilding(BuildingState("building_research", city.id, "Instituto do Conhecimento", BuildingKind.RESEARCH_LAB,
            Vector2State(-9f, 10f), 18f, 12f, 9f, technology = 0.18f, accentColor = 0xFF84D98AL))
        addBuilding(BuildingState("building_university", city.id, "Escola de Sistemas Vivos", BuildingKind.UNIVERSITY,
            Vector2State(11f, 12f), 9f, 13f, 8f, technology = 0.12f, accentColor = 0xFFA9D96BL))
        addBuilding(BuildingState("building_data", city.id, "Núcleo de Inferência", BuildingKind.DATA_CENTER,
            Vector2State(-14f, -11f), 7f, 11f, 9f, technology = 0.2f, accentColor = 0xFF6FD6DFL))
        addBuilding(BuildingState("building_square", city.id, "Praça do Girassol", BuildingKind.PUBLIC_SQUARE,
            Vector2State(0f, 0f), 0.5f, 15f, 15f, accentColor = 0xFFF2C14EL))
        addBuilding(BuildingState("building_cafe", city.id, "Café Semente", BuildingKind.CAFE,
            Vector2State(10f, -8f), 3.5f, 7f, 6f, accentColor = 0xFFFFD166L))
        addBuilding(BuildingState("building_transit", city.id, "Terminal Horizonte", BuildingKind.TRANSIT_HUB,
            Vector2State(0f, -29f), 5f, 16f, 6f, accentColor = 0xFF9C8CE8L))

        val homeIds = (1..8).map { index ->
            val column = (index - 1) % 4
            val row = (index - 1) / 4
            val id = "building_home_$index"
            addBuilding(BuildingState(
                id = id, cityId = city.id, name = "Residencial Ipê $index", kind = BuildingKind.RESIDENCE,
                position = Vector2State(-48f + column * 11f, 42f + row * 12f),
                height = 7f + (index % 3) * 2f, footprintX = 8f, footprintZ = 8f,
                accentColor = 0xFFD8B4A0L,
            ))
            id
        }

        val agents = (0 until population).map { index ->
            val profession = when {
                index < population * 2 / 10 -> Profession.RESEARCHER
                index < population * 5 / 10 -> Profession.ENGINEER
                index < population * 6 / 10 -> Profession.DESIGNER
                index < population * 7 / 10 -> Profession.MANAGER
                index < population * 8 / 10 -> Profession.OPERATOR
                index < population * 9 / 10 -> Profession.EDUCATOR
                else -> Profession.ARTISAN
            }
            val employer = when (profession) {
                Profession.EDUCATOR, Profession.ARTISAN -> null
                else -> companies[index % companies.size].id
            }
            val homeId = homeIds[index % homeIds.size]
            val home = buildings.first { it.id == homeId }
            val name = "${givenNames[index % givenNames.size]} ${surnames[(index * 7 + index / givenNames.size) % surnames.size]}"
            val personality = Personality(
                openness = random.range(0.2f, 0.95f),
                conscientiousness = random.range(0.25f, 0.95f),
                extraversion = random.range(0.15f, 0.95f),
                agreeableness = random.range(0.25f, 0.95f),
                ambition = random.range(0.2f, 0.95f),
            )
            val skills = skillsByProfession.getValue(profession).associateWith { random.range(0.18f, 0.62f) } +
                ("learning" to random.range(0.2f, 0.7f))
            AgentState(
                id = "agent_${index + 1}",
                name = name,
                appearance = Appearance(
                    height = random.range(0.88f, 1.13f),
                    build = random.range(0.25f, 0.8f),
                    skinTone = random.nextInt(5),
                    hairStyle = random.nextInt(6),
                    primaryColor = agentAccent(index),
                    accentColor = companyAccent(index % companyNames.size),
                ),
                personality = personality,
                goals = listOf(
                    GoalState("goal_${index + 1}_career", "Avançar como ${profession.displayName().lowercase()}", priority = personality.ambition),
                    GoalState("goal_${index + 1}_life", "Construir uma vida equilibrada em Aurora", progress = 0.2f, priority = 0.65f),
                ),
                knowledge = mapOf("Aurora" to 0.25f, "Girassol" to random.range(0.05f, 0.2f)),
                skills = skills,
                needs = Needs(
                    energy = random.range(62f, 95f), nutrition = random.range(55f, 90f),
                    social = random.range(40f, 85f), motivation = random.range(50f, 90f),
                    satisfaction = random.range(45f, 80f), stress = random.range(8f, 38f),
                ),
                routine = defaultRoutine(),
                profession = profession,
                employerId = employer,
                homeId = homeId,
                currentBuildingId = homeId,
                position = Vector2State(home.position.x + random.range(-2f, 2f), home.position.z + random.range(-2f, 2f)),
                salaryPerDay = 95.0 + skills.values.average() * 130.0 + if (profession == Profession.MANAGER) 45.0 else 0.0,
                lastUpdatedMinute = startMinute,
            )
        }

        val genesis = WorldEvent(
            id = 1, simMinute = startMinute, kind = WorldEventKind.GENESIS,
            title = "Aurora começou a existir",
            description = "$population habitantes, ${companies.size} empresas e ${buildings.size} edifícios foram inicializados com estado persistente.",
            companyIds = companies.map { it.id },
        )
        return WorldState(
            id = "world_aurora_${seed.toULong().toString(16)}",
            name = "Aurora",
            seed = seed,
            rngState = random.state,
            elapsedMinutes = startMinute,
            lastWallClockMs = nowWallClockMs,
            playerCapital = 100_000.0,
            cities = listOf(city),
            buildings = buildings,
            agents = agents,
            companies = companies,
            events = listOf(genesis),
            nextAgentId = population.toLong() + 1,
            nextCompanyId = companies.size.toLong() + 1,
            nextBuildingId = buildings.size.toLong() + 1,
            nextEventId = 2,
        )
    }

    fun defaultRoutine(): List<RoutineBlock> = listOf(
        RoutineBlock(0, 420, ActionType.SLEEP),
        RoutineBlock(420, 480, ActionType.EAT),
        RoutineBlock(480, 720, ActionType.WORK),
        RoutineBlock(720, 780, ActionType.EAT),
        RoutineBlock(780, 1_020, ActionType.WORK),
        RoutineBlock(1_020, 1_080, ActionType.EAT),
        RoutineBlock(1_080, 1_260, ActionType.SOCIALIZE),
        RoutineBlock(1_260, 1_440, ActionType.SLEEP),
    )

    internal fun companyAccent(index: Int): Long = listOf(
        0xFFF2C14EL, 0xFF6FD6DFL, 0xFF84D98AL, 0xFF9C8CE8L, 0xFFFF8E8EL, 0xFFA9D96BL,
    )[index % 6]

    private fun agentAccent(index: Int): Long = listOf(
        0xFF416C75L, 0xFF736287L, 0xFF6D7750L, 0xFF8A6651L, 0xFF4D648DL, 0xFF875D78L,
    )[index % 6]
}

fun Profession.displayName(): String = when (this) {
    Profession.ENGINEER -> "Engenharia"
    Profession.RESEARCHER -> "Pesquisa"
    Profession.DESIGNER -> "Design"
    Profession.MANAGER -> "Gestão"
    Profession.OPERATOR -> "Operações"
    Profession.EDUCATOR -> "Educação"
    Profession.ARTISAN -> "Produção"
    Profession.UNEMPLOYED -> "Sem ocupação"
}

fun ActionType.displayName(): String = when (this) {
    ActionType.SLEEP -> "Descansando"
    ActionType.EAT -> "Alimentando-se"
    ActionType.WORK -> "Trabalhando"
    ActionType.RESEARCH -> "Pesquisando"
    ActionType.LEARN -> "Aprendendo"
    ActionType.SOCIALIZE -> "Socializando"
    ActionType.RELAX -> "Recuperando o equilíbrio"
    ActionType.IDLE -> "Observando a cidade"
}
