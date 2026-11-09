package dev.labforge.app.core.agent

import dev.labforge.app.core.research.ResearchEngine
import dev.labforge.app.integration.llamatik.LlamatikRuntime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import dev.labforge.app.core.research.ResearchRepository
import dev.labforge.app.core.term.GitRepo
import dev.labforge.app.core.term.ShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ToolResult(val ok: Boolean, val output: String)

abstract class AgentTool(protected val workspace: File) {
    abstract val name: String
    abstract val description: String
    abstract fun parameters(): JSONObject
    abstract suspend fun execute(args: JSONObject): ToolResult

    fun spec(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", parameters())
        })
    }

    protected fun resolve(path: String): File {
        require(!path.contains('\u0000')) { "caminho inválido" }
        val cleaned = path.trim().removePrefix("/").ifBlank { "." }
        val f = File(workspace, cleaned).canonicalFile
        val root = workspace.canonicalFile
        require(f.path == root.path || f.path.startsWith(root.path + File.separator)) {
            "caminho fora do workspace: $path"
        }
        return f
    }

    /**
     * Same as [resolve] but refuses to touch git internals: `.git/hooks` and
     * `.git/config` are executable surfaces, so a write there would let the agent
     * escalate from "edit files" to "run arbitrary code".
     */
    protected fun resolveWritable(path: String): File {
        val f = resolve(path)
        val gitDir = File(workspace.canonicalFile, ".git").canonicalFile
        require(!(f.path == gitDir.path || f.path.startsWith(gitDir.path + File.separator))) {
            "escrita em .git é bloqueada: $path"
        }
        return f
    }

    protected fun arr(vararg names: String) = JSONArray().apply { names.forEach { put(it) } }

    protected fun schema(props: JSONObject, required: List<String> = emptyList()): JSONObject =
        JSONObject().apply {
            put("type", "object")
            put("properties", props)
            if (required.isNotEmpty()) put("required", JSONArray(required))
        }

    protected fun prop(type: String, desc: String): JSONObject =
        JSONObject().apply { put("type", type); put("description", desc) }
}

class ListDirTool(ws: File) : AgentTool(ws) {
    override val name = "list_dir"
    override val description = "Lista arquivos e pastas do workspace."
    override fun parameters() = schema(JSONObject().apply {
        put("path", prop("string", "Diretório relativo (padrão: .)"))
    })
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir = resolve(args.optString("path", "."))
            require(dir.isDirectory) { "não é diretório: ${args.optString("path")}" }
            val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
            val text = entries.take(300).joinToString("\n") { f ->
                val kind = if (f.isDirectory) "dir " else "file"
                "$kind  ${f.name}${if (f.isFile) "  (${f.length()} B)" else ""}"
            }.ifBlank { "(vazio)" }
            ToolResult(true, text)
        }.getOrElse { ToolResult(false, it.message ?: "erro") }
    }
}

class ReadFileTool(ws: File) : AgentTool(ws) {
    override val name = "read_file"
    override val description = "Lê um arquivo de texto do workspace (até 60 KB)."
    override fun parameters() = schema(JSONObject().apply {
        put("path", prop("string", "Caminho relativo do arquivo"))
        put("offset", prop("integer", "Linha inicial (1-based, opcional)"))
        put("limit", prop("integer", "Máximo de linhas (opcional)"))
    }, listOf("path"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val f = resolve(args.getString("path"))
            require(f.isFile) { "arquivo não encontrado" }
            require(f.length() <= 8_000_000) { "arquivo grande demais para read_file (use offset)" }
            val offset = (args.optInt("offset", 1)).coerceAtLeast(1) - 1
            val limit = args.optInt("limit", 1200).coerceIn(1, 2000)
            var total = 0L
            val out = ArrayList<String>(limit)
            f.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    total++
                    if (total > offset && out.size < limit) out += line
                }
            }
            val text = out.joinToString("\n")
            ToolResult(true, text.take(60_000) + if (total > offset + limit) "\n[... $total linhas no total]" else "")
        }.getOrElse { ToolResult(false, it.message ?: "erro") }
    }
}

