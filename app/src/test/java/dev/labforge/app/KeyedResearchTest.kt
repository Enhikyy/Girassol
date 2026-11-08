package dev.labforge.app

import dev.labforge.app.core.agent.GitHubReadFileTool
import dev.labforge.app.core.agent.GitPushTool
import dev.labforge.app.core.agent.GitHubCreateIssueTool
import dev.labforge.app.core.github.GitHubApi
import dev.labforge.app.core.research.KeyedResearchApis
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KeyedResearchTest {

    @Test
    fun parsesBraveWebResults() {
        val json = """
            {"web":{"results":[
              {"title":"Kotlin <strong>coroutines</strong>","url":"https://kotlinlang.org/docs/coroutines.html",
               "description":"Guide &amp; reference","extra_snippets":["structured concurrency"]},
              {"title":"bad","url":"javascript:alert(1)","description":"x"}
            ]}}
        """.trimIndent()
        val docs = KeyedResearchApis.parseBrave(json)
        assertEquals(1, docs.size)
        assertEquals("Kotlin coroutines", docs[0].title)
        assertEquals("Brave Search", docs[0].source)
        assertTrue(docs[0].snippet.contains("Guide & reference"))
        assertTrue(docs[0].snippet.contains("structured concurrency"))
    }

    @Test
    fun braveWithoutKeyIsSkipped() = runBlocking {
        assertTrue(KeyedResearchApis.brave("kotlin", "").isEmpty())
    }

    @Test
    fun parsesGithubRepoSearch() {
        val json = """{"items":[{"full_name":"ggml-org/llama.cpp","html_url":"https://github.com/ggml-org/llama.cpp",
            "description":"LLM inference in C/C++","stargazers_count":90000,"language":"C++",
            "topics":["llm","gguf"],"pushed_at":"2026-10-01T00:00:00Z"}]}"""
        val docs = KeyedResearchApis.parseGithubRepos(json)
        assertEquals("ggml-org/llama.cpp", docs.single().title)
        assertEquals(2026, docs.single().year)
        assertTrue(docs.single().snippet.contains("gguf"))
        assertTrue(docs.single().score > 2.0)
    }

    @Test
    fun githubHeadersOnlyCarryTokenWhenSet() {
        assertFalse(GitHubApi.headers("").containsKey("Authorization"))
        assertEquals("Bearer abc", GitHubApi.headers(" abc ")["Authorization"])
        assertEquals("src/a%20b/c.kt", GitHubApi.encPath("/src/a b/c.kt"))
        assertTrue(GitHubApi.isRepo("Enhikyy/Girassol"))
        assertFalse(GitHubApi.isRepo("../etc/passwd"))
    }

    private fun ws(): File = Files.createTempDirectory("gh").toFile()

    @Test
    fun githubToolsRejectBadRepoAndMissingToken() = runBlocking {
        val bad = GitHubReadFileTool(ws(), "").execute(JSONObject().put("repo", "not a repo"))
        assertFalse(bad.ok)
        val noToken = GitHubCreateIssueTool(ws(), "").execute(JSONObject().put("repo", "a/b").put("title", "t"))
        assertFalse(noToken.ok)
        assertTrue(noToken.output.contains("token"))
    }

    @Test
    fun pushIsRefusedWithoutGithubRemote() = runBlocking {
        val dir = ws()
        org.eclipse.jgit.api.Git.init().setDirectory(dir).call().close()
        val res = GitPushTool(dir, "secret-token").execute(JSONObject())
        assertFalse(res.ok)
        assertFalse(res.output.contains("secret-token"))
    }
}
