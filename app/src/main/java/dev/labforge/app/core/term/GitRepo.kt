package dev.labforge.app.core.term

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.PersonIdent
import java.io.File

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
                git.diff().call().joinToString("\n") { d -> d.toString() }.ifBlank { "(sem alterações)" }
            }
        }.getOrElse { "erro no diff: ${it.message}" }
    }
}