class WriteFileTool(ws: File) : AgentTool(ws) {
    override val name = "write_file"
    override val description = "Cria ou sobrescreve um arquivo do workspace."
    override fun parameters() = schema(JSONObject().apply {
        put("path", prop("string", "Caminho relativo"))
        put("content", prop("string", "Conteúdo completo do arquivo"))
    }, listOf("path", "content"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val f = resolveWritable(args.getString("path"))
            f.parentFile?.mkdirs()
            val content = args.getString("content")
            f.writeText(content)
            ToolResult(true, "escrito ${f.relativeTo(workspace)} (${content.length} chars)")
        }.getOrElse { ToolResult(false, it.message ?: "erro") }
    }
}

class ApplyPatchTool(ws: File) : AgentTool(ws) {
    override val name = "apply_patch"
    override val description = "Edição cirúrgica: substitui um trecho exato por outro. O trecho antigo deve aparecer uma única vez."
    override fun parameters() = schema(JSONObject().apply {
        put("path", prop("string", "Caminho relativo"))
        put("find", prop("string", "Trecho exato a localizar"))
        put("replace", prop("string", "Novo trecho"))
    }, listOf("path", "find", "replace"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val f = resolveWritable(args.getString("path"))
            require(f.isFile) { "arquivo não encontrado" }
            require(f.length() <= 2_000_000) { "arquivo grande demais para apply_patch" }
            val text = f.readText()
            val find = args.getString("find")
            val occurrences = Regex(Regex.escape(find)).findAll(text).count()
            require(occurrences == 1) { "o trecho aparece $occurrences vezes; precisa ser único" }
            f.writeText(text.replace(find, args.getString("replace")))
            ToolResult(true, "patch aplicado em ${f.relativeTo(workspace)}")
        }.getOrElse { ToolResult(false, it.message ?: "erro") }
    }
}

class SearchCodeTool(ws: File) : AgentTool(ws) {
    override val name = "search_code"
    override val description = "Busca texto ou regex no workspace (equivalente ao ripgrep), com número de linha."
    override fun parameters() = schema(JSONObject().apply {
        put("query", prop("string", "Texto ou expressão regular"))
        put("regex", prop("boolean", "Interpretar query como regex (padrão false)"))
        put("max_results", prop("integer", "Máximo de resultados (padrão 40)"))
    }, listOf("query"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val query = args.getString("query")
            val useRegex = args.optBoolean("regex", false)
            val max = args.optInt("max_results", 40).coerceIn(1, 200)
            val regex = if (useRegex) Regex(query, RegexOption.IGNORE_CASE)
            else Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
            val out = StringBuilder()
            var hits = 0
            val skipDirs = setOf(".git", "models", "output", "runs", "build", ".gradle")
            workspace.walkTopDown()
                .onEnter { it.name !in skipDirs }
                .filter { it.isFile && it.length() < 1_000_000 }
                .forEach { file ->
                    if (hits >= max) return@forEach
                    if (file.extension.lowercase() in setOf("gguf", "dat", "bin", "so", "apk", "png", "jpg", "zip")) return@forEach
                    runCatching {
                        file.useLines { lines ->
                            lines.forEachIndexed { idx, line ->
                                if (hits < max && regex.containsMatchIn(line)) {
                                    out.append(file.relativeTo(workspace)).append(':').append(idx + 1)
                                        .append(": ").append(line.trim().take(240)).append('\n')
                                    hits++
                                }
                            }
                        }
                    }
                }
            ToolResult(true, if (hits == 0) "nenhum resultado para \"$query\"" else out.toString())
        }.getOrElse { ToolResult(false, it.message ?: "erro") }
    }
}

