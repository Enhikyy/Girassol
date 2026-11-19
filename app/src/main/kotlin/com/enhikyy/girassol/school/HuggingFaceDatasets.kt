package com.enhikyy.girassol.school

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DatasetHit(
    val id: String,
    val downloads: Long,
    val likes: Long,
)

/** Descoberta de datasets públicos; a transferência permanece sujeita a aprovação. */
object HuggingFaceDatasets {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 12): List<DatasetHit> = withContext(Dispatchers.IO) {
        val q = query.trim().take(200)
        if (q.length < 2) return@withContext emptyList()
        val url = "https://huggingface.co/api/datasets?search=${URLEncoder.encode(q, "UTF-8")}&limit=${limit.coerceIn(1, 30)}&sort=downloads&direction=-1"
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", "GirassolAndroid/2.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val array = JSONArray(response.body?.string().orEmpty())
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optString("id")
                    if (id.isBlank()) return@mapNotNull null
                    DatasetHit(
                        id = id,
                        downloads = item.optLong("downloads"),
                        likes = item.optLong("likes"),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }
}
