package dev.labforge.app.integration.api

import com.llamatik.sdk.agent.AgentTool
import com.llamatik.sdk.agent.AgentToolResult
import dev.labforge.app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class PublicPost(
    val userId: Int = 0,
    val id: Int = 0,
    val title: String = "",
    val body: String = "",
)

interface PublicApi {
    @GET("posts")
    suspend fun posts(@Query("_limit") limit: Int = 3): List<PublicPost>
}

/** Retrofit + OkHttp + kotlinx.serialization, no auth, free public API. */
object PublicApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val api: PublicApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PublicApi::class.java)
    }
}

/**
 * Llamatik AgentTool backed by Retrofit. Registered in the Llamatik ToolRegistry
 * and callable both by the SDK executor and by Girassol's ReAct loop.
 */
class PublicApiTool : AgentTool {
    override val id = "public_api_posts"
    override val displayName = "Public API posts"
    override val description = "Fetches public posts from jsonplaceholder.typicode.com via Retrofit (no auth)."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "How many posts to fetch (1-10)")
            }
        }
    }

    override fun isSupported(): Boolean = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val limit = input["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 10) ?: 3
        return runCatching {
            val posts = PublicApiClient.api.posts(limit)
            if (posts.isEmpty()) {
                AgentToolResult.Failure("API returned no posts")
            } else {
                AgentToolResult.Success(
                    posts.joinToString("\n") { "#${it.id} ${it.title}\n${it.body.take(140)}" },
                )
            }
        }.getOrElse { AgentToolResult.Failure(it.message ?: "REST call failed") }
    }
}
