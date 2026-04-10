package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HttpProbeRepositoryImpl – input validation")
class HttpProbeRepositoryValidationTest {

    private val repo = HttpProbeRepositoryImpl()

    @Test
    @DisplayName("probe returns Error for blank URL")
    fun `probe returns Error for blank URL`() = runTest {
        val result = repo.probe(HttpProbeRequest(url = "   "))
        assertTrue(result is NetworkResult.Error)
        assertTrue((result as NetworkResult.Error).message.contains("blank", ignoreCase = true))
    }

    @Test
    @DisplayName("probe returns Error for non-HTTP URL")
    fun `probe returns Error for non-HTTP URL`() = runTest {
        val result = repo.probe(HttpProbeRequest(url = "ftp://example.com"))
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("probe returns Error for malformed URL")
    fun `probe returns Error for malformed URL`() = runTest {
        val result = repo.probe(HttpProbeRequest(url = "not a url at all"))
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("probe returns Error for timeout below 500ms")
    fun `probe returns Error for timeout below 500ms`() = runTest {
        val result = repo.probe(HttpProbeRequest(url = "https://example.com", timeoutMs = 499))
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("probe returns Error for timeout above 60000ms")
    fun `probe returns Error for timeout above 60000ms`() = runTest {
        val result = repo.probe(HttpProbeRequest(url = "https://example.com", timeoutMs = 60_001))
        assertTrue(result is NetworkResult.Error)
    }
}

@DisplayName("HttpSecurityAnalyzer – header ratings")
class HttpSecurityAnalyzerTest {

    @Test
    @DisplayName("HSTS present on HTTPS gets PASS")
    fun `HSTS present on HTTPS gets PASS`() {
        val headers = mapOf("Strict-Transport-Security" to listOf("max-age=31536000; includeSubDomains"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val hsts = checks.first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.PASS, hsts.rating)
    }

    @Test
    @DisplayName("HSTS absent on HTTPS gets FAIL")
    fun `HSTS absent on HTTPS gets FAIL`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        val hsts = checks.first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.FAIL, hsts.rating)
    }

    @Test
    @DisplayName("HSTS absent on HTTP gets INFO")
    fun `HSTS absent on HTTP gets INFO`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = false)
        val hsts = checks.first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.INFO, hsts.rating)
    }

    @Test
    @DisplayName("X-Frame-Options DENY gets PASS")
    fun `X-Frame-Options DENY gets PASS`() {
        val headers = mapOf("X-Frame-Options" to listOf("DENY"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val check = checks.first { it.headerName == "X-Frame-Options" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("X-Frame-Options SAMEORIGIN gets PASS")
    fun `X-Frame-Options SAMEORIGIN gets PASS`() {
        val headers = mapOf("X-Frame-Options" to listOf("SAMEORIGIN"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val check = checks.first { it.headerName == "X-Frame-Options" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("X-Frame-Options absent gets FAIL")
    fun `X-Frame-Options absent gets FAIL`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        val check = checks.first { it.headerName == "X-Frame-Options" }
        assertEquals(SecurityRating.FAIL, check.rating)
    }

    @Test
    @DisplayName("X-Content-Type-Options nosniff gets PASS")
    fun `X-Content-Type-Options nosniff gets PASS`() {
        val headers = mapOf("X-Content-Type-Options" to listOf("nosniff"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val check = checks.first { it.headerName == "X-Content-Type-Options" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("X-Content-Type-Options absent gets FAIL")
    fun `X-Content-Type-Options absent gets FAIL`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        val check = checks.first { it.headerName == "X-Content-Type-Options" }
        assertEquals(SecurityRating.FAIL, check.rating)
    }

    @Test
    @DisplayName("CSP present gets PASS")
    fun `CSP present gets PASS`() {
        val headers = mapOf("Content-Security-Policy" to listOf("default-src 'self'"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val check = checks.first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("CSP absent gets WARN")
    fun `CSP absent gets WARN`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        val check = checks.first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.WARN, check.rating)
    }

    @Test
    @DisplayName("Server header with version info gets WARN")
    fun `Server header with version info gets WARN`() {
        val headers = mapOf("Server" to listOf("Apache/2.4.51 (Ubuntu)"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val check = checks.first { it.headerName == "Server" }
        assertEquals(SecurityRating.WARN, check.rating)
    }

    @Test
    @DisplayName("Server header absent or generic gets INFO")
    fun `Server header absent or generic gets INFO`() {
        val headers = mapOf("Server" to listOf("cloudflare"))
        val checks = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
        val check = checks.first { it.headerName == "Server" }
        assertEquals(SecurityRating.INFO, check.rating)
    }

    @Test
    @DisplayName("analyze returns at least 5 security checks")
    fun `analyze returns at least 5 security checks`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        assertTrue(checks.size >= 5)
    }
}

@DisplayName("HttpMethod – body support")
class HttpMethodBodySupportTest {

    @Test
    @DisplayName("POST supports body")
    fun `POST supports body`() {
        assertTrue(HttpMethod.POST.supportsBody)
    }

    @Test
    @DisplayName("PUT supports body")
    fun `PUT supports body`() {
        assertTrue(HttpMethod.PUT.supportsBody)
    }

    @Test
    @DisplayName("PATCH supports body")
    fun `PATCH supports body`() {
        assertTrue(HttpMethod.PATCH.supportsBody)
    }

    @Test
    @DisplayName("GET does not support body")
    fun `GET does not support body`() {
        assertTrue(!HttpMethod.GET.supportsBody)
    }

    @Test
    @DisplayName("HEAD does not support body")
    fun `HEAD does not support body`() {
        assertTrue(!HttpMethod.HEAD.supportsBody)
    }

    @Test
    @DisplayName("DELETE does not support body")
    fun `DELETE does not support body`() {
        assertTrue(!HttpMethod.DELETE.supportsBody)
    }
}
