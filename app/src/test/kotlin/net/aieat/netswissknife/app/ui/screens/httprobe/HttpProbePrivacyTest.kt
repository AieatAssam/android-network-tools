package net.aieat.netswissknife.app.ui.screens.httprobe

import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpProbePrivacyTest {
    @Test
    fun `share formatter redacts URL credentials paths queries and sensitive headers`() {
        val sourceUrl = "https://alice:source-secret@source.example/start?source-token=private#source-fragment"
        val finalUrl = "https://bob:destination-secret@target.example/final?final-token=private#final-fragment"
        val result = HttpProbeResult(
            request = HttpProbeRequest(url = sourceUrl),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 42,
            responseHeaders = mapOf(
                "Content-Type" to listOf("application/json"),
                "Location" to listOf("https://next.example/path?location-token=private"),
                "Set-Cookie" to listOf("sid=cookie-secret; Secure"),
                "Authorization" to listOf("Bearer auth-secret"),
                "Proxy-Authorization" to listOf("Basic proxy-secret"),
            ),
            responseBody = "{}",
            responseBodyBytes = 2,
            finalUrl = finalUrl,
            redirectChain = listOf(sourceUrl),
            securityChecks = emptyList(),
        )

        val share = buildHttpShareText(result, "2 B")

        assertTrue(share.contains("https://source.example/[path omitted]"))
        assertTrue(share.contains("Final URL: https://target.example/[path omitted]"))
        assertTrue(share.contains("https://next.example/[path omitted]"))
        assertTrue(share.contains("Content-Type: application/json"))
        listOf(
            "source-secret", "destination-secret", "source-token", "final-token", "location-token",
            "cookie-secret", "auth-secret", "proxy-secret", "source-fragment", "final-fragment",
        ).forEach { assertFalse(share.contains(it), "Share output leaked $it") }
    }

    @Test
    fun `safe response headers omit credentials and redact Location`() {
        val visible = visibleHttpResponseHeaders(
            mapOf(
                "Set-Cookie" to listOf("session=secret"),
                "Authorization" to listOf("Bearer secret"),
                "Location" to listOf("https://next.example/path?token=secret"),
                "Content-Type" to listOf("text/plain"),
            )
        )
        assertFalse(visible.keys.any { it.equals("Set-Cookie", true) || it.equals("Authorization", true) })
        assertTrue(visible["Location"]!!.single().contains("https://next.example/[path omitted]"))
        assertTrue(visible["Content-Type"]!!.single() == "text/plain")
    }
}
