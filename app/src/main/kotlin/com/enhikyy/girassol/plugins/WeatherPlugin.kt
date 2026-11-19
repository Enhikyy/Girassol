package com.enhikyy.girassol.plugins

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Clima (Open-Meteo — API gratuita e SEM chave).
 * Usado no MUNDO: ambiente da praça (OUTIE) reflete clima real opcional.
 * Offline => null (o mundo simulado segue com seu clima proprio).
 */
object WeatherPlugin : GirassolPlugin {
    override val name: String = "Clima (Open-Meteo)"

    override suspend fun status(): String = "sem chave, sob demanda"

    override suspend fun execute(query: String): String? = withContext(Dispatchers.IO) {
        val parts = query.split(",")
        if (parts.size != 2) return@withContext null
        val lat = parts[0].trim().toFloatOrNull() ?: return@withContext null
        val lon = parts[1].trim().toFloatOrNull() ?: return@withContext null
        if (lat !in -90f..90f || lon !in -180f..180f) return@withContext null

        runCatching {
            val conn = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&timezone=auto&forecast_days=1",
            ).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val cur = json.getJSONObject("current")
                "Clima real agora: %.1f°C, umidade %d%%, vento %.0f km/h (codigo %d)".format(
                    cur.getDouble("temperature_2m"),
                    cur.getInt("relative_humidity_2m"),
                    cur.getDouble("wind_speed_10m"),
                    cur.getInt("weather_code"),
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
