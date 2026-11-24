package net.aieat.netswissknife.core.network.httprobe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HttpSecurityAnalyzer treats document-only headers as not applicable to non-HTML responses")
class HttpSecurityAnalyzerNonHtmlTest {
    private val documentOnly = listOf(
        "Content-Security-Policy",
        "X-Frame-Options",
        "Cross-Origin-Opener-Policy",
        "Cross-Origin-Embedder-Policy",
    )

    private fun ratingOf(checks: List<SecurityHeaderCheck>, name: String) =
        checks.first { it.headerName.equals(name, ignoreCase = true) }.rating

    @Test
    fun `missing document headers on a JSON response are INFO, not FAIL or WARN`() {
        val checks = HttpSecurityAnalyzer.analyze(
            mapOf("Content-Type" to listOf("application/json")),
            isHttps = true,
        )

        documentOnly.forEach { name -> assertEquals(SecurityRating.INFO, ratingOf(checks, name), name) }
        val frameOptions = checks.first { it.headerName == "X-Frame-Options" }
        assertEquals("httprobe_sec_document_header_not_applicable", frameOptions.descriptionKey)
    }

    @Test
    fun `missing document headers on an HTML response keep their FAIL or WARN rating`() {
        val checks = HttpSecurityAnalyzer.analyze(
            mapOf("Content-Type" to listOf("text/html; charset=utf-8")),
            isHttps = true,
        )

        assertEquals(SecurityRating.WARN, ratingOf(checks, "Content-Security-Policy"))
        assertEquals(SecurityRating.FAIL, ratingOf(checks, "X-Frame-Options"))
    }

    @Test
    fun `a response without a Content-Type is still judged as a document`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)

        assertEquals(SecurityRating.WARN, ratingOf(checks, "Content-Security-Policy"))
    }

    @Test
    fun `a document header that is present still passes on a non-HTML response`() {
        val checks = HttpSecurityAnalyzer.analyze(
            mapOf(
                "Content-Type" to listOf("application/json"),
                "Content-Security-Policy" to listOf("default-src 'none'"),
            ),
            isHttps = true,
        )

        assertEquals(SecurityRating.PASS, ratingOf(checks, "Content-Security-Policy"))
    }
}
