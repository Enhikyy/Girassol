package dev.labforge.app.integration.llamatik

import android.content.Context
import com.llamatik.sdk.agent.AgentActionLogRepository
import com.llamatik.sdk.agent.AgentExecutor
import com.llamatik.sdk.agent.AgentTool
import com.llamatik.sdk.agent.AgentToolResult
import com.llamatik.sdk.agent.ParsedToolCall
import com.llamatik.sdk.agent.ToolPermission
import com.llamatik.sdk.agent.ToolPermissionRepository
import com.llamatik.sdk.agent.ToolRegistry
import com.llamatik.sdk.agent.registerBuiltIns
import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.entitlement.EntitlementRepository
import com.llamatik.sdk.framework.AgentConfig
import com.llamatik.sdk.framework.AgentStreamChunk
import com.llamatik.sdk.framework.MemoryStorage
import com.llamatik.sdk.framework.PersistentMemory
import dev.labforge.app.core.AppSelection
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.research.ResearchEngine
import dev.labforge.app.core.research.ResearchRepository
import dev.labforge.app.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONArray
import org.json.JSONObject
import com.russhwolf.settings.SharedPreferencesSettings
import java.io.File

class FileMemoryStorage(private val root: File) : MemoryStorage {
    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(root, key).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        File(root, key).takeIf { it.exists() }?.readBytes()
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { File(root, key).delete() }
    }
}

private class AlwaysEntitled : EntitlementRepository {
    override val isPremium = MutableStateFlow(true)
    override suspend fun canUseCompanionMode() = true
    override suspend fun canImportCustomModels() = true
    override suspend fun canUseAgentTools() = true
    override suspend fun refreshEntitlements() = Unit
}

/** Llamatik AgentTool that reuses Girassol's free-API research engine. */
class ResearchAgentTool(private val context: Context) : AgentTool {
    override val id = "research_web"
    override val displayName = "Free web research"
    override val description = "Researches a theme on Wikipedia, OpenAlex, arXiv, Stack Overflow and DuckDuckGo, saving a local dossier."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("theme") { put("type", "string"); put("description", "Research theme") }
        }
    }

    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val theme = input["theme"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
            ?: return AgentToolResult.Failure("Missing 'theme'")
        return runCatching {
            val repo = ResearchRepository(File(context.filesDir, "labforge/research"))
            val keys = runCatching { dev.labforge.app.core.settings.SettingsRepository(context).settings.first() }.getOrNull()
                ?.let { dev.labforge.app.core.research.ResearchKeys(it.braveApiKey, it.githubToken) }
                ?: dev.labforge.app.core.research.ResearchKeys()
            val bundle = ResearchEngine(repo, keys).research(theme)
            AgentToolResult.Success(
                bundle.docs.take(6).joinToString("\n\n") {
                    "[${it.source}] ${it.title}\n${it.snippet.take(400)}\n${it.url}"
                },
            )
        }.getOrElse { AgentToolResult.Failure(it.message ?: "research failed") }
    }
}

/**
 * Girassol's Llamatik runtime. Uses the official SDK agent contracts (ToolRegistry,
 * AgentExecutor, PersistentMemory, AgentStreamChunk) while keeping inference and
 * training on Girassol's own llama.cpp engine. Llamatik's native core is excluded
 * because it ships a second, incompatible libllama.so.
 */
object LlamatikRuntime {

    @Volatile private var initialized = false
    lateinit var tools: ToolRegistry
        private set
    lateinit var memory: PersistentMemory
        private set
    lateinit var executor: AgentExecutor
        private set
    lateinit var config: AgentConfig
        private set

    private lateinit var appContext: Context
    private lateinit var settingsRepo: SettingsRepository

    val isInitialized: Boolean get() = initialized
    val sdkVersion: String = "2.2.2"

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            settingsRepo = SettingsRepository(appContext)
            val settings = SharedPreferencesSettings(
                appContext.getSharedPreferences("llamatik_runtime", android.content.Context.MODE_PRIVATE),
                false,
            )

            val registry = ToolRegistry()
            registry.registerBuiltIns()
            registry.register(PublicApiToolAdapter())
            registry.register(ResearchAgentTool(appContext))
            tools = registry

            val permissions = ToolPermissionRepository(settings)
            permissions.setAgentEnabled(true)
            registry.all().forEach {
                permissions.setPermission(ToolPermission(it.id, granted = true, requiresConfirmation = false))
            }
            val logs = AgentActionLogRepository(settings)
            executor = AgentExecutor(
                toolRegistry = registry,
                permissionRepository = permissions,
                logRepository = logs,
                entitlementRepository = AlwaysEntitled(),
                requestConfirmation = { _, _ -> true },
            )

