package com.enhikyy.girassol.core.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.enhikyy.girassol.core.world.CanonicalEvent
import com.enhikyy.girassol.core.world.ProposalOrigin
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Drives
import com.enhikyy.girassol.data.Health
import org.json.JSONArray
import org.json.JSONObject

data class RestoredWorldProjection(
    val agents: List<Agent>,
    val tick: Long,
    val simulatedMs: Long,
    val generation: Int,
    val selectedId: Long?
)

object WorldCoreDatabase {
    @Volatile
    private var helper: WorldCoreOpenHelper? = null

    val isInitialized: Boolean
        get() = helper != null

    fun initialize(context: Context) {
        if (helper != null) return
        synchronized(this) {
            if (helper == null) helper = WorldCoreOpenHelper(context.applicationContext)
        }
    }

    internal fun writableDatabase(): SQLiteDatabase? = helper?.writableDatabase
    internal fun readableDatabase(): SQLiteDatabase? = helper?.readableDatabase
}

private class WorldCoreOpenHelper(context: Context) :
    SQLiteOpenHelper(context, "girassol_world.db", null, DATABASE_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL("PRAGMA synchronous=NORMAL")
        db.execSQL("PRAGMA mmap_size=268435456")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS canonical_events (
                event_id INTEGER PRIMARY KEY,
                schema_version INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                actor_id TEXT NOT NULL,
                action_type TEXT NOT NULL,
                target_id TEXT,
                origin TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                prev_hash TEXT NOT NULL,
                event_hash TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS world_checkpoints (
                tick INTEGER PRIMARY KEY,
                last_event_id INTEGER NOT NULL,
                state_json TEXT NOT NULL,
                rng_state_json TEXT NOT NULL,
                chain_hash TEXT NOT NULL,
                created_utc INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rsi_promotion_ledger (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                world_tick INTEGER NOT NULL,
                proposer_agent_id TEXT NOT NULL,
                baseline_hash TEXT NOT NULL,
                candidate_hash TEXT NOT NULL,
                promoted INTEGER NOT NULL CHECK (promoted IN (0, 1)),
                net_fitness_gain REAL NOT NULL,
                brier_before REAL NOT NULL,
                brier_after REAL NOT NULL,
                reason TEXT NOT NULL,
                created_utc INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subjective_beliefs (
                observer_agent_id TEXT NOT NULL,
                claim_key TEXT NOT NULL,
                domain TEXT NOT NULL,
                belief REAL NOT NULL,
                disbelief REAL NOT NULL,
                uncertainty REAL NOT NULL,
                base_rate REAL NOT NULL,
                provenance_event_id INTEGER,
                updated_tick INTEGER NOT NULL,
                PRIMARY KEY (observer_agent_id, claim_key)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ecological_niche_structures (
                structure_id TEXT PRIMARY KEY,
                structure_type TEXT NOT NULL,
                grid_x INTEGER NOT NULL,
                grid_z INTEGER NOT NULL,
                level INTEGER NOT NULL,
                structural_integrity REAL NOT NULL,
                builder_agent_id TEXT NOT NULL,
                built_at_tick INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_canonical_events_tick ON canonical_events(tick)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_canonical_events_actor ON canonical_events(actor_id, tick)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrações futuras devem preservar o event log; nunca apagar eventos.
        onCreate(db)
    }

    private companion object {
        const val DATABASE_VERSION = 1
    }
}

/**
 * Event store transacional. Cada agente emite um evento de resultado por tick;
 * o payload guarda o estado projetado necessário para recuperação determinística.
 */
object WorldCoreEventStore {
    private const val SCHEMA_VERSION = 1
    private const val GENESIS_HASH = "GENESIS"
    private const val CHECKPOINT_INTERVAL = 500L

    fun commitTick(
        tick: Long,
        agents: List<Agent>,
        simulatedMs: Long,
        generation: Int,
        selectedId: Long?,
        worldSeed: Long
    ): Boolean {
        val db = WorldCoreDatabase.writableDatabase() ?: return false
        require(agents.map { it.id }.distinct().size == agents.size) {
            "IDs de agente duplicados no snapshot do WorldCore"
        }

        db.beginTransaction()
        try {
            var lastEventId = 0L
            var previousHash = GENESIS_HASH
            var previousTick = 0L
            db.rawQuery(
                "SELECT event_id, tick, event_hash FROM canonical_events ORDER BY event_id DESC LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    lastEventId = cursor.getLong(0)
                    previousTick = cursor.getLong(1)
                    previousHash = cursor.getString(2)
                }
            }
            require(tick > previousTick) {
                "WorldCore tick não monotônico: recebido=$tick anterior=$previousTick"
            }

            for (agent in agents.sortedBy { it.id }) {
                val action = agent.lastIntention
                val payload = agentPayload(agent, tick, simulatedMs, generation, selectedId)
                val eventId = ++lastEventId
                val actorId = agent.id.toString()
                val hash = CanonicalEvent.computeHash(
                    schemaVersion = SCHEMA_VERSION,
                    eventId = eventId,
                    tick = tick,
                    actorId = actorId,
                    actionType = action,
                    targetId = null,
                    origin = ProposalOrigin.UTILITY_AI,
                    payloadJson = payload,
                    prevHash = previousHash
                )
                val values = ContentValues().apply {
                    put("event_id", eventId)
                    put("schema_version", SCHEMA_VERSION)
                    put("tick", tick)
                    put("actor_id", actorId)
                    put("action_type", action)
                    putNull("target_id")
                    put("origin", ProposalOrigin.UTILITY_AI.name)
                    put("payload_json", payload)
                    put("prev_hash", previousHash)
                    put("event_hash", hash)
                }
                db.insertOrThrow("canonical_events", null, values)
                previousHash = hash
            }

            if (tick % CHECKPOINT_INTERVAL == 0L) {
                val checkpoint = ContentValues().apply {
                    put("tick", tick)
                    put("last_event_id", lastEventId)
                    put("state_json", snapshotJson(agents, tick, simulatedMs, generation, selectedId))
                    put(
                        "rng_state_json",
                        JSONObject()
                            .put("worldSeed", worldSeed.toString())
                            .put("strategy", "sha256-derived-independent-streams")
                            .put("checkpointTick", tick)
                            .toString()
                    )
                    put("chain_hash", previousHash)
                    put("created_utc", System.currentTimeMillis())
                }
                db.insertWithOnConflict(
                    "world_checkpoints",
                    null,
                    checkpoint,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Restaura o último checkpoint e reaplica os eventos posteriores,
     * verificando continuidade e SHA-256 antes de expor o estado à UI.
     */
    fun restoreLatestProjection(): RestoredWorldProjection? {
        val db = WorldCoreDatabase.readableDatabase() ?: return null
        val agents = linkedMapOf<Long, Agent>()
        var tick = 0L
        var simulatedMs = 0L
        var generation = 0
        var selectedId: Long? = null
        var lastEventId = 0L
        var previousHash = GENESIS_HASH

        db.rawQuery(
            """
            SELECT tick, last_event_id, state_json, chain_hash
            FROM world_checkpoints ORDER BY tick DESC LIMIT 1
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                tick = cursor.getLong(0)
                lastEventId = cursor.getLong(1)
                val state = JSONObject(cursor.getString(2))
                simulatedMs = state.optLong("simulatedMs", 0L)
                generation = state.optInt("generation", 0)
                selectedId = state.optNullableLong("selectedId")
                val savedAgents = state.optJSONArray("agents") ?: JSONArray()
                for (index in 0 until savedAgents.length()) {
                    val agent = agentFromJson(savedAgents.getJSONObject(index))
                    agents[agent.id] = agent
                }
                previousHash = cursor.getString(3)
            }
        }

        db.rawQuery(
            """
            SELECT event_id, schema_version, tick, actor_id, action_type, target_id,
                   origin, payload_json, prev_hash, event_hash
            FROM canonical_events WHERE event_id > ? ORDER BY event_id ASC
            """.trimIndent(),
            arrayOf(lastEventId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(0)
                val schemaVersion = cursor.getInt(1)
                val eventTick = cursor.getLong(2)
                val actorId = cursor.getString(3)
                val actionType = cursor.getString(4)
                val targetId = if (cursor.isNull(5)) null else cursor.getString(5)
                val origin = ProposalOrigin.valueOf(cursor.getString(6))
                val payload = cursor.getString(7)
                val prevHash = cursor.getString(8)
                val eventHash = cursor.getString(9)

                check(eventId == lastEventId + 1L) { "Lacuna no eventId do WorldCore: $eventId" }
                check(prevHash == previousHash) { "Cadeia SHA-256 quebrada no evento $eventId" }
                val computed = CanonicalEvent.computeHash(
                    schemaVersion, eventId, eventTick, actorId, actionType,
                    targetId, origin, payload, prevHash
                )
                check(computed == eventHash) { "Hash inválido no evento $eventId" }

                val payloadObject = JSONObject(payload)
                val agent = agentFromJson(payloadObject.getJSONObject("agent"))
                agents[agent.id] = agent
                tick = eventTick
                simulatedMs = payloadObject.optLong("simulatedMs", simulatedMs)
                generation = payloadObject.optInt("generation", generation)
                selectedId = payloadObject.optNullableLong("selectedId")
                lastEventId = eventId
                previousHash = eventHash
            }
        }

        if (tick <= 0L || agents.isEmpty()) return null
        return RestoredWorldProjection(
            agents = agents.values.sortedBy { it.id },
            tick = tick,
            simulatedMs = simulatedMs,
            generation = generation,
            selectedId = selectedId
        )
    }

    private fun agentPayload(
        agent: Agent,
        tick: Long,
        simulatedMs: Long,
        generation: Int,
        selectedId: Long?
    ): String = JSONObject()
        .put("agent", agentJson(agent))
        .put("tick", tick)
        .put("simulatedMs", simulatedMs)
        .put("generation", generation)
        .put("selectedId", selectedId ?: JSONObject.NULL)
        .toString()

    private fun snapshotJson(
        agents: List<Agent>,
        tick: Long,
        simulatedMs: Long,
        generation: Int,
        selectedId: Long?
    ): String = JSONObject()
        .put("tick", tick)
        .put("simulatedMs", simulatedMs)
        .put("generation", generation)
        .put("selectedId", selectedId ?: JSONObject.NULL)
        .put("agents", JSONArray().apply {
            agents.sortedBy { it.id }.forEach { put(agentJson(it)) }
        })
        .toString()

    private fun agentJson(agent: Agent): JSONObject = JSONObject()
        .put("id", agent.id)
        .put("name", agent.name)
        .put("role", agent.role)
        .put("energy", agent.drives.energy.toDouble())
        .put("social", agent.drives.social.toDouble())
        .put("knowledge", agent.drives.knowledge.toDouble())
        .put("focus", agent.drives.focus.toDouble())
        .put("isInnie", agent.isInnie)
        .put("x", agent.x.toDouble())
        .put("z", agent.z.toDouble())
        .put("generation", agent.generation)
        .put("health", agent.health.name)
        .put("fitness", agent.fitness.toDouble())
        .put("lastIntention", agent.lastIntention)

    private fun agentFromJson(json: JSONObject): Agent = Agent(
        id = json.getLong("id"),
        name = json.getString("name"),
        role = json.getString("role"),
        drives = Drives(
            energy = json.getDouble("energy").toFloat(),
            social = json.getDouble("social").toFloat(),
            knowledge = json.getDouble("knowledge").toFloat(),
            focus = json.getDouble("focus").toFloat()
        ),
        isInnie = json.getBoolean("isInnie"),
        x = json.getDouble("x").toFloat(),
        z = json.getDouble("z").toFloat(),
        generation = json.optInt("generation", 0),
        health = runCatching { Health.valueOf(json.optString("health", Health.HEALTHY.name)) }
            .getOrDefault(Health.HEALTHY),
        fitness = json.optDouble("fitness", 0.0).toFloat(),
        lastIntention = json.optString("lastIntention", "—")
    )

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)
}
