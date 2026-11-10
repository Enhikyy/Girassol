package app.girassol.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "girassol-settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class GirassolSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val animationScale: Float = 1f,
    val remoteFallback: Boolean = false,
    val activeModelPath: String = "",
    val automaticSleep: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<GirassolSettings> = context.settingsStore.data.map { preferences ->
        GirassolSettings(
            themeMode = runCatching { ThemeMode.valueOf(preferences[THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            animationScale = preferences[ANIMATION] ?: 1f,
            remoteFallback = preferences[REMOTE] ?: false,
            activeModelPath = preferences[MODEL] ?: "",
            automaticSleep = preferences[SLEEP] ?: true,
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.settingsStore.edit { it[THEME] = value.name }
    suspend fun setAnimationScale(value: Float) = context.settingsStore.edit { it[ANIMATION] = value.coerceIn(0f, 2f) }
    suspend fun setRemoteFallback(value: Boolean) = context.settingsStore.edit { it[REMOTE] = value }
    suspend fun setActiveModel(value: String) = context.settingsStore.edit { it[MODEL] = value }
    suspend fun setAutomaticSleep(value: Boolean) = context.settingsStore.edit { it[SLEEP] = value }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ANIMATION = floatPreferencesKey("animation")
        val REMOTE = booleanPreferencesKey("remote_fallback")
        val MODEL = stringPreferencesKey("active_model")
        val SLEEP = booleanPreferencesKey("automatic_sleep")
    }
}
