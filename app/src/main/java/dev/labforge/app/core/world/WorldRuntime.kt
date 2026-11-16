package dev.labforge.app.core.world

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Shared in-process world state with durable checkpoints and wall-clock catch-up. */
object WorldRuntime {
    private const val PREFS="girassol_world_runtime"
    private const val KEY_SNAPSHOT="snapshot"
    private const val KEY_LAST_WALL_TIME="last_wall_time"
    private const val KEY_ENABLED="enabled"
    private val lock=Any()
    private val engine=WorldSimulationEngine()
    private val mutableSnapshot=MutableStateFlow(WorldSnapshot.initial())
    val snapshot: StateFlow<WorldSnapshot> = mutableSnapshot.asStateFlow()
    @Volatile private var preferences: SharedPreferences?=null
    @Volatile private var initialized=false
    private var lastWallTime=0L

    fun initialize(context: Context) {
        synchronized(lock) {
            if(initialized) return
            val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            preferences=prefs
            mutableSnapshot.value=decode(prefs.getString(KEY_SNAPSHOT,null))
            lastWallTime=prefs.getLong(KEY_LAST_WALL_TIME,0L)
            val now=System.currentTimeMillis()
            if(lastWallTime>0L && now>lastWallTime) {
                mutableSnapshot.value=engine.advance(mutableSnapshot.value,(now-lastWallTime)/1_000.0)
            }
            lastWallTime=now
            initialized=true
            persistLocked()
        }
    }

    fun advance(context: Context, nowMillis: Long=System.currentTimeMillis()) {
        initialize(context)
        synchronized(lock) {
            val elapsed=(nowMillis-lastWallTime).coerceAtLeast(0L)
            if(elapsed>=1_000L) {
                mutableSnapshot.value=engine.advance(mutableSnapshot.value,elapsed/1_000.0)
                lastWallTime=nowMillis
                persistLocked()
            }
        }
    }

    fun flush(context: Context) {
        initialize(context)
        synchronized(lock) { persistLocked() }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        synchronized(lock) { preferences?.edit()?.putBoolean(KEY_ENABLED,enabled)?.apply() }
    }

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(KEY_ENABLED,false)

    private fun persistLocked() {
        val s=mutableSnapshot.value
        val root=JSONObject().put("simulatedMinutes",s.simulatedMinutes).put("eventCount",s.eventCount)
        val residents=JSONArray()
        s.residents.forEach { r ->
            residents.put(JSONObject()
                .put("id",r.id).put("name",r.name).put("x",r.x.toDouble()).put("z",r.z.toDouble())
                .put("energy",r.energy.toDouble()).put("social",r.social.toDouble())
                .put("knowledge",r.knowledge.toDouble()).put("focus",r.focus.toDouble())
                .put("action",r.action.name).put("innie",r.innie).put("cooperation",r.cooperation.toDouble()))
        }
        root.put("residents",residents)
        preferences?.edit()?.putString(KEY_SNAPSHOT,root.toString())?.putLong(KEY_LAST_WALL_TIME,lastWallTime)?.apply()
    }

    private fun decode(raw: String?): WorldSnapshot {
        if(raw.isNullOrBlank()) return WorldSnapshot.initial()
        return runCatching {
            val root=JSONObject(raw)
            val array=root.getJSONArray("residents")
            val residents=buildList {
                for(i in 0 until array.length()) {
                    val o=array.getJSONObject(i)
                    add(ResidentState(
                        id=o.getInt("id"),name=o.getString("name"),x=o.getDouble("x").toFloat(),z=o.getDouble("z").toFloat(),
                        energy=o.getDouble("energy").toFloat(),social=o.getDouble("social").toFloat(),
                        knowledge=o.getDouble("knowledge").toFloat(),focus=o.getDouble("focus").toFloat(),
                        action=ResidentAction.valueOf(o.getString("action")),innie=o.getBoolean("innie"),
                        cooperation=o.getDouble("cooperation").toFloat(),
                    ))
                }
            }
            WorldSnapshot(root.optLong("simulatedMinutes"),root.optLong("eventCount"),residents.ifEmpty { WorldSnapshot.initial().residents })
        }.getOrElse { WorldSnapshot.initial() }
    }
}
