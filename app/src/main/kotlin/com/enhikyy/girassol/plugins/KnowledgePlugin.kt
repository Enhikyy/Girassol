package com.enhikyy.girassol.plugins

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Conhecimento geral (REST Countries — SEM chave): fato curto sobre um país.
 * Alimenta a Faculdade/Bar: NPCs citam dados reais no modo OUTIE.
 */
object KnowledgePlugin : GirassolPlugin {
    override val name: String = "Países (REST Countries)"

    override suspend fun status(): String = "sem chave, sob demanda"

    override suspend fun execute(query: String): String? = withContext(Dispatchers.IO) {
        val name = query.trim()
        if (name.length < 3) return@withContext null
        runCatching {
            val conn = URL(
                "https://restcountries.com/v3.1/name/" +
                    URLEncoder.encode(name, "UTF-8") + "?fields=name,capital,population,region,languages",
            ).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val arr = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                val c = arr.optJSONObject(0) ?: return@withContext null
                val langs = c.optJSONObject("languages")?.let { jo ->
                    (0 until jo.length()).map { index ->
                        val key = jo.names()?.optString(index).orEmpty()
                        jo.optString(key)
                    }
                }?.joinToString(", ") ?: "?"
                "%s: capital %s, região %s, população %d, idiomas: %s".format(
                    c.getJSONObject("name").optString("common", "?"),
                    c.optJSONArray("capital")?.optString(0) ?: "?",
                    c.optString("region"),
                    c.optLong("population"),
                    langs,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
