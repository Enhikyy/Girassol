package dev.labforge.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("labforge_settings")

data class AppSettings(
    val endpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.2,
    val maxSteps: Int = 15,
    val autoCommit: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val endpoint = stringPreferencesKey("endpoint")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val temperature = doublePreferencesKey("temperature")
        val maxSteps = intPreferencesKey("max_steps")
        val autoCommit = booleanPreferencesKey("auto_commit")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            endpoint = p[Keys.endpoint] ?: "https://api.openai.com/v1",
            apiKey = p[Keys.apiKey] ?: "",
            model = p[Keys.model] ?: "gpt-4o-mini",
            temperature = p[Keys.temperature] ?: 0.2,
            maxSteps = p[Keys.maxSteps] ?: 15,
            autoCommit = p[Keys.autoCommit] ?: true,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        // Transform inside edit{}: reading the snapshot first and writing after is a
        // read-modify-write race that silently drops concurrent changes.
        context.dataStore.edit { p ->
            val current = AppSettings(
                endpoint = p[Keys.endpoint] ?: "https://api.openai.com/v1",
                apiKey = p[Keys.apiKey] ?: "",
                model = p[Keys.model] ?: "gpt-4o-mini",
                temperature = p[Keys.temperature] ?: 0.2,
                maxSteps = p[Keys.maxSteps] ?: 15,
                autoCommit = p[Keys.autoCommit] ?: true,
            )
            val next = transform(current)
            p[Keys.endpoint] = next.endpoint
            p[Keys.apiKey] = next.apiKey
            p[Keys.model] = next.model
            p[Keys.temperature] = next.temperature
            p[Keys.maxSteps] = next.maxSteps
            p[Keys.autoCommit] = next.autoCommit
        }
    }
}
