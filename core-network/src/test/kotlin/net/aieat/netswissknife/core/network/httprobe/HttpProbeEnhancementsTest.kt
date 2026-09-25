package net.aieat.netswissknife.core.network.httprobe

import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimingBreakdown
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory

class HttpProbeEnhancementsTest {
    @Test
    fun `HSTS rejects junk duplicate max age and duplicate header fields and ignores HTTP`() {
        val invalid =
            listOf(
                listOf("max-age=31536000junk"),
                listOf("max-age=31536000; max-age=63072000"),
                listOf("max-age=63072000", "max-age=63072000"),
            )
        invalid.forEach { values ->
            val check =
                HttpSecurityAnalyzer
                    .analyze(
                        mapOf("Strict-Transport-Security" to values),
                        isHttps = true,
                    ).first { it.headerName == "Strict-Transport-Security" }
            assertEquals(SecurityRating.WARN, check.rating, values.toString())
        }

        val overHttp =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Strict-Transport-Security" to listOf("max-age=1")),
                    isHttps = false,
                ).first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.INFO, overHttp.rating)
        assertEquals("httprobe_sec_hsts_ignored_http", overHttp.descriptionKey)
    }

    @Test
    fun `CSP uses effective script directive precedence and checks script src elem`() {
        val safeOverride =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Content-Security-Policy" to listOf("default-src 'unsafe-inline'; script-src 'self'")),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, safeOverride.rating)

        val unsafeElement =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf(
                        "Content-Security-Policy" to
                            listOf(
                                "default-src 'self'; script-src 'self'; " +
                                    "script-src-elem 'unsafe-inline'; script-src-attr 'self'",
                            ),
                    ),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.WARN, unsafeElement.rating)
        assertEquals("httprobe_sec_csp_unsafe_inline", unsafeElement.descriptionKey)
    }

    @Test
    fun `CSP nonce or hash disables unsafe inline warning but unsafe eval still warns`() {
        val safeWithNonce =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Content-Security-Policy" to listOf("script-src 'unsafe-inline' 'nonce-aBc123'")),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, safeWithNonce.rating)

        val safeWithHash =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf(
                        "Content-Security-Policy" to
                            listOf(
                                "script-src-elem 'unsafe-inline' 'sha256-${"A".repeat(43)}='; " +
                                    "script-src-attr 'self'",
                            ),
                    ),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, safeWithHash.rating)

        val evalStillUnsafe =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf(
                        "Content-Security-Policy" to
                            listOf("script-src 'unsafe-inline' 'nonce-aBc123' 'unsafe-eval'"),
                    ),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.WARN, evalStillUnsafe.rating)
    }

    @Test
    fun `CSP unsafe eval follows script src fallback independently of element directives`() {
        val unsafeFallback =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf(
                        "Content-Security-Policy" to
                            listOf("default-src 'self' 'unsafe-eval'; " + "script-src-elem 'self'"),
                    ),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.WARN, unsafeFallback.rating)
    }

    @Test
    fun `CSP uses first duplicate directive and strict dynamic disables unsafe inline`() {
        val duplicateScript =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Content-Security-Policy" to listOf("script-src 'self'; script-src 'unsafe-eval'")),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, duplicateScript.rating)

        val strictDynamic =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Content-Security-Policy" to listOf("script-src 'unsafe-inline' 'strict-dynamic'")),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, strictDynamic.rating)

        val duplicateFrameAncestor =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Content-Security-Policy" to listOf("frame-ancestors 'self'; frame-ancestors *")),
                    isHttps = true,
                ).first { it.headerName == "X-Frame-Options" }
        assertEquals(SecurityRating.PASS, duplicateFrameAncestor.rating)
    }

    @Test
    fun `CSP nonce source accepts base64url and malformed unquoted sources do not suppress unsafe inline`() {
        val urlSafeNonce =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Content-Security-Policy" to listOf("script-src 'unsafe-inline' 'nonce-a_b-c'")),
                    isHttps = true,
                ).first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.PASS, urlSafeNonce.rating)

        for (malformed in listOf(
            "script-src 'unsafe-inline' nonce-a_b-c",
            "script-src 'unsafe-inline' strict-dynamic",
        )) {
            val check =
                HttpSecurityAnalyzer
                    .analyze(
                        mapOf("Content-Security-Policy" to listOf(malformed)),
                        isHttps = true,
                    ).first { it.headerName == "Content-Security-Policy" }
            assertEquals(SecurityRating.WARN, check.rating, malformed)
        }
    }

    @Test
    fun `duplicate CSP values are all inspected and wildcard or malformed frame ancestors do not pass`() {
        val duplicate =
            mapOf(
                "Content-Security-Policy" to listOf("script-src 'self'"),
                "content-security-policy" to listOf("script-src 'unsafe-eval'"),
            )
        val csp =
            HttpSecurityAnalyzer
                .analyze(duplicate, isHttps = true)
                .first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.WARN, csp.rating)

        for (directive in listOf("frame-ancestors *", "frame-ancestors", "frame-ancestors 'self")) {
            val xfo =
                HttpSecurityAnalyzer
                    .analyze(
                        mapOf("Content-Security-Policy" to listOf(directive)),
                        isHttps = true,
                    ).first { it.headerName == "X-Frame-Options" }
            assertTrue(xfo.rating != SecurityRating.PASS, directive)
        }
    }

    @Test
    fun `duplicate X frame options do not trust only a protective first value`() {
        for (values in listOf(listOf("DENY", "ALLOW-FROM https://example.test"), listOf("DENY", "DENY"))) {
            val xfo =
                HttpSecurityAnalyzer
                    .analyze(
                        mapOf("X-Frame-Options" to values),
                        isHttps = true,
                    ).first { it.headerName == "X-Frame-Options" }
            assertEquals(SecurityRating.WARN, xfo.rating)
        }
    }

    @Test
    fun `duplicate singleton security headers are not accepted by trusting the first value`() {
        val checks =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf(
                        "X-Content-Type-Options" to listOf("nosniff", "invalid"),
                        "Referrer-Policy" to listOf("strict-origin", "unsafe-url"),
                        "Permissions-Policy" to listOf("camera=()", "camera=(self)"),
                        "Cross-Origin-Opener-Policy" to listOf("same-origin", "unsafe-none"),
                        "Cross-Origin-Embedder-Policy" to listOf("require-corp", "unsafe-none"),
                    ),
                    isHttps = true,
                ).associateBy { it.headerName }
        assertEquals(SecurityRating.FAIL, checks.getValue("X-Content-Type-Options").rating)
        assertEquals(SecurityRating.WARN, checks.getValue("Referrer-Policy").rating)
        assertEquals(SecurityRating.WARN, checks.getValue("Permissions-Policy").rating)
        assertEquals(SecurityRating.WARN, checks.getValue("Cross-Origin-Opener-Policy").rating)
        assertTrue(checks.getValue("Cross-Origin-Embedder-Policy").rating != SecurityRating.PASS)
    }

    @Test
    fun `timing bar separates setup phases from total TTFB`() {
        val breakdown =
            HttpTimingBreakdown.from(
                HttpTimings(dnsMs = 5, connectMs = 10, tlsMs = 15, ttfbMs = 40, transferMs = 7),
            )
        assertEquals(10L, breakdown.serverWaitMs)
        assertEquals(40L, breakdown.ttfbTotalMs)

        val overlapping =
            HttpTimingBreakdown.from(
                HttpTimings(dnsMs = 20, connectMs = 30, tlsMs = 10, ttfbMs = 40),
            )
        assertEquals(0L, overlapping.serverWaitMs)
        assertEquals(40L, overlapping.ttfbTotalMs)
    }

    @Test
    fun `HSTS grades a one year policy as strong and short policies as warning`() {
        val strong =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Strict-Transport-Security" to listOf("max-age=63072000; includeSubDomains; preload")),
                    isHttps = true,
                ).first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.PASS, strong.rating)
        assertEquals("httprobe_sec_hsts_pass_strong", strong.descriptionKey)

        val short =
            HttpSecurityAnalyzer
                .analyze(
                    mapOf("Strict-Transport-Security" to listOf("max-age=300")),
                    isHttps = true,
                ).first { it.headerName == "Strict-Transport-Security" }
        assertEquals(SecurityRating.WARN, short.rating)
        assertEquals("httprobe_sec_hsts_short", short.descriptionKey)
    }

    @Test
    fun `CSP unsafe script directives warn and frame ancestors satisfy frame protection`() {
        val checks =
            HttpSecurityAnalyzer.analyze(
                mapOf("Content-Security-Policy" to listOf("default-src 'self'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'")),
                isHttps = true,
            )
        val csp = checks.first { it.headerName == "Content-Security-Policy" }
        assertEquals(SecurityRating.WARN, csp.rating)
        assertEquals("httprobe_sec_csp_unsafe_inline", csp.descriptionKey)
        val frame = checks.first { it.headerName == "X-Frame-Options" }
        assertEquals(SecurityRating.PASS, frame.rating)
        assertEquals("httprobe_sec_xfo_via_csp", frame.descriptionKey)
    }

    @Test
    fun `cookie checks report flags without copying cookie values into findings`() {
        val complete =
            CookieAnalyzer
                .analyze(
                    mapOf("set-cookie" to listOf("sid=secret; Secure; HttpOnly; SameSite=Lax")),
                    isHttps = true,
                ).single()
        assertEquals(SecurityRating.PASS, complete.rating)
        assertEquals("Set-Cookie: sid", complete.headerName)
        assertNull(complete.value)

        val noSecure =
            CookieAnalyzer
                .analyze(
                    mapOf("Set-Cookie" to listOf("sid=x; HttpOnly; SameSite=Lax")),
                    isHttps = true,
                ).single()
        assertEquals(SecurityRating.WARN, noSecure.rating)
        assertEquals("httprobe_sec_cookie_missing_secure", noSecure.descriptionKey)

        val noHttpOnly =
            CookieAnalyzer
                .analyze(
                    mapOf("Set-Cookie" to listOf("sid=x; Secure; SameSite=Lax")),
                    isHttps = true,
                ).single()
        assertEquals(SecurityRating.WARN, noHttpOnly.rating)
        assertEquals("httprobe_sec_cookie_missing_httponly", noHttpOnly.descriptionKey)

        val malformed = CookieAnalyzer.analyze(mapOf("Set-Cookie" to listOf("not-a-cookie")), isHttps = true).single()
        assertEquals(SecurityRating.WARN, malformed.rating)
        assertEquals("httprobe_sec_cookie_malformed", malformed.descriptionKey)

        val plainHttp =
            CookieAnalyzer
                .analyze(
                    mapOf("Set-Cookie" to listOf("sid=x; HttpOnly; SameSite=Lax")),
                    isHttps = false,
                ).single()
        assertEquals(SecurityRating.INFO, plainHttp.rating)

        val rejectedSameSiteNone =
            CookieAnalyzer
                .analyze(
                    mapOf("Set-Cookie" to listOf("sid=x; HttpOnly; SameSite=None")),
                    isHttps = false,
                ).single()
        assertEquals(SecurityRating.WARN, rejectedSameSiteNone.rating)
        assertEquals("httprobe_sec_cookie_samesite_none_requires_secure", rejectedSameSiteNone.descriptionKey)

        val rejectedSecureOverHttp =
            CookieAnalyzer
                .analyze(
                    mapOf("Set-Cookie" to listOf("sid=x; Secure; HttpOnly; SameSite=None")),
                    isHttps = false,
                ).single()
        assertEquals(SecurityRating.WARN, rejectedSecureOverHttp.rating)
        assertEquals("httprobe_sec_cookie_secure_from_http", rejectedSecureOverHttp.descriptionKey)
    }

    @Test
    fun `curl export quotes request URL and restricts HTTPS redirects`() {
        val get = CurlExporter.build(HttpProbeRequest(url = "https://example.test/a?x=1"))
        assertEquals("curl -X 'GET' --proto-redir '=https' --max-redirs 10 -L 'https://example.test/a?x=1'", get)
        val http = CurlExporter.build(HttpProbeRequest(url = "http://example.test/a"))
        assertEquals("curl -X 'GET' --proto-redir '=http,https' --max-redirs 10 -L 'http://example.test/a'", http)

        val post =
            CurlExporter.build(
                HttpProbeRequest(
                    url = "https://example.test/submit",
                    method = HttpMethod.POST,
                    headers = listOf("X-Name" to "O'Reilly"),
                    body = "{\"message\":\"it's ok\"}",
                    followRedirects = false,
                ),
            )
        assertEquals(
            "curl -X 'POST' -H 'X-Name: O'\\''Reilly' --data-raw '{\"message\":\"it'\\''s ok\"}' 'https://example.test/submit'",
            post,
        )
    }

    @Test
    fun `curl export prevents origin redirects from replaying custom headers or body`() {
        val withAuthorization =
            HttpProbeRequest(
                url = "https://example.test/start",
                headers = listOf("Authorization" to "Bearer private-token"),
                followRedirects = true,
            )
        val command = CurlExporter.build(withAuthorization)
        assertTrue(command.contains("Authorization: Bearer private-token"))
        assertTrue(!command.contains(" -L"))
        assertTrue(CurlExporter.redirectFollowingSuppressed(withAuthorization))

        val withBody =
            HttpProbeRequest(
                url = "https://example.test/start",
                method = HttpMethod.POST,
                body = "private payload",
                followRedirects = true,
            )
        assertTrue(!CurlExporter.build(withBody).contains(" -L"))

        val postWithoutBody =
            HttpProbeRequest(
                url = "https://example.test/submit",
                method = HttpMethod.POST,
                followRedirects = true,
            )
        assertTrue(!CurlExporter.build(postWithoutBody).contains(" -L"))
        assertTrue(CurlExporter.redirectFollowingSuppressed(postWithoutBody))

        listOf(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS).forEach { method ->
            val request =
                HttpProbeRequest(
                    url = "https://example.test/resource",
                    method = method,
                    followRedirects = true,
                )
            assertTrue(!CurlExporter.build(request).contains(" -L"), method.name)
        }
    }

    @Test
    fun `JSON pretty printer formats values and rejects malformed text`() {
        assertEquals("{\n  \"a\": 1\n}", JsonPrettyPrinter.prettyPrint("{\"a\":1}"))
        assertEquals("[\n  1,\n  2\n]", JsonPrettyPrinter.prettyPrint("[1,2]"))
        assertNull(JsonPrettyPrinter.prettyPrint("<html>no</html>"))
    }

    @Test
    fun `JSON pretty printer handles a half megabyte document within the planned budget`() {
        val input = "{\"payload\":\"${"x".repeat(512 * 1024)}\"}"
        val threadBean = ManagementFactory.getThreadMXBean()
        val cpuTimingSupported = threadBean.isCurrentThreadCpuTimeSupported && threadBean.isThreadCpuTimeEnabled
        val wallStart = System.nanoTime()
        val cpuStart = if (cpuTimingSupported) threadBean.currentThreadCpuTime else wallStart
        val formatted = JsonPrettyPrinter.prettyPrint(input)
        val wallElapsedMs = (System.nanoTime() - wallStart) / 1_000_000
        val elapsedMs =
            if (cpuTimingSupported) {
                (threadBean.currentThreadCpuTime - cpuStart) / 1_000_000
            } else {
                wallElapsedMs
            }
        assertNotNull(formatted)
        assertTrue(
            elapsedMs <= 200,
            "512 KiB JSON formatting used ${elapsedMs}ms CPU (${wallElapsedMs}ms wall)",
        )
    }
}
