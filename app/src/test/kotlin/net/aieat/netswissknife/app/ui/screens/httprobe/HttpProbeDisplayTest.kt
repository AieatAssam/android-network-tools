package net.aieat.netswissknife.app.ui.screens.httprobe

import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpProbeDisplayTest {
    private val localized = RedirectPlaceholders(
        address = "[adresse masquée]",
        path = "[chemin masqué]",
        query = "[requête masquée]",
    )

    @Test
    fun `redirect placeholders come from the caller, not from English defaults`() {
        assertEquals(
            "https://example.com/[chemin masqué]",
            safeRedirectDisplayValue("https://example.com/private/path", localized),
        )
        assertEquals("[adresse masquée]", safeRedirectDisplayValue("/relative", localized))
        assertEquals("[adresse masquée]", safeRedirectDisplayValue("not a uri %%", localized))
    }

    @Test
    fun `on-screen headers keep redirect addresses whole but still drop credentials`() {
        val headers = mapOf(
            "Location" to listOf("https://example.com/callback?token=abc"),
            "Set-Cookie" to listOf("session=secret"),
            "Content-Type" to listOf("text/html"),
        )

        val shown = httpResponseHeadersForDisplay(headers)

        assertEquals(listOf("https://example.com/callback?token=abc"), shown["Location"])
        assertFalse(shown.containsKey("Set-Cookie"))
        assertTrue(shown.containsKey("Content-Type"))
    }

    @Test
    fun `on-screen URLs drop credentials and fragments but keep the path and query`() {
        assertEquals(
            "https://target.example/final?final-token=private",
            displayUrl("https://bob:destination-secret@target.example/final?final-token=private#final-fragment"),
        )
        assertEquals("//target.example/next", displayUrl("//bob:secret@target.example/next"))
        assertEquals("/relative?x=1", displayUrl("/relative?x=1"))
        assertEquals("[redirect address omitted]", displayUrl("//bob:secret@[broken/path"))
    }

    @Test
    fun `shared headers reduce redirect addresses with the caller's placeholders`() {
        val headers = mapOf("location" to listOf("https://example.com/callback?token=abc"))

        val shared = visibleHttpResponseHeaders(headers, localized)

        // An existing path is hidden with the path placeholder; the query is not shown separately.
        assertEquals(listOf("https://example.com/[chemin masqué]"), shared["location"])
    }

    @Test
    fun `share text uses a colon rather than a dash after the tool name`() {
        val result = HttpProbeResult(
            request = HttpProbeRequest(url = "https://example.com/", method = HttpMethod.GET),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 12L,
            responseHeaders = emptyMap(),
            responseBody = null,
            responseBodyBytes = 0L,
            finalUrl = "https://example.com/",
            redirectChain = emptyList(),
            securityChecks = emptyList(),
        )

        val text = buildHttpShareText(result, sizeText = "0 B")

        assertTrue(text.startsWith("HTTP: https://example.com/\n"))
        assertFalse(text.contains('–'))
    }
}
