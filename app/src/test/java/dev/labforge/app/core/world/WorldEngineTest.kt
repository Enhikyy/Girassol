package dev.labforge.app.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WorldEngineTest {
    @Test
    fun sameSeedAndInputsProduceIdenticalWorld() {
        val a = WorldEngine(WorldFactory.create(seed = 42L, population = 32))
        val b = WorldEngine(WorldFactory.create(seed = 42L, population = 32))
        val focus = setOf("agent_1", "agent_7")
        val visible = setOf("agent_2", "agent_3", "agent_8")
        repeat(180) {
            a.step(10, wallClockMs = 1_000L, detailedIds = focus, visibleIds = visible)
            b.step(10, wallClockMs = 1_000L, detailedIds = focus, visibleIds = visible)
        }
        assertEquals(a.state, b.state)
        assertTrue(a.state.agents.any { it.activityHistory.isNotEmpty() })
        assertTrue(a.state.agents.any { it.memories.isNotEmpty() })
    }

    @Test
    fun criticalEnergyOverridesWorkRoutine() {
        val initial = WorldFactory.create(seed = 9L, population = 16)
        val tired = initial.agents.first().copy(
            needs = initial.agents.first().needs.copy(energy = 4f, nutrition = 90f),
            action = null,
        )
        val engine = WorldEngine(initial.copy(agents = initial.agents.map { if (it.id == tired.id) tired else it }))
        engine.step(60, detailedIds = setOf(tired.id))
        assertEquals(ActionType.SLEEP, engine.state.agent(tired.id)?.action?.type)
        assertTrue(engine.state.agent(tired.id)!!.needs.energy > 4f)
    }

    @Test
    fun lodUpdatesNearMediumAndFarAgentsAtDifferentRates() {
        val engine = WorldEngine(WorldFactory.create(seed = 7L, population = 300))
        val initialMinute = engine.state.elapsedMinutes
        engine.step(60)
        assertEquals(initialMinute + 60, engine.state.agent("agent_1")!!.lastUpdatedMinute)
        assertEquals(initialMinute, engine.state.agent("agent_300")!!.lastUpdatedMinute)
        engine.step(300)
        assertEquals(initialMinute + 360, engine.state.agent("agent_300")!!.lastUpdatedMinute)
        assertEquals(AgentLod.DETAILED, engine.lodFor("agent_300", 299, setOf("agent_300"), emptySet()))
        assertEquals(AgentLod.SIMPLIFIED, engine.lodFor("agent_200", 199, emptySet(), setOf("agent_200")))
        assertEquals(AgentLod.STATISTICAL, engine.lodFor("agent_200", 199, emptySet(), emptySet()))
    }

    @Test
    fun offlineResumeIsCompensatedAndCapped() {
        val engine = WorldEngine(WorldFactory.create(seed = 21L, population = 24, nowWallClockMs = 0L))
        val start = engine.state.elapsedMinutes
        val elapsed = engine.catchUp(8L * 24L * 60L * 60L * 1_000L)
        assertEquals(WorldEngine.MAX_OFFLINE_MINUTES, elapsed)
        assertEquals(start + WorldEngine.MAX_OFFLINE_MINUTES, engine.state.elapsedMinutes)
        assertTrue(engine.state.events.first().description.contains("não executou"))
        val after = engine.state.elapsedMinutes
        assertEquals(0, engine.catchUp(10L))
        assertEquals(after, engine.state.elapsedMinutes)
    }

    @Test
    fun economyLedgersBalanceAndProductsComeFromProduction() {
        val engine = WorldEngine(WorldFactory.create(seed = 77L, population = 72))
        repeat(4 * 32) { engine.step(360) }
        assertTrue(engine.state.economyHistory.size >= 30)
        assertTrue(engine.state.companies.any { company -> company.products.any { it.stage == ProductStage.RELEASED } })
        engine.state.economyHistory.forEach { metric ->
            assertEquals(metric.revenue - metric.expenses, metric.profit, 0.001)
        }
        engine.state.companies.filterNot { it.bankrupt }.forEach { company ->
            val latest = company.metrics.last()
            assertEquals(latest.revenue - latest.expenses, latest.profit, 0.001)
            assertEquals(engine.state.agents.count { it.employerId == company.id }, latest.employees)
            assertTrue(latest.productivity in 0f..1f)
            assertTrue(latest.marketShare in 0f..1f)
        }
        val revenueFromProducts = engine.state.companies.sumOf { company ->
            company.products.sumOf { product -> product.totalRevenue }
        }
        assertTrue(revenueFromProducts > 0.0)
    }

    @Test
    fun bankruptcyReleasesEmployeesAndIsRecorded() {
        val initial = WorldFactory.create(seed = 11L, population = 24)
        val movedToEndOfDay = initial.copy(
            elapsedMinutes = 1_400L,
            agents = initial.agents.map { it.copy(lastUpdatedMinute = 1_400L) },
            companies = initial.companies.mapIndexed { index, company ->
                if (index == 0) company.copy(cash = -30_000.0) else company
            },
        )
        val engine = WorldEngine(movedToEndOfDay)
        val failedCompanyId = initial.companies.first().id
        val releasedAgents = initial.agents.filter { it.employerId == failedCompanyId }
        val releasedIds = releasedAgents.map { it.id }.toSet()
        engine.step(60)
        assertTrue(engine.state.companies.first().bankrupt)
        releasedAgents.forEach { before ->
            val after = engine.state.agent(before.id)!!
            assertEquals(before.wallet + before.salaryPerDay, after.wallet, 0.0001)
        }
        val finalEmployers = releasedIds.associateWith { id -> engine.state.agent(id)?.employerId }
        assertTrue("Vínculos finais: $finalEmployers", finalEmployers.values.all { it != failedCompanyId })
        assertTrue(finalEmployers.values.filterNotNull().all { employerId ->
            engine.state.companies.singleOrNull { it.id == employerId }?.bankrupt == false
        })
        assertTrue(engine.state.events.any {
            it.kind == WorldEventKind.BANKRUPTCY && failedCompanyId in it.companyIds
        })
    }

    @Test
    fun userEventsAndFoundingChangeActualSimulationState() {
        val engine = WorldEngine(WorldFactory.create(seed = 5L, population = 16))
        val socialBefore = engine.state.agents.map { it.needs.social }
        engine.applyHappening(HappeningType.FESTIVAL, "Teste de festival")
        engine.state.agents.forEachIndexed { index, agent ->
            assertTrue(agent.needs.social >= socialBefore[index])
        }
        val capital = engine.state.playerCapital
        val buildings = engine.state.buildings.size
        engine.foundCompany("Estúdio Real", CompanyStrategy.RESEARCH)
        assertEquals(capital - WorldEngine.FOUNDING_COST, engine.state.playerCapital, 0.0)
        assertEquals(buildings + 1, engine.state.buildings.size)
        assertNotNull(engine.state.companies.single { it.name == "Estúdio Real" }.products.single())
    }

    @Test
    fun girassolKnowledgeAndJobsUpdateRealIntegrationState() {
        val engine = WorldEngine(WorldFactory.create(seed = 5L, population = 16))
        val before = engine.state.building("building_research")!!.technology
        engine.syncKnowledge(3, "JNI seguro")
        assertEquals(3, engine.state.integration.knowledgeCount)
        assertTrue(engine.state.building("building_research")!!.technology > before)
        assertTrue(engine.state.agents.filter { it.profession == Profession.RESEARCHER }.all { it.memories.isNotEmpty() })
        engine.syncJob("lora", "Treino real", 0.5f, true, "", 0L)
        assertEquals("lora", engine.state.integration.activeJobKind)
        assertEquals(0.5f, engine.state.integration.activeJobProgress)
        engine.syncJob("lora", "Treino real", 1f, false, "Adaptador salvo", 99L)
        assertEquals("Adaptador salvo", engine.state.integration.lastJobSummary)
        assertTrue(engine.state.events.any { it.kind == WorldEventKind.INTEGRATION && it.description.contains("Adaptador salvo") })
    }

    @Test
    fun staleJobCompletionsCannotRewriteHistoryOrRewardTechnology() {
        val engine = WorldEngine(WorldFactory.create(seed = 5L, population = 16))
        engine.syncJob("lora", "Treino novo", 1f, false, "Concluído", 100L)
        val completed = engine.state
        val technology = completed.building("building_data")!!.technology

        engine.syncJob("lora", "Treino antigo", 1f, false, "Desatualizado", 99L)
        assertEquals(completed.integration, engine.state.integration)
        assertEquals(completed.events, engine.state.events)
        assertEquals(technology, engine.state.building("building_data")!!.technology)

        engine.syncJob("lora", "Treino duplicado", 1f, false, "Duplicado", 100L)
        assertEquals(completed.integration, engine.state.integration)
        assertEquals(completed.events, engine.state.events)
        assertEquals(technology, engine.state.building("building_data")!!.technology)
    }

    @Test
    fun schemaValidationRejectsVersionsWithoutAnExplicitReader() {
        val world = WorldFactory.create(seed = 5L, population = 8)
        listOf(-1, 0, WORLD_SCHEMA_VERSION + 1).forEach { version ->
            assertThrows(IllegalArgumentException::class.java) {
                WorldEngine.validate(world.copy(schemaVersion = version))
            }
        }
        WorldEngine.validate(world)
    }

    @Test
    fun differentSeedsDoNotProduceTheSameHistory() {
        val a = WorldEngine(WorldFactory.create(seed = 1L, population = 16))
        val b = WorldEngine(WorldFactory.create(seed = 2L, population = 16))
        repeat(20) { a.step(60); b.step(60) }
        assertNotEquals(a.state.agents.map { it.action }, b.state.agents.map { it.action })
    }
}
