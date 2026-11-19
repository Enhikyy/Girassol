package com.enhikyy.girassol.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot

/** Os dois mundos do sistema Severance. */
enum class Realm { INNIE, OUTIE }

/** Uma memória episódica de um agente, ancorada no espaço do mundo. */
data class Memory(
    val id: Long,
    val agentId: Long,
    val realm: Realm,
    val content: String,
    val importance: Float,
    val x: Float,
    val z: Float,
    val timestamp: Long,
    /** Tick lógico do WorldCore em que a memória foi criada. */
    val worldTick: Long = 0L,
)

/** Região do mundo que dá acesso a um dos dois reinos. */
data class Geofence(
    val name: String,
    val realm: Realm,
    val x: Float,
    val z: Float,
    val radius: Float,
)

/**
 * Memória dual do Girassol.
 *
 * Cada memória pertence a um reino — INNIE (trabalho/universidade) ou OUTIE
 * (bar/praça) — e a recuperação filtra por três eixos ao mesmo tempo: agente,
 * reino e proximidade espacial. Um agente no bar não "lembra" do escritório,
 * mesmo que as duas memórias existam na mesma base.
 */
object SeveranceMemory {

    /** Limite de memórias guardadas; as menos importantes caem primeiro. */
    private const val MAX_MEMORIES = 4_000

    private val lock = Any()
    private val memories = ArrayList<Memory>(256)
    private var nextId = 1L

    private val _counts = MutableStateFlow(mapOf(Realm.INNIE to 0, Realm.OUTIE to 0))
    val counts: StateFlow<Map<Realm, Int>> = _counts.asStateFlow()

    private val _realm = MutableStateFlow(Realm.OUTIE)
    val currentRealm: StateFlow<Realm> = _realm.asStateFlow()

    /** Geofences iniciais: universidade (INNIE), praça e bar (OUTIE). */
    val geofences: List<Geofence> = listOf(
        Geofence("Universidade", Realm.INNIE, 0f, 0f, 50f),
        Geofence("Praça Central", Realm.OUTIE, 100f, 100f, 30f),
        Geofence("Bar do Neural", Realm.OUTIE, -50f, -50f, 20f),
    )

    /** Reino correspondente a uma posição, ou `null` fora de qualquer geofence. */
    fun realmAt(x: Float, z: Float): Realm? =
        geofences.firstOrNull { hypot(x - it.x, z - it.z) <= it.radius }?.realm

    fun setRealm(realm: Realm) {
        _realm.value = realm
    }

    /** Grava uma memória. Memórias com importância quase nula são descartadas. */
    fun remember(
        agentId: Long,
        realm: Realm,
        content: String,
        importance: Float,
        x: Float,
        z: Float,
        timestamp: Long = System.currentTimeMillis(),
        worldTick: Long = 0L,
    ): Memory? {
        if (importance <= 0.02f) return null
        synchronized(lock) {
            val memory = Memory(
                id = nextId++,
                agentId = agentId,
                realm = realm,
                content = content,
                importance = importance.coerceIn(0f, 1f),
                x = x,
                z = z,
                timestamp = timestamp,
                worldTick = worldTick,
            )
            memories += memory
            if (memories.size > MAX_MEMORIES) evictUnderLock()
            publishCountsUnderLock()
            return memory
        }
    }

    /**
     * Recupera memórias de um agente **naquele** reino, ordenadas por relevância.
     *
     * A relevância combina importância e proximidade à posição atual — memórias
     * formadas aqui tendem a ganhar das distantes.
     */
    fun recall(
        agentId: Long,
        realm: Realm,
        x: Float,
        z: Float,
        limit: Int = 8,
    ): List<Memory> = synchronized(lock) {
        memories
            .asSequence()
            .filter { it.agentId == agentId && it.realm == realm }
            .map { it to (it.importance - spatialPenalty(it, x, z)) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Todas as memórias de um agente, ambos os reinos (para inspeção). */
    fun allFor(agentId: Long): List<Memory> = synchronized(lock) {
        memories.filter { it.agentId == agentId }
    }

    fun total(): Int = synchronized(lock) { memories.size }

    /** Remove somente os nós explicitamente selecionados pela consolidação hipocampal. */
    fun removeByIds(ids: Set<Long>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            memories.removeAll { it.id in ids }
            publishCountsUnderLock()
        }
    }

    fun clear() {
        synchronized(lock) {
            memories.clear()
            publishCountsUnderLock()
        }
    }

    private fun spatialPenalty(memory: Memory, x: Float, z: Float): Float {
        val distance = hypot(memory.x - x, memory.z - z)
        return (distance / 400f).coerceIn(0f, 0.5f)
    }

    /** Remove as memórias menos importantes quando o teto é ultrapassado. */
    private fun evictUnderLock() {
        val toDrop = memories.size - MAX_MEMORIES
        if (toDrop <= 0) return
        val victims = memories.sortedBy { it.importance }.take(toDrop).toSet()
        memories.removeAll(victims)
    }

    private fun publishCountsUnderLock() {
        _counts.value = mapOf(
            Realm.INNIE to memories.count { it.realm == Realm.INNIE },
            Realm.OUTIE to memories.count { it.realm == Realm.OUTIE },
        )
    }

    @Suppress("unused")
    private fun distance(a: Memory, b: Memory): Float = abs(a.x - b.x) + abs(a.z - b.z)
}
