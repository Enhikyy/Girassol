package com.enhikyy.girassol.core.world

import kotlin.math.hypot

data class AffordanceAdvertisement(
    val sourceEntityId: String,
    val actionType: String,
    val x: Float,
    val z: Float,
    val broadcastRadius: Float,
    val advertisedRelief: Map<String, Double>,
    val maxConcurrentUsers: Int = 1
)

class SpatialStigmergyAndAffordanceGrid(
    private val width: Int,
    private val height: Int,
    private val cellSize: Float = 2.0f
) {
    private val advertisements = mutableMapOf<String, AffordanceAdvertisement>()
    private val reservations = mutableMapOf<String, MutableMap<String, Long>>() // entityId -> (agentId -> expireTick)
    private val pheromoneChannels = mutableMapOf<String, FloatArray>()

    fun depositPheromone(channel: String, worldX: Float, worldZ: Float, amount: Float) {
        val grid = pheromoneChannels.getOrPut(channel) { FloatArray(width * height) }
        val gx = (worldX / cellSize).toInt().coerceIn(0, width - 1)
        val gz = (worldZ / cellSize).toInt().coerceIn(0, height - 1)
        val idx = gz * width + gx
        grid[idx] = (grid[idx] + amount).coerceIn(0f, 100f)
    }

    fun samplePheromone(channel: String, worldX: Float, worldZ: Float): Float {
        val grid = pheromoneChannels[channel] ?: return 0f
        val gx = (worldX / cellSize).toInt().coerceIn(0, width - 1)
        val gz = (worldZ / cellSize).toInt().coerceIn(0, height - 1)
        return grid[gz * width + gx]
    }

    fun stepDiffusionAndEvaporation(evaporationRate: Float = 0.03f, diffusionRate: Float = 0.12f) {
        for ((_, grid) in pheromoneChannels) {
            val next = FloatArray(grid.size)
            for (z in 0 until height) {
                for (x in 0 until width) {
                    val idx = z * width + x
                    val center = grid[idx]
                    val north = if (z > 0) grid[(z - 1) * width + x] else center
                    val south = if (z < height - 1) grid[(z + 1) * width + x] else center
                    val west = if (x > 0) grid[z * width + (x - 1)] else center
                    val east = if (x < width - 1) grid[z * width + (x + 1)] else center
                    val avgNeighbors = (north + south + west + east) * 0.25f
                    val diffused = (1f - diffusionRate) * center + diffusionRate * avgNeighbors
                    next[idx] = (diffused * (1f - evaporationRate)).coerceAtLeast(0f)
                }
            }
            next.copyInto(grid)
        }
    }

    fun publishAdvertisement(ad: AffordanceAdvertisement) {
        advertisements[ad.sourceEntityId] = ad
    }

    fun purgeExpiredReservations(currentTick: Long) {
        for ((_, holders) in reservations) {
            holders.entries.removeIf { it.value <= currentTick }
        }
    }

    fun tryReserve(entityId: String, agentId: String, currentTick: Long, ttlTicks: Long = 30L): Boolean {
        val ad = advertisements[entityId] ?: return false
        val holders = reservations.getOrPut(entityId) { mutableMapOf() }
        holders.entries.removeIf { it.value <= currentTick }
        if (agentId in holders) {
            holders[agentId] = currentTick + ttlTicks
            return true
        }
        if (holders.size >= ad.maxConcurrentUsers) return false
        holders[agentId] = currentTick + ttlTicks
        return true
    }

    fun releaseReservation(entityId: String, agentId: String) {
        reservations[entityId]?.remove(agentId)
    }

    fun queryReachable(
        agentId: String,
        agentX: Float,
        agentZ: Float,
        perceptionRadius: Float,
        currentTick: Long
    ): List<Pair<AffordanceAdvertisement, Float>> {
        return advertisements.values.mapNotNull { ad ->
            val holders = reservations[ad.sourceEntityId].orEmpty()
            val activeCount = holders.count { it.value > currentTick && it.key != agentId }
            if (activeCount >= ad.maxConcurrentUsers) return@mapNotNull null

            val dist = hypot((ad.x - agentX).toDouble(), (ad.z - agentZ).toDouble()).toFloat()
            if (dist <= minOf(perceptionRadius, ad.broadcastRadius)) (ad to dist) else null
        }.sortedBy { it.second }
    }
}