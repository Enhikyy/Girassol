package dev.labforge.app.ui.world

import dev.labforge.app.core.world.Vector2State
import dev.labforge.app.core.world.WorldFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WorldCameraTest {
    @Test
    fun centerRayPointsFromEyeToTarget() {
        val camera = OrbitCamera().snapshot()
        val ray = WorldPicking.ray(camera, 400f, 300f, 800, 600)
        val expected = (camera.target - camera.eye).normalized()
        assertVectorClose(camera.eye, ray.origin)
        assertVectorClose(expected, ray.direction)
        assertEquals(1f, ray.direction.length(), 0.0001f)
    }

    @Test
    fun pickingUsesNearestPositiveIntersectionAndIgnoresObjectsBehindCamera() {
        val ray = WorldRay(WorldVec3(0f, 0f, 0f), WorldVec3(0f, 0f, -1f))
        val targets = listOf(
            WorldPickTarget("behind", WorldVec3(0f, 0f, 5f), 1f),
            WorldPickTarget("far", WorldVec3(0f, 0f, -12f), 1f),
            WorldPickTarget("near", WorldVec3(0f, 0f, -5f), 1f),
            WorldPickTarget("miss", WorldVec3(8f, 0f, -4f), 1f),
        )
        assertEquals("near", WorldPicking.nearest(ray, targets))
        assertNull(WorldPicking.intersectSphere(ray, WorldVec3(8f, 0f, -4f), 1f))
    }

    @Test
    fun residentWinsWhenRayAlsoIntersectsBuildingSphere() {
        val ray = WorldRay(WorldVec3(0f, 0f, 0f), WorldVec3(0f, 0f, -1f))
        val targets = listOf(
            WorldPickTarget("building", WorldVec3(0f, 0f, -5f), 2f),
            WorldPickTarget("agent", WorldVec3(0f, 0f, -12f), 1f, priority = 1),
        )
        assertEquals("agent", WorldPicking.nearest(ray, targets))
    }

    @Test
    fun rayInsideSphereReturnsExitRatherThanNegativeEntry() {
        val ray = WorldRay(WorldVec3(0f, 0f, 0f), WorldVec3(1f, 0f, 0f))
        val distance = WorldPicking.intersectSphere(ray, WorldVec3(0f, 0f, 0f), 2f)
        assertNotNull(distance)
        assertEquals(2f, distance!!, 0.0001f)
    }

    @Test
    fun cameraClampsInputAndConvergesToFocusedEntity() {
        val camera = OrbitCamera()
        camera.zoom(0.0001f)
        assertEquals(144f, camera.distance, 0.0001f)
        camera.zoom(0.0001f)
        assertEquals(145f, camera.distance, 0.0001f)
        repeat(20) { camera.zoom(2f) }
        assertEquals(10f, camera.distance, 0.01f)
        camera.rotate(0f, 10_000f)
        assertEquals(1.42f, camera.pitch, 0.0001f)
        camera.rotate(0f, -20_000f)
        assertEquals(0.18f, camera.pitch, 0.0001f)
        camera.focus(Vector2State(12f, -8f), height = 20f)
        repeat(120) { camera.animate() }
        val snapshot = camera.snapshot()
        assertEquals(12f, snapshot.targetX, 0.001f)
        assertEquals(-8f, snapshot.targetZ, 0.001f)
        assertEquals(7f, snapshot.targetY, 0.001f)
        assertEquals(27f, snapshot.distance, 0.001f)
    }

    @Test
    fun pickingTargetsComeFromRenderedPersistedGeometry() {
        val world = WorldFactory.create(seed = 91L, population = 8)
        val renderedAgents = world.agents.take(3)
        val targets = WorldPicking.targets(world, renderedAgents)
        assertEquals(world.buildings.size + renderedAgents.size, targets.size)
        assertTrue(world.agents.drop(3).none { agent -> targets.any { it.id == agent.id } })
        renderedAgents.forEach { agent ->
            val target = targets.single { it.id == agent.id }
            assertEquals(agent.position.x, target.center.x, 0f)
            assertEquals(agent.position.z, target.center.z, 0f)
            assertTrue(target.radius > 0f)
            assertTrue(target.priority > targets.first { it.id == world.buildings.first().id }.priority)
        }
    }

    private fun assertVectorClose(expected: WorldVec3, actual: WorldVec3) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
        assertEquals(expected.z, actual.z, 0.0001f)
        assertTrue(abs((actual - expected).length()) < 0.0002f)
    }
}
