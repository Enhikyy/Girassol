package dev.labforge.app.ui.world

import dev.labforge.app.core.world.AgentState
import dev.labforge.app.core.world.Vector2State
import dev.labforge.app.core.world.WorldState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class WorldVec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: WorldVec3) = WorldVec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: WorldVec3) = WorldVec3(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float) = WorldVec3(x * value, y * value, z * value)
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): WorldVec3 = length().takeIf { it > 0.00001f }?.let { this * (1f / it) } ?: WorldVec3(0f, 0f, -1f)
    fun cross(other: WorldVec3) = WorldVec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )
    fun dot(other: WorldVec3): Float = x * other.x + y * other.y + z * other.z
}

data class WorldRay(val origin: WorldVec3, val direction: WorldVec3)
data class WorldPickTarget(
    val id: String,
    val center: WorldVec3,
    val radius: Float,
    val priority: Int = 0,
)

class OrbitCamera {
    var targetX = 0f
        private set
    var targetY = 3f
        private set
    var targetZ = 0f
        private set
    var yaw = -0.72f
        private set
    var pitch = 0.72f
        private set
    var distance = 72f
        private set

    private var desiredX = targetX
    private var desiredY = targetY
    private var desiredZ = targetZ
    private var desiredDistance = distance

    @Synchronized
    fun rotate(deltaX: Float, deltaY: Float) {
        yaw = (yaw + deltaX * 0.007f) % (Math.PI * 2).toFloat()
        pitch = (pitch + deltaY * 0.006f).coerceIn(0.18f, 1.42f)
        desiredX = targetX
        desiredY = targetY
        desiredZ = targetZ
        desiredDistance = distance
    }

    @Synchronized
    fun zoom(scale: Float) {
        distance = (distance / scale.coerceIn(0.5f, 2f)).coerceIn(10f, 145f)
        desiredDistance = distance
    }

    @Synchronized
    fun pan(deltaX: Float, deltaY: Float) {
        val scale = distance * 0.0014f
        val rightX = cos(yaw)
        val rightZ = -sin(yaw)
        val forwardX = -sin(yaw)
        val forwardZ = -cos(yaw)
        targetX = (targetX - deltaX * scale * rightX + deltaY * scale * forwardX).coerceIn(-85f, 85f)
        targetZ = (targetZ - deltaX * scale * rightZ + deltaY * scale * forwardZ).coerceIn(-85f, 85f)
        desiredX = targetX
        desiredY = targetY
        desiredZ = targetZ
    }

    @Synchronized
    fun focus(point: Vector2State, height: Float = 2f) {
        desiredX = point.x
        desiredY = (height * 0.35f).coerceIn(1.5f, 10f)
        desiredZ = point.z
        desiredDistance = if (height > 5f) 27f else 15f
    }

    @Synchronized
    fun animate() {
        val blend = 0.12f
        targetX += (desiredX - targetX) * blend
        targetY += (desiredY - targetY) * blend
        targetZ += (desiredZ - targetZ) * blend
        distance += (desiredDistance - distance) * blend
    }

    @Synchronized
    fun snapshot(): OrbitCameraSnapshot = OrbitCameraSnapshot(targetX, targetY, targetZ, yaw, pitch, distance)
}

data class OrbitCameraSnapshot(
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val yaw: Float,
    val pitch: Float,
    val distance: Float,
) {
    val target: WorldVec3 get() = WorldVec3(targetX, targetY, targetZ)
    val eye: WorldVec3
        get() {
            val horizontal = cos(pitch) * distance
            return WorldVec3(
                targetX + sin(yaw) * horizontal,
                targetY + sin(pitch) * distance,
                targetZ + cos(yaw) * horizontal,
            )
        }
}

object WorldPicking {
    fun ray(camera: OrbitCameraSnapshot, x: Float, y: Float, width: Int, height: Int): WorldRay {
        require(width > 0 && height > 0)
        val eye = camera.eye
        val forward = (camera.target - eye).normalized()
        val right = forward.cross(WorldVec3(0f, 1f, 0f)).normalized()
        val up = right.cross(forward).normalized()
        val ndcX = 2f * x / width - 1f
        val ndcY = 1f - 2f * y / height
        val tangent = tan(Math.toRadians(45.0) / 2.0).toFloat()
        val aspect = width.toFloat() / height
        val direction = (forward + right * (ndcX * tangent * aspect) + up * (ndcY * tangent)).normalized()
        return WorldRay(eye, direction)
    }

    fun nearest(ray: WorldRay, targets: List<WorldPickTarget>): String? {
        var nearestTarget: WorldPickTarget? = null
        var nearestDistance = Float.POSITIVE_INFINITY
        targets.forEach { target ->
            val distance = intersectSphere(ray, target.center, target.radius) ?: return@forEach
            val current = nearestTarget
            if (current == null || target.priority > current.priority ||
                (target.priority == current.priority && distance < nearestDistance)
            ) {
                nearestTarget = target
                nearestDistance = distance
            }
        }
        return nearestTarget?.id
    }

    fun intersectSphere(ray: WorldRay, center: WorldVec3, radius: Float): Float? {
        val oc = ray.origin - center
        val b = oc.dot(ray.direction)
        val c = oc.dot(oc) - radius * radius
        val discriminant = b * b - c
        if (discriminant < 0f) return null
        val root = sqrt(discriminant)
        val near = -b - root
        val far = -b + root
        return when {
            near > 0.05f -> near
            far > 0.05f -> far
            else -> null
        }
    }

    fun targets(world: WorldState, renderedAgents: List<AgentState>): List<WorldPickTarget> = buildList {
        world.buildings.forEach { building ->
            add(WorldPickTarget(
                id = building.id,
                center = WorldVec3(building.position.x, building.height * 0.5f, building.position.z),
                radius = maxOf(building.footprintX, building.footprintZ, building.height * 0.45f) * 0.72f,
            ))
        }
        renderedAgents.forEach { agent ->
            add(WorldPickTarget(
                id = agent.id,
                center = WorldVec3(agent.position.x, agent.appearance.height * 0.8f, agent.position.z),
                radius = 1.05f,
                priority = 1,
            ))
        }
    }
}