class TerminalTool(ws: File) : AgentTool(ws) {
    private val shell = ShellSession(ws)
    override val name = "run_terminal"
    override val description = "Executa comando shell no sandbox (sh/toybox). Sem python/pip/git nativos no Android; use as ferramentas git_*."
    override fun parameters() = schema(JSONObject().apply {
        put("command", prop("string", "Comando shell"))
        put("timeout_seconds", prop("integer", "Timeout em segundos (padrão 60, máx 300)"))
    }, listOf("command"))
    override suspend fun execute(args: JSONObject): ToolResult {
        val cmd = args.getString("command")
        val timeout = args.optInt("timeout_seconds", 60).coerceIn(1, 300).toLong()
        val r = shell.run(cmd, timeout)
        val text = buildString {
            append("$ ").append(cmd).append('\n')
            append(r.output).append('\n')
            append("[exit ").append(r.exitCode).append(", ").append(r.durationMs).append(" ms]")
        }
        return ToolResult(r.ok, text)
    }
}

class GitTool(ws: File, private val action: String) : AgentTool(ws) {
    override val name = "git_$action"
    override val description = when (action) {
        "status" -> "Mostra o estado do repositório git do workspace."
        "commit" -> "Faz commit de todas as mudanças (rede de segurança antes de edições drásticas)."
        "log" -> "Lista os últimos commits."
        "reset" -> "Volta o workspace para um commit anterior (git reset --hard). Use apenas se o código quebrou."
        else -> "Operação git"
    }
    override fun parameters() = schema(JSONObject().apply {
        put("dir", prop("string", "Pasta do repositório dentro do workspace (padrão: raiz; use a pasta clonada)"))
        if (action == "commit") put("message", prop("string", "Mensagem do commit"))
        if (action == "reset") put("rev", prop("string", "Revisão alvo (padrão HEAD~1)"))
        if (action == "log") put("limit", prop("integer", "Quantidade de commits (padrão 10)"))
    })
    override suspend fun execute(args: JSONObject): ToolResult {
        val repo = GitRepo(resolve(args.optString("dir").ifBlank { "." }))
        val output = when (action) {
            "status" -> repo.status()
            "commit" -> repo.commit(args.optString("message", "checkpoint do agente"))
            "log" -> repo.log(args.optInt("limit", 10))
            "reset" -> repo.resetHard(args.optString("rev", "HEAD~1"))
            else -> return ToolResult(false, "ação desconhecida")
        }
        return ToolResult(!output.startsWith("erro"), output)
    }
}

class MemorySaveTool(ws: File, private val lessons: LessonStore) : AgentTool(ws) {
    override val name = "memory_save"
    override val description = "Salva uma lição aprendida no caderno de estudos (para não repetir erros)."
    override fun parameters() = schema(JSONObject().apply {
        put("title", prop("string", "Título curto e buscável"))
        put("body", prop("string", "O problema, a causa e a solução"))
        put("tags", prop("array", "Tags (opcional)"))
    }, listOf("title", "body"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val tags = args.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }
                ?: emptyList()
            val l = lessons.save(args.getString("title"), args.getString("body"), tags)
            ToolResult(true, "lição salva: ${l.title}")
        }.getOrElse { ToolResult(false, it.message ?: "erro") }
    }
}

