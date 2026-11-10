package dev.labforge.app.core.term

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

/**
 * Git as the agent's safety net, implemented with JGit so it works on stock
 * Android (no git binary required).
 */
class GitRepo(private val dir: File) {

    private fun open(): Git = Git.open(dir)

    private fun ensureInit(): Git {
        dir.mkdirs()
        val gitDir = File(dir, ".git")
        val git = if (gitDir.exists()) Git.open(dir) else Git.init().setDirectory(dir).call()
        val ignore = File(dir, ".gitignore")
        if (!ignore.exists()) {
            ignore.writeText(
                "models/\noutput/\n*.gguf\n*.dat\n*.imatrix\n*.bin\nruns/\n.cache/\n",
            )
        }
        return git
    }

    suspend fun init(): String = withContext(Dispatchers.IO) {
        runCatching { ensureInit().use { "repositório git pronto em ${dir.name}" } }
            .getOrElse { "erro ao iniciar git: ${it.message}" }
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit().use { git ->
                val st = git.status().call()
                buildString {
                    append("branch: ").append(git.repository.branch).append('\n')
                    if (st.isClean) append("working tree limpo")
                    if (st.added.isNotEmpty()) append("added: ${st.added.joinToString(", ")}\n")
                    if (st.changed.isNotEmpty()) append("modified: ${st.changed.joinToString(", ")}\n")
                    if (st.modified.isNotEmpty()) append("modified: ${st.modified.joinToString(", ")}\n")
                    if (st.untracked.isNotEmpty()) append("untracked: ${st.untracked.joinToString(", ")}\n")
                    if (st.missing.isNotEmpty()) append("deleted: ${st.missing.joinToString(", ")}\n")
                }
            }
        }.getOrElse { "erro no git status: ${it.message}" }
    }

    suspend fun commit(message: String): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit().use { git ->
                git.add().addFilepattern(".").call()
                git.add().addFilepattern(".").setUpdate(true).call()
                val ident = PersonIdent("LabForge Agent", "agent@labforge.local")
                val commit = git.commit()
                    .setMessage(message.ifBlank { "checkpoint do agente" })
                    .setAuthor(ident)
                    .setCommitter(ident)
                    .setAllowEmpty(true)
                    .call()
                "commit ${commit.name.take(8)} · ${commit.shortMessage}"
            }
        }.getOrElse { "erro ao commitar: ${it.message}" }
    }

    suspend fun log(limit: Int = 12): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit().use { git ->
                git.log().setMaxCount(limit).call().joinToString("\n") { c ->
                    "${c.name.take(8)} ${c.shortMessage} (${c.authorIdent.name})"
                }.ifBlank { "(sem commits)" }
            }
        }.getOrElse { "erro no git log: ${it.message}" }
    }

    suspend fun resetHard(rev: String): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit().use { git ->
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(rev.ifBlank { "HEAD~1" }).call()
                "reset --hard ${rev.ifBlank { "HEAD~1" }} concluído"
            }
        }.getOrElse { "erro no reset: ${it.message}" }
    }

    suspend fun diff(): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit().use { git ->
                val output = ByteArrayOutputStream()
                val changes = git.diff().setOutputStream(output).call()
                if (changes.isEmpty()) return@use "(sem alterações)"
                output.toString(Charsets.UTF_8.name())
            }
        }.getOrElse { "erro no diff: ${it.message}" }
    }

    /** Clones [url] into this directory. The token is only ever sent to https://github.com. */
    suspend fun cloneFrom(url: String, token: String = ""): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "use uma URL https://" }
        require(!dir.exists() || dir.listFiles().isNullOrEmpty()) { "destino já existe e não está vazio" }
        val cmd = Git.cloneRepository().setURI(url).setDirectory(dir).setCloneSubmodules(false)
        githubCredentials(url, token)?.let { cmd.setCredentialsProvider(it) }
        cmd.call().use { g -> "clonado em ${dir.name} (branch ${g.repository.branch})" }
    }

    suspend fun checkoutBranch(name: String): String = withContext(Dispatchers.IO) {
        require(Regex("^[A-Za-z0-9._/-]+$").matches(name) && !name.contains("..")) { "nome de branch inválido" }
        open().use { g ->
            val exists = g.repository.findRef("refs/heads/$name") != null
            g.checkout().setName(name).setCreateBranch(!exists).call()
            "branch atual: $name" + if (exists) "" else " (criada)"
        }
    }

    suspend fun push(token: String, remote: String = "origin"): String = withContext(Dispatchers.IO) {
        open().use { g ->
            val config = g.repository.config
            val url = config.getString("remote", remote, "url") ?: error("remote '$remote' não configurado")
            val pushUrls = config.getStringList("remote", remote, "pushurl").filter { it.isNotBlank() }
            val destinations = pushUrls.ifEmpty { listOf(url) }
            require(destinations.all { isGitHubHttpsUrl(it) }) {
                "push recusado: todas as URLs de envio precisam apontar para https://github.com"
            }
            val creds = githubCredentials(url, token) ?: error("push só é permitido para https://github.com com token configurado")
            val branch = g.repository.branch
            require(branch !in setOf("main", "master")) { "crie uma branch (git_branch) antes do push; push direto em $branch é bloqueado" }
            val results = g.push().setRemote(remote).add(branch).setCredentialsProvider(creds).call()
            val updates = results.flatMap { it.remoteUpdates }
            require(updates.isNotEmpty()) { "o servidor não confirmou nenhuma referência enviada" }
            val rejected = updates.filter {
                it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                    it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE
            }
            check(rejected.isEmpty()) {
                "push rejeitado: " + rejected.joinToString("; ") {
                    it.remoteName + ": " + it.status + " " + it.message.orEmpty()
                }
            }
            updates.joinToString("\n") { it.remoteName + ": " + it.status }
        }
    }

    private fun isGitHubHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo.isNullOrBlank()
    }

    private fun githubCredentials(url: String, token: String): UsernamePasswordCredentialsProvider? {
        if (token.isBlank() || !url.startsWith("https://")) return null
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        return if (host == "github.com") UsernamePasswordCredentialsProvider("x-access-token", token.trim()) else null
    }
}
