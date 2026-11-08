package dev.labforge.app.core.agent

import dev.labforge.app.core.github.GitHubApi
import dev.labforge.app.core.term.GitRepo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

private fun schema(required: List<String>, vararg props: Pair<String, String>) = JSONObject().apply {
    put("type", "object")
    put("properties", JSONObject().apply {
        props.forEach { (k, d) -> put(k, JSONObject().put("type", "string").put("description", d)) }
    })
    put("required", JSONArray(required))
}

abstract class GitHubTool(ws: File, protected val token: String) : AgentTool(ws) {
    protected fun repoArg(args: JSONObject): String {
        val repo = args.optString("repo").trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        require(GitHubApi.isRepo(repo)) { "repo deve estar no formato dono/nome" }
        return repo
    }

    protected fun requireToken() = require(token.isNotBlank()) { "configure um token do GitHub em Ajustes → Pesquisa e GitHub" }

    protected suspend fun guarded(block: suspend () -> String): ToolResult =
        runCatching { ToolResult(true, block()) }
            .getOrElse { ToolResult(false, (it.message ?: "erro no GitHub").replace(token.ifBlank { "\u0000" }, "***")) }
}

class GitHubSearchReposTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "github_search_repos"
    override val description = "Busca repositórios públicos no GitHub (ordenados por estrelas). Use para achar referências e bibliotecas."
    override fun parameters() = schema(listOf("query"), "query" to "termos de busca, aceita qualificadores como language:kotlin")
    override suspend fun execute(args: JSONObject) = guarded {
        val q = args.getString("query").trim()
        require(q.isNotEmpty()) { "query vazia" }
        val items = JSONObject(GitHubApi.request("GET", "/search/repositories?q=${GitHubApi.enc(q)}&sort=stars&per_page=10", token))
            .optJSONArray("items") ?: JSONArray()
        (0 until items.length()).joinToString("\n") { i ->
            val o = items.getJSONObject(i)
            "${o.optString("full_name")} ★${o.optInt("stargazers_count")} ${o.optString("language").takeIf { it != "null" }.orEmpty()} — " +
                o.optString("description").takeIf { it != "null" }.orEmpty().take(160)
        }.ifBlank { "nenhum repositório encontrado" }
    }
}

class GitHubSearchCodeTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "github_search_code"
    override val description = "Busca trechos de código no GitHub (requer token). Opcionalmente restrito a um repo dono/nome."
    override fun parameters() = schema(listOf("query"), "query" to "o que procurar", "repo" to "opcional: dono/nome")
    override suspend fun execute(args: JSONObject) = guarded {
        requireToken()
        var q = args.getString("query").trim()
        if (args.optString("repo").isNotBlank()) q += " repo:${repoArg(args)}"
        val items = JSONObject(GitHubApi.request("GET", "/search/code?q=${GitHubApi.enc(q)}&per_page=15", token))
            .optJSONArray("items") ?: JSONArray()
        (0 until items.length()).joinToString("\n") { i ->
            val o = items.getJSONObject(i)
            "${o.getJSONObject("repository").optString("full_name")}:${o.optString("path")}"
        }.ifBlank { "nenhum resultado" }
    }
}

class GitHubReadFileTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "github_read_file"
    override val description = "Lê um arquivo (ou lista uma pasta) de um repositório GitHub sem precisar clonar."
    override fun parameters() = schema(listOf("repo"), "repo" to "dono/nome", "path" to "caminho do arquivo ou pasta (vazio = raiz)", "ref" to "opcional: branch, tag ou commit")
    override suspend fun execute(args: JSONObject) = guarded {
        val repo = repoArg(args)
        val ref = args.optString("ref").trim()
        val path = "/repos/$repo/contents/${GitHubApi.encPath(args.optString("path"))}" +
            if (ref.isNotEmpty()) "?ref=${GitHubApi.enc(ref)}" else ""
        val body = GitHubApi.request("GET", path, token).trim()
        if (body.startsWith("[")) {
            val arr = JSONArray(body)
            (0 until arr.length()).joinToString("\n") { i ->
                val o = arr.getJSONObject(i)
                (if (o.optString("type") == "dir") "📁 " else "   ") + o.optString("path")
            }
        } else {
            val o = JSONObject(body)
            if (o.optString("encoding") != "base64") error("arquivo grande demais ou binário: ${o.optString("html_url")}")
            val text = String(Base64.getMimeDecoder().decode(o.optString("content")), Charsets.UTF_8)
            if (text.length > 20_000) text.take(20_000) + "\n… (truncado, ${text.length} caracteres)" else text
        }
    }
}

class GitHubListIssuesTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "github_list_issues"
    override val description = "Lista issues e pull requests de um repositório GitHub."
    override fun parameters() = schema(listOf("repo"), "repo" to "dono/nome", "state" to "open | closed | all (padrão open)")
    override suspend fun execute(args: JSONObject) = guarded {
        val state = args.optString("state").ifBlank { "open" }.takeIf { it in setOf("open", "closed", "all") } ?: "open"
        val arr = JSONArray(GitHubApi.request("GET", "/repos/${repoArg(args)}/issues?state=$state&per_page=20", token))
        (0 until arr.length()).joinToString("\n") { i ->
            val o = arr.getJSONObject(i)
            val kind = if (o.has("pull_request")) "PR" else "issue"
            "#${o.optInt("number")} [$kind] ${o.optString("title")} (${o.optString("state")})"
        }.ifBlank { "nenhuma issue" }
    }
}

class GitHubCreateIssueTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "github_create_issue"
    override val description = "Abre uma issue num repositório GitHub (requer token com permissão de issues)."
    override fun parameters() = schema(listOf("repo", "title"), "repo" to "dono/nome", "title" to "título", "body" to "descrição em markdown")
    override suspend fun execute(args: JSONObject) = guarded {
        requireToken()
        val payload = JSONObject().put("title", args.getString("title")).put("body", args.optString("body"))
        val o = JSONObject(GitHubApi.request("POST", "/repos/${repoArg(args)}/issues", token, payload))
        "issue #${o.optInt("number")} criada: ${o.optString("html_url")}"
    }
}

class GitHubCreatePrTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "github_create_pr"
    override val description = "Abre um pull request (requer token). Faça git_branch, git_commit e git_push antes."
    override fun parameters() = schema(
        listOf("repo", "head", "title"),
        "repo" to "dono/nome", "head" to "branch com as mudanças", "base" to "branch de destino (padrão main)",
        "title" to "título", "body" to "descrição",
    )
    override suspend fun execute(args: JSONObject) = guarded {
        requireToken()
        val payload = JSONObject()
            .put("title", args.getString("title"))
            .put("head", args.getString("head"))
            .put("base", args.optString("base").ifBlank { "main" })
            .put("body", args.optString("body"))
        val o = JSONObject(GitHubApi.request("POST", "/repos/${repoArg(args)}/pulls", token, payload))
        "PR #${o.optInt("number")} aberto: ${o.optString("html_url")}"
    }
}

class GitCloneTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "git_clone"
    override val description = "Clona um repositório (https) para uma pasta dentro do workspace."
    override fun parameters() = schema(listOf("url"), "url" to "https://github.com/dono/nome(.git) ou dono/nome", "dir" to "pasta de destino (padrão: nome do repo)")
    override suspend fun execute(args: JSONObject) = guarded {
        var url = args.getString("url").trim()
        if (GitHubApi.isRepo(url)) url = "https://github.com/$url.git"
        val defaultDir = url.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
        val target = resolve(args.optString("dir").ifBlank { defaultDir })
        require(target.canonicalPath != workspace.canonicalPath) { "escolha uma subpasta do workspace" }
        require(!target.path.split(File.separator).contains(".git")) { "destino inválido" }
        GitRepo(target).cloneFrom(url, token)
    }
}

class GitBranchTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "git_branch"
    override val description = "Cria (ou troca para) uma branch num repositório do workspace."
    override fun parameters() = schema(listOf("name"), "name" to "nome da branch", "dir" to "pasta do repositório (padrão: raiz do workspace)")
    override suspend fun execute(args: JSONObject) = guarded {
        GitRepo(resolve(args.optString("dir").ifBlank { "." })).checkoutBranch(args.getString("name").trim())
    }
}

class GitPushTool(ws: File, token: String) : GitHubTool(ws, token) {
    override val name = "git_push"
    override val description = "Envia a branch atual para o GitHub (requer token; push em main/master é bloqueado)."
    override fun parameters() = schema(emptyList(), "dir" to "pasta do repositório (padrão: raiz do workspace)", "remote" to "remote (padrão origin)")
    override suspend fun execute(args: JSONObject) = guarded {
        requireToken()
        GitRepo(resolve(args.optString("dir").ifBlank { "." })).push(token, args.optString("remote").ifBlank { "origin" })
    }
}

fun gitHubTools(workspace: File, token: String): List<AgentTool> = listOf(
    GitHubSearchReposTool(workspace, token),
    GitHubSearchCodeTool(workspace, token),
    GitHubReadFileTool(workspace, token),
    GitHubListIssuesTool(workspace, token),
    GitHubCreateIssueTool(workspace, token),
    GitHubCreatePrTool(workspace, token),
    GitCloneTool(workspace, token),
    GitBranchTool(workspace, token),
    GitPushTool(workspace, token),
)