class MemorySearchTool(ws: File, private val lessons: LessonStore) : AgentTool(ws) {
    override val name = "memory_search"
    override val description = "Consulta o caderno de estudos antes de começar uma tarefa."
    override fun parameters() = schema(JSONObject().apply {
        put("query", prop("string", "Termos da tarefa ou erro"))
    }, listOf("query"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val found = lessons.search(args.getString("query"), 4)
        if (found.isEmpty()) ToolResult(true, "nenhuma lição encontrada")
        else ToolResult(true, found.joinToString("\n\n") { "## ${it.title}\n${it.body}" })
    }
}

class ResearchWebTool(ws: File, private val keys: dev.labforge.app.core.research.ResearchKeys = dev.labforge.app.core.research.ResearchKeys()) : AgentTool(ws) {
    override val name = "research_web"
    override val description = "Pesquisa um tema na web (Brave Search se configurado, GitHub, Wikipedia, OpenAlex, arXiv, Semantic Scholar, Stack Overflow, DuckDuckGo) e salva o material com fontes."
    override fun parameters() = schema(JSONObject().apply {
        put("theme", prop("string", "Tema ou pergunta de pesquisa"))
    }, listOf("theme"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val repo = ResearchRepository(File(workspace, "research"))
            val engine = ResearchEngine(repo, keys)
            val bundle = engine.research(args.getString("theme"))
            val text = bundle.docs.take(8).joinToString("\n\n") {
                "[${it.source}] ${it.title}\n${it.snippet.take(500)}\n${it.url}"
            }
            ToolResult(true, "pesquisa salva (${bundle.docs.size} fontes)\n\n$text")
        }.getOrElse { ToolResult(false, it.message ?: "falha na pesquisa") }
    }
}

class ResearchSearchTool(ws: File) : AgentTool(ws) {
    override val name = "research_search"
    override val description = "Busca no material de pesquisa já salvo (RAG local BM25), sem rede."
    override fun parameters() = schema(JSONObject().apply {
        put("query", prop("string", "Termos a recuperar"))
        put("k", prop("integer", "Quantidade de fontes (padrão 5)"))
    }, listOf("query"))
    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val repo = ResearchRepository(File(workspace, "research"))
            val bundle = repo.latest() ?: return@runCatching ToolResult(false, "nenhuma pesquisa salva")
            val docs = repo.retrieve(bundle, args.getString("query"), args.optInt("k", 5))
            ToolResult(true, docs.joinToString("\n\n") {
                "[${it.source}] ${it.title}\n${it.snippet.take(600)}\n${it.url}"
            })
        }.getOrElse { ToolResult(false, it.message ?: "falha na busca local") }
    }
}

class LlamatikToolBridge(private val toolId: String, ws: File) : AgentTool(ws) {
    override val name = toolId
    override val description = "Ferramenta registrada no Llamatik Runtime ($toolId)."
    override fun parameters(): JSONObject = runCatching {
        JSONObject(LlamatikRuntime.tools.get(toolId)?.schema?.toString() ?: "{\"type\":\"object\"}")
    }.getOrElse { JSONObject().put("type", "object") }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val input = runCatching { Json.parseToJsonElement(args.toString()) as? JsonObject }
            .getOrNull() ?: JsonObject(emptyMap())
        when (val result = LlamatikRuntime.runTool(toolId, input)) {
            is com.llamatik.sdk.agent.AgentToolResult.Success -> ToolResult(true, result.outputSummary)
            is com.llamatik.sdk.agent.AgentToolResult.Failure -> ToolResult(false, result.errorMessage)
            com.llamatik.sdk.agent.AgentToolResult.PermissionDenied -> ToolResult(false, "permissão negada pelo Llamatik")
            com.llamatik.sdk.agent.AgentToolResult.Unsupported -> ToolResult(false, "ferramenta não suportada")
        }
    }
}

fun defaultTools(
    workspace: File,
    lessons: LessonStore,
    settings: dev.labforge.app.core.settings.AppSettings = dev.labforge.app.core.settings.AppSettings(),
): List<AgentTool> = listOf(
    ListDirTool(workspace),
    ReadFileTool(workspace),
    WriteFileTool(workspace),
    ApplyPatchTool(workspace),
    SearchCodeTool(workspace),
    TerminalTool(workspace),
    GitTool(workspace, "status"),
    GitTool(workspace, "commit"),
    GitTool(workspace, "log"),
    GitTool(workspace, "reset"),
    MemorySaveTool(workspace, lessons),
    MemorySearchTool(workspace, lessons),
    ResearchWebTool(workspace, dev.labforge.app.core.research.ResearchKeys(settings.braveApiKey, settings.githubToken)),
    ResearchSearchTool(workspace),
    LlamatikToolBridge("public_api_posts", workspace),
) + gitHubTools(workspace, settings.githubToken)
