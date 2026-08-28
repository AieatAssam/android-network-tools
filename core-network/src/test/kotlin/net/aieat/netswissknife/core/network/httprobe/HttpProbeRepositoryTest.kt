package net.aieat.netswissknife.core.network.httprobe

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetSocketAddress

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

    // ── Previously uncovered branches ────────────────────────────────────────

    @Test
    @DisplayName("analyze reports the same seven checks in a stable order")
    fun `analyze reports the seven checks in order`() {
        val checks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        assertEquals(
            listOf(
                "Strict-Transport-Security",
                "Content-Security-Policy",
                "X-Frame-Options",
                "X-Content-Type-Options",
                "Referrer-Policy",
                "Permissions-Policy",
                "Server"
            ),
            checks.map { it.headerName }
        )
    }

    @Test
    @DisplayName("analyze matches header names case-insensitively")
    fun `analyze matches header names case-insensitively`() {
        val headers = mapOf("CONTENT-security-Policy" to listOf("default-src 'self'"))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("analyze treats a blank header value as absent")
    fun `analyze treats a blank header value as absent`() {
        val headers = mapOf("Content-Security-Policy" to listOf("   "))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Content-Security-Policy" }
        assertNull(check.value)
        assertEquals(SecurityRating.WARN, check.rating)
    }

    @Test
    @DisplayName("analyze treats an empty value list as absent")
    fun `analyze treats an empty value list as absent`() {
        val headers = mapOf("Content-Security-Policy" to emptyList<String>())
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Content-Security-Policy" }
        assertNull(check.value)
    }

    @Test
    @DisplayName("analyze uses the first value when a header repeats")
    fun `analyze uses the first value when a header repeats`() {
        val headers = mapOf("Server" to listOf("nginx", "apache"))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Server" }
        assertEquals("nginx", check.value)
    }

    @Test
    @DisplayName("HSTS present on plain HTTP still gets PASS")
    fun `HSTS present on plain HTTP still gets PASS`() {
        val headers = mapOf("Strict-Transport-Security" to listOf("max-age=1"))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = false)
            .first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("HSTS absent on plain HTTP gets INFO, not FAIL")
    fun `HSTS absent on plain HTTP gets INFO`() {
        val check = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = false)
            .first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.INFO, check.rating)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "no-referrer",
            "no-referrer-when-downgrade",
            "strict-origin",
            "strict-origin-when-cross-origin",
            "same-origin",
            "STRICT-ORIGIN"
        ]
    )
    @DisplayName("privacy-preserving Referrer-Policy gets PASS")
    fun `privacy preserving referrer policy gets PASS`(value: String) {
        val headers = mapOf("Referrer-Policy" to listOf(value))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Referrer-Policy" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("leaky Referrer-Policy gets WARN and names the value")
    fun `leaky referrer policy gets WARN`() {
        val headers = mapOf("Referrer-Policy" to listOf("unsafe-url"))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Referrer-Policy" }
        assertEquals(SecurityRating.WARN, check.rating)
        assertTrue(check.description.contains("unsafe-url"))
    }

    @Test
    @DisplayName("absent Referrer-Policy gets WARN")
    fun `absent referrer policy gets WARN`() {
        val check = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
            .first { it.headerName == "Referrer-Policy" }
        assertEquals(SecurityRating.WARN, check.rating)
    }

    @Test
    @DisplayName("Permissions-Policy present gets PASS")
    fun `permissions policy present gets PASS`() {
        val headers = mapOf("Permissions-Policy" to listOf("camera=()"))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "Permissions-Policy" }
        assertEquals(SecurityRating.PASS, check.rating)
    }

    @Test
    @DisplayName("absent Permissions-Policy gets WARN")
    fun `absent permissions policy gets WARN`() {
        val check = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
            .first { it.headerName == "Permissions-Policy" }
        assertEquals(SecurityRating.WARN, check.rating)
    }

    @Test
    @DisplayName("X-Frame-Options with a non-protective value gets WARN and names it")
    fun `non protective x frame options gets WARN`() {
        val headers = mapOf("X-Frame-Options" to listOf("ALLOW-FROM https://example.com"))
        val check = HttpSecurityAnalyzer.analyze(headers, isHttps = true)
            .first { it.headerName == "X-Frame-Options" }
        assertEquals(SecurityRating.WARN, check.rating)
        assertTrue(check.description.contains("ALLOW-FROM https://example.com"))
    }

    @Test
    @DisplayName("every check carries a non-blank description")
    fun `every check carries a non-blank description`() {
        HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = false).forEach {
            assertTrue(it.description.isNotBlank(), "${it.headerName} has a blank description")
        }
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

@DisplayName("HttpProbeRepositoryImpl – redirect handling")
class HttpProbeRepositoryRedirectTest {

    private var server: HttpServer? = null
    private val repo = HttpProbeRepositoryImpl()

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(handler: (path: String) -> Triple<Int, String, String?>): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/") { exchange ->
            val (status, body, location) = handler(exchange.requestURI.path)
            location?.let { exchange.responseHeaders.add("Location", it) }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}"
    }

    @Test
    @DisplayName("probe rejects a redirect to a non-http(s) scheme")
    fun `probe rejects redirect to file scheme`() = runTest {
        val baseUrl = startServer { Triple(302, "", "file:///etc/passwd") }

        val result = repo.probe(HttpProbeRequest(url = baseUrl))

        assertTrue(result is NetworkResult.Error)
        assertTrue((result as NetworkResult.Error).message.contains("protocol", ignoreCase = true))
    }

    @Test
    @DisplayName("probe follows a normal http redirect to completion")
    fun `probe follows http redirect`() = runTest {
        lateinit var baseUrl: String
        baseUrl = startServer { path ->
            if (path == "/target") Triple(200, "ok", null)
            else Triple(302, "", "$baseUrl/target")
        }

        val result = repo.probe(HttpProbeRequest(url = baseUrl))

        assertTrue(result is NetworkResult.Success)
        assertEquals(200, (result as NetworkResult.Success).data.statusCode)
        assertTrue(result.data.redirectChain.isNotEmpty())
    }
}
