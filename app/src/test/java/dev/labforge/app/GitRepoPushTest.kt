package dev.labforge.app

import dev.labforge.app.core.term.GitRepo
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GitRepoPushTest {
    @Test
    fun refusesAlternatePushUrlOutsideGithubBeforeSendingCredentials() = runBlocking {
        val directory = Files.createTempDirectory("girassol-git-push-test").toFile()
        try {
            Git.init().setDirectory(directory).call().use { git ->
                git.repository.config.setString("remote", "origin", "url", "https://github.com/example/repo.git")
                git.repository.config.setString("remote", "origin", "pushurl", "https://example.invalid/collect.git")
                git.repository.config.save()
            }
            val error = runCatching { GitRepo(directory).push("test-token") }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error?.message.orEmpty().contains("github.com"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun diffReturnsApplicablePatchInsteadOfObjectDescriptions() = runBlocking {
        val directory = Files.createTempDirectory("girassol-git-diff-test").toFile()
        try {
            Git.init().setDirectory(directory).call().use { git ->
                val source = java.io.File(directory, "message.txt")
                source.writeText("original\n")
                git.add().addFilepattern("message.txt").call()
                val ident = PersonIdent("Test", "test@example.com")
                git.commit().setMessage("initial").setAuthor(ident).setCommitter(ident).call()
                source.writeText("revised\n")
            }
            val patch = GitRepo(directory).diff()
            assertTrue(patch, patch.contains("diff --git a/message.txt b/message.txt"))
            assertTrue(patch.contains("-original"))
            assertTrue(patch.contains("+revised"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
