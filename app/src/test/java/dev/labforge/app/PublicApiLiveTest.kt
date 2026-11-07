package dev.labforge.app

import com.llamatik.sdk.agent.AgentToolResult
import dev.labforge.app.integration.api.PublicApiTool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PublicApiLiveTest {
    @Test
    fun retrofitToolCallsRealApi() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val result = PublicApiTool().execute(JsonObject(emptyMap()))
        assertTrue("resultado: $result", result is AgentToolResult.Success)
        assertTrue((result as AgentToolResult.Success).outputSummary.contains("#"))
    }
}
