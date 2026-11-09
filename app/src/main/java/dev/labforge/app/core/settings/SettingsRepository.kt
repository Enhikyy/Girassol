package dev.labforge.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore("labforge_settings")

data class AppSettings(
    val endpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.2,
    val maxSteps: Int = 15,
    val autoCommit: Boolean = true,
    val braveApiKey: String = "",
    val githubToken: String = "",
)

fun AppSettings.researchKeys() = dev.labforge.app.core.research.ResearchKeys(braveApiKey, githubToken)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val endpoint = stringPreferencesKey("endpoint")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val temperature = doublePreferencesKey("temperature")
        val maxSteps = intPreferencesKey("max_steps")
        val autoCommit = booleanPreferencesKey("auto_commit")
        val braveApiKey = stringPreferencesKey("brave_api_key")
        val githubToken = stringPreferencesKey("github_token")
    }

    private fun read(p: Preferences) = AppSettings(
        endpoint = p[Keys.endpoint] ?: "https://api.openai.com/v1",
        apiKey = p[Keys.apiKey] ?: "",
        model = p[Keys.model] ?: "gpt-4o-mini",
        temperature = p[Keys.temperature] ?: 0.2,
        maxSteps = p[Keys.maxSteps] ?: 15,
        autoCommit = p[Keys.autoCommit] ?: true,
        braveApiKey = p[Keys.braveApiKey] ?: "",
        githubToken = p[Keys.githubToken] ?: "",
    )

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map(::read)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            // Transform inside edit{}: a separate read-then-write loses concurrent changes.
            context.dataStore.edit { p ->
                val next = transform(read(p))
                p[Keys.endpoint] = next.endpoint
                p[Keys.apiKey] = next.apiKey
                p[Keys.model] = next.model
                p[Keys.temperature] = next.temperature
                p[Keys.maxSteps] = next.maxSteps
                p[Keys.autoCommit] = next.autoCommit
                p[Keys.braveApiKey] = next.braveApiKey
                p[Keys.githubToken] = next.githubToken
            }
        } catch (e: IOException) {
            // Keep the app alive on a full disk/corrupt preferences file.
        }
    }
}
