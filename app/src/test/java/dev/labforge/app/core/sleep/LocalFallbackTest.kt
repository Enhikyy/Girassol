package dev.labforge.app.core.sleep

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.settings.AppSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LocalFallbackTest {
    private fun messages() = JSONArray().put(JSONObject().put("role", "user").put("content", "Olá"))

    @Test fun missingLocalModelUsesExplicitlyEnabledFallback() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"fallback OK"}}]}"""))
            val cfg = AppSettings(localInference = true, localModelPath = "/missing.gguf",
                allowCloudFallback = true, endpoint = server.url("/v1").toString())
            assertEquals("fallback OK", LlmClient.fromSettings(cfg).chat(messages(), null).content)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun disabledFallbackDoesNotSendConversation() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val cfg = AppSettings(localInference = true, localModelPath = "/missing.gguf",
                allowCloudFallback = false, endpoint = server.url("/v1").toString())
            var failed = false
            try { LlmClient.fromSettings(cfg).chat(messages(), null) }
            catch (_: IllegalStateException) { failed = true }
            assertTrue(failed)
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }
}
