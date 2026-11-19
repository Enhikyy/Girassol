package com.enhikyy.girassol.core.integration

import com.enhikyy.girassol.core.cognition.AllostaticThermalCoupling
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Drives
import com.enhikyy.girassol.cognitive.Intention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeaDecisionBridgeTest {
    @Test
    fun sameSnapshotAndTickProduceSameDecision() {
        val agent = Agent(
            id = 41L,
            name = "Agente de teste",
            role = "Pesquisador",
            drives = Drives(energy = 0.62f, social = 0.35f, knowledge = 0.22f, focus = 0.7f),
            x = 8f,
            z = -12f
        )
        val neighbours = listOf(
            Agent(
                id = 42L,
                name = "Vizinho",
                role = "Artesão",
                drives = Drives(energy = 0.2f, social = 0.4f, knowledge = 0.3f, focus = 0.5f),
                x = 12f,
                z = -8f
            )
        )

        GeaDecisionBridge.updateThermalTier(AllostaticThermalCoupling.ThermalTier.NONE_OR_LIGHT)
        val first = GeaDecisionBridge.decide(agent, neighbours, tick = 77L, dtSeconds = 0.1f)
        val second = GeaDecisionBridge.decide(agent, neighbours, tick = 77L, dtSeconds = 0.1f)

        assertEquals(first.intention, second.intention)
        assertEquals(first.score, second.score, 0.000001f)
        assertEquals(first.rationale, second.rationale)
    }

    @Test
    fun decisionAlwaysBelongsToDeclaredActionSpace() {
        val agent = Agent(
            id = 99L,
            name = "Agente",
            role = "Explorador",
            drives = Drives(energy = 0.9f, social = 0.1f, knowledge = 0.1f, focus = 0.8f)
        )

        val decision = GeaDecisionBridge.decide(agent, emptyList(), tick = 4L, dtSeconds = 0.1f)

        assertTrue(Intention.entries.contains(decision.intention))
        assertTrue(decision.rationale.contains("GEA G(pi)="))
    }
}