            memory = PersistentMemory(
                storage = FileMemoryStorage(File(appContext.filesDir, "llamatik")),
                storageKey = "memory/conversation.txt",
                maxEntries = 200,
            )
            config = AgentConfig(
                name = "Girassol Llamatik",
                description = "Local autonomous agent",
                modelPath = AppSelection.modelPath,
                systemPrompt = SYSTEM,
                temperature = 0.2f,
                maxTokens = 1024,
                contextLength = 4096,
                memory = memory,
                toolRegistry = registry,
                executor = executor,
                maxToolRounds = 4,
            )
            initialized = true
        }
    }

    suspend fun restoreMemory() {
        if (initialized) runCatching { memory.restore() }
    }

    suspend fun persistMemory() {
        if (initialized) runCatching { memory.persist() }
    }

    fun toolIds(): List<String> = if (initialized) tools.all().map { it.id } else emptyList()
    fun memorySize(): Int = if (initialized) memory.size() else 0

    suspend fun runTool(toolId: String, input: JsonObject): AgentToolResult {
        if (!initialized) return AgentToolResult.Failure("runtime not initialized")
        val tool = tools.get(toolId) ?: return AgentToolResult.Failure("unknown tool: $toolId")
        return runCatching { executor.execute(ParsedToolCall(toolId, input)) }
            .getOrElse { AgentToolResult.Failure(it.message ?: "tool failed") }
    }

    suspend fun testPublicApi(): AgentToolResult =
        runTool("public_api_posts", JsonObject(emptyMap()))

    /** Streams tokens in Llamatik's AgentStreamChunk format, with tool rounds. */
    fun stream(prompt: String): Flow<AgentStreamChunk> = callbackFlow {
        if (!initialized) {
            trySend(AgentStreamChunk.Failure("Runtime não inicializado"))
            close(); return@callbackFlow
        }
        try {
            val cfg = settingsRepo.settings.first()
            if (cfg.apiKey.isBlank() && cfg.endpoint.contains("openai.com")) {
                trySend(AgentStreamChunk.Failure("Configure endpoint/API key em Ajustes para o streaming."))
                close(); return@callbackFlow
            }
            val client = LlmClient(cfg.endpoint, cfg.apiKey, cfg.model, cfg.temperature)
            streamWith(client, prompt) { trySend(it) }
        } catch (t: Throwable) {
            trySend(AgentStreamChunk.Failure(t.message ?: "stream failed"))
        }
        close()
    }

    /** Injectable for tests: same loop with a custom client. */
    suspend fun streamWith(
        client: LlmClient,
        prompt: String,
        onChunk: (AgentStreamChunk) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val specs = JSONArray().apply {
            tools.all().forEach { t ->
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", t.id)
                        put("description", t.description)
                        put("parameters", JSONObject(t.schema.toString()))
                    })
                })
            }
        }
        if (!initialized) error("runtime not initialized")

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM))
            memory.messages().takeLast(16).forEach { m ->
                put(JSONObject().put("role", m.role.name.lowercase()).put("content", m.content))
            }
            put(JSONObject().put("role", "user").put("content", prompt))
        }

        var final = ""
        var rounds = 0
        while (rounds < 4) {
            rounds++
            val reply = client.chat(messages, if (specs.length() > 0) specs else null) { text, reasoning ->
                if (!reasoning && text.isNotEmpty()) onChunk(AgentStreamChunk.Delta(text))
            }
            if (reply.toolCalls.isEmpty()) {
                final = reply.content.ifBlank { final }
                break
            }
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", reply.content.ifBlank { JSONObject.NULL })
                put("tool_calls", JSONArray().apply {
                    reply.toolCalls.forEach { call ->
                        put(JSONObject().apply {
                            put("id", call.id)
                            put("type", "function")
                            put("function", JSONObject().put("name", call.name).put("arguments", call.arguments))
                        })
                    }
                })
            })
            for (call in reply.toolCalls) {
                onChunk(AgentStreamChunk.ToolCallStarted(call.name, call.arguments.take(180)))
                val input = runCatching { Json.parseToJsonElement(call.arguments).let { it as? JsonObject } }
                    .getOrNull() ?: JsonObject(emptyMap())
                val result = runTool(call.name, input)
                val (summary, ok) = when (result) {
                    is AgentToolResult.Success -> result.outputSummary to true
                    is AgentToolResult.Failure -> result.errorMessage to false
                    AgentToolResult.PermissionDenied -> "permission denied" to false
                    AgentToolResult.Unsupported -> "unsupported" to false
                }
                onChunk(AgentStreamChunk.ToolResult(call.name, summary, ok))
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", summary),
                )
            }
        }

        if (final.isBlank()) final = "(sem resposta)"
        memory.add(ChatMessage(ChatMessage.Role.User, prompt))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, final))
        runCatching { memory.persist() }
        onChunk(AgentStreamChunk.Done(final))
        final
    }

    /** Sleep-cycle hook: flush memory and return a compact state summary. */
    suspend fun consolidate(): String {
        if (!initialized) return "runtime not initialized"
        persistMemory()
        return "memória consolidada: ${memory.size()} mensagens"
    }

    /** PublicApiTool is in integration.api; kept as adapter to avoid duplicate ids. */
    private class PublicApiToolAdapter : AgentTool {
        private val delegate = dev.labforge.app.integration.api.PublicApiTool()
        override val id = delegate.id
        override val displayName = delegate.displayName
        override val description = delegate.description
        override val schema = delegate.schema
        override fun isSupported() = delegate.isSupported()
        override suspend fun execute(input: JsonObject) = delegate.execute(input)
    }

    private val SYSTEM = """
Você é o Girassol, agente local em Android. Você tem ferramentas registradas no runtime
Llamatik. Use research_web para pesquisar temas e public_api_posts para testar APIs REST.
Responda em português do Brasil, de forma direta e verificável.
""".trimIndent()
}
