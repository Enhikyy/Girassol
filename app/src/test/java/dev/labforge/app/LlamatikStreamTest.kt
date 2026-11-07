package dev.labforge.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.llamatik.sdk.framework.AgentStreamChunk
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.integration.llamatik.LlamatikRuntime
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LlamatikStreamTest {

    @Test
    fun streamsDeltasAndFinishes() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Olá\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\" mundo\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )
        server.start()
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        LlamatikRuntime.init(app)
        val client = LlmClient(server.url("/v1").toString(), "", "test-model")
        val chunks = Collections.synchronizedList(mutableListOf<AgentStreamChunk>())
        val final = LlamatikRuntime.streamWith(client, "diga olá") { chunks.add(it) }
        assertTrue("final=$final", final.contains("Olá"))
        assertTrue(chunks.any { it is AgentStreamChunk.Delta })
        assertTrue(chunks.any { it is AgentStreamChunk.Done })
        server.shutdown()
    }
}
