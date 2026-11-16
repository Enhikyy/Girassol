package dev.labforge.app

import dev.labforge.app.core.research.AgentReachNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentReachNativeTest {
    @Test fun acceptsPublicHttpsUrls() {
        assertEquals("https://example.com/path", AgentReachNative.validatePublicUrl("https://example.com/path#section"))
    }

    @Test fun rejectsLocalAndPrivateTargets() {
        listOf(
            "http://localhost/admin",
            "http://127.0.0.1/",
            "http://192.168.1.10/",
            "http://10.0.0.5/",
            "http://service.internal/",
            "http://user:password@example.com/",
            "file:///etc/passwd",
            "https://example.com:8443/",
        ).forEach { url ->
            val rejected = runCatching { AgentReachNative.validatePublicUrl(url) }.isFailure
            assertTrue("URL deveria ser bloqueada: $url", rejected)
        }
    }
}
