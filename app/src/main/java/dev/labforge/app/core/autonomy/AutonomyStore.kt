package dev.labforge.app.core.autonomy

import android.content.Context
import androidx.core.content.edit

/** Persisted outcome of the last night cycle, shown in the Autonomy screen. */
object AutonomyStore {

    private const val PREFS = "girassol_autonomy"
    private const val KEY_AT = "last_at"
    private const val KEY_OK = "last_ok"
    private const val KEY_MESSAGE = "last_message"
    private const val KEY_COUNT = "run_count"

    data class LastRun(val at: Long, val ok: Boolean, val message: String, val count: Int)

    fun last(context: Context): LastRun {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LastRun(
            at = prefs.getLong(KEY_AT, 0L),
            ok = prefs.getBoolean(KEY_OK, false),
            message = prefs.getString(KEY_MESSAGE, "").orEmpty(),
            count = prefs.getInt(KEY_COUNT, 0),
        )
    }

    fun record(context: Context, ok: Boolean, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(KEY_AT, System.currentTimeMillis())
            putBoolean(KEY_OK, ok)
            putString(KEY_MESSAGE, message.take(400))
            putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1)
        }
    }
}
