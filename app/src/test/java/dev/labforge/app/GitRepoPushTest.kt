package dev.labforge.app

import dev.labforge.app.core.term.GitRepo
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
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
}
