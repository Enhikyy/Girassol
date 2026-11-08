package dev.labforge.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.llamatik.sdk.agent.AgentToolResult
import dev.labforge.app.integration.llamatik.LlamatikRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LlamatikRuntimeTest {

    @Test
    fun runtimeInitializesWithBuiltInsAndCustomTools() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        LlamatikRuntime.init(app)
        assertTrue(LlamatikRuntime.isInitialized)
        val ids = LlamatikRuntime.toolIds()
        assertTrue(ids.contains("calculator"))
        assertTrue(ids.contains("datetime"))
        assertTrue(ids.contains("public_api_posts"))
        assertTrue(ids.contains("research_web"))
        assertTrue(ids.contains("github_search_repos"))
        assertTrue(ids.contains("github_create_issue"))
        assertTrue(ids.contains("git_push"))
    }

    @Test
    fun executorRunsBuiltInTool() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        LlamatikRuntime.init(app)
        val result = LlamatikRuntime.runTool(
            "calculator",
            buildJsonObject { put("expression", "2+2") },
        )
        assertTrue("resultado: $result", result is AgentToolResult.Success)
        assertTrue((result as AgentToolResult.Success).outputSummary.contains("4"))
    }

    @Test
    fun coreNativeIsExcludedFromTheApk() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.llamatik.core.platform.LlamaBridge")
        }
    }
}
