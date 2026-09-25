package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.MonotonicClock
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Headers
import okhttp3.ResponseHeaderLimitException
import okhttp3.ResponseHeaderLimitKind
import okhttp3.Authenticator
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngine
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineCall
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineRequest
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineResponse
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import net.aieat.netswissknife.core.network.httprobe.engine.OkHttpEngine
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.ErrorCode
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong
import java.nio.file.Files
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OkHttpEngineTest {
    private val servers = mutableListOf<MockWebServer>()

    @AfterEach
    fun tearDown() {
        servers.forEach(MockWebServer::close)
    }

    private fun server(protocols: List<Protocol> = listOf(Protocol.HTTP_1_1)): MockWebServer =
        MockWebServer().also { it.protocols = protocols; it.start(); servers += it }

    private fun repositoryFor(protocol: Protocol): HttpProbeRepositoryImpl =
        HttpProbeRepositoryImpl(
            OkHttpEngine(OkHttpClient.Builder().protocols(listOf(protocol)).build())
        )

    private fun assertHeaderLimit(result: NetworkResult<HttpProbeResult>, kind: ResponseHeaderLimitKind) {
        assertTrue(result is NetworkResult.Error, "over-limit response must be rejected: $result")
        val error = result as NetworkResult.Error
        assertEquals(ErrorCode.HTTP_RESPONSE_HEADERS_TOO_LARGE, error.info?.code)
        var cause = error.cause
        while (cause != null && cause !is ResponseHeaderLimitException) cause = cause.cause
        assertNotNull(cause, "typed parser rejection should reach the repository")
        assertEquals(kind, (cause as ResponseHeaderLimitException).kind)
    }

    @Test
    fun `adapter reports HTTP1 protocol timing and identity encoding with bounded body`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/plain; charset=UTF-8").body("abcdefgh").build())

        val result = HttpProbeRepositoryImpl().probe(HttpProbeRequest(url = server.url("/").toString(), maxResponseBodyBytes = 4))

        assertTrue(result is net.aieat.netswissknife.core.network.NetworkResult.Success<*>)
        val data = (result as net.aieat.netswissknife.core.network.NetworkResult.Success<*>).data as HttpProbeResult
        assertEquals("abcd", data.responseBody)
        assertEquals(4L, data.responseBodyBytes)
        assertTrue(data.responseBodyTruncated)
        assertEquals("http/1.1", data.protocol)
        assertTrue(data.timings.totalMs >= (data.timings.ttfbMs ?: 0L))
        assertTrue(data.timings.dnsMs == null || data.timings.dnsMs >= 0L)
        assertTrue(data.timings.connectMs == null || data.timings.connectMs >= 0L)
        assertTrue(data.timings.ttfbMs == null || data.timings.ttfbMs >= 0L)
        assertEquals("identity", server.takeRequest()!!.headers["Accept-Encoding"])
    }

    @Test
    fun `adapter maps HTTP2 prior knowledge and returns error response body`() = runTest {
        val server = server(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        server.enqueue(MockResponse.Builder().code(418).body("teapot").build())

        val result = HttpProbeRepositoryImpl(OkHttpEngine(OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()))
            .probe(HttpProbeRequest(url = server.url("/").toString()))

        assertTrue(result is net.aieat.netswissknife.core.network.NetworkResult.Success<*>)
        val data = (result as net.aieat.netswissknife.core.network.NetworkResult.Success<*>).data as HttpProbeResult
        assertEquals(418, data.statusCode)
        assertEquals("teapot", data.responseBody)
        assertTrue(data.protocol.startsWith("h2"), "reported protocol was ${data.protocol}")
        assertNotNull(server.takeRequest())
    }

    @Test
    fun `HTTP1 and HTTP2 accept exact response metadata budgets and preserve duplicate values`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val server = server(listOf(protocol))
            val response = MockResponse.Builder().code(200)
            repeat(97) { index -> response.addHeader(if (index % 2 == 0) "X-Test" else "x-test", "v$index") }
            response.addHeader("Set-Cookie", "a=1; Expires=Wed, 21 Oct 2030 07:28:00 GMT")
            response.addHeader("Set-Cookie", "b=2")
            server.enqueue(response.build())

            val result = repositoryFor(protocol).probe(HttpProbeRequest(url = server.url("/").toString()))

            assertTrue(result is NetworkResult.Success<*>, "$protocol exact field-count response should be accepted: $result")
            val data = (result as NetworkResult.Success<*>).data as HttpProbeResult
            assertEquals(100, data.responseHeaders.values.sumOf { it.size })
            assertEquals((0 until 97).map { "v$it" }, data.responseHeaders.entries.first { it.key.equals("X-Test", ignoreCase = true) }.value)
            assertEquals(
                listOf("a=1; Expires=Wed, 21 Oct 2030 07:28:00 GMT", "b=2"),
                data.responseHeaders.entries.first { it.key.equals("Set-Cookie", ignoreCase = true) }.value,
            )
        }
    }

    @Test
    fun `HTTP1 and HTTP2 reject the 101st response field with a typed limit`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val server = server(listOf(protocol))
            val response = MockResponse.Builder().code(200)
            repeat(101) { response.addHeader("X-Test", "v") }
            server.enqueue(response.build())

            val result = repositoryFor(protocol).probe(HttpProbeRequest(url = server.url("/").toString()))

            assertHeaderLimit(result, ResponseHeaderLimitKind.FIELD_COUNT)
        }
    }

    @Test
    fun `HTTP1 and HTTP2 aggregate informational and final response fields`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val server = server(listOf(protocol))
            val informational = MockResponse.Builder().code(103).apply {
                repeat(50) { addHeader("X-Interim", "v") }
            }.build()
            val finalResponse = MockResponse.Builder().code(200).apply {
                repeat(50) { addHeader("X-Final", "v") }
                addInformationalResponse(informational)
            }.build()
            server.enqueue(finalResponse)

            val result = repositoryFor(protocol).probe(HttpProbeRequest(url = server.url("/").toString()))

            assertHeaderLimit(result, ResponseHeaderLimitKind.FIELD_COUNT)
        }
    }

    @Test
    fun `HTTP1 and HTTP2 aggregate trailers with response fields`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val server = server(listOf(protocol))
            val trailers = Headers.Builder().apply { repeat(40) { add("X-Trailer", "v") } }.build()
            val response = MockResponse.Builder().code(200).apply {
                repeat(if (protocol == Protocol.HTTP_1_1) 60 else 61) { addHeader("X-Initial", "v") }
                if (protocol == Protocol.HTTP_1_1) {
                    chunkedBody("x", 1)
                } else {
                    removeHeader("Content-Length")
                    body("x")
                }
                trailers(trailers)
            }.build()
            server.enqueue(response)

            val result = repositoryFor(protocol).probe(HttpProbeRequest(url = server.url("/").toString()))

            assertHeaderLimit(result, ResponseHeaderLimitKind.FIELD_COUNT)
        }
    }

    @Test
    fun `HTTP1 and HTTP2 surface over-budget trailers when closing a body-limited response`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val server = server(listOf(protocol))
            val trailers = Headers.Builder().apply { repeat(101) { add("X-Trailer", "v") } }.build()
            val response = MockResponse.Builder().code(200).apply {
                if (protocol == Protocol.HTTP_1_1) {
                    chunkedBody("abcdefgh", 1)
                } else {
                    removeHeader("Content-Length")
                    body("abcdefgh")
                }
                trailers(trailers)
            }.build()
            server.enqueue(response)

            val result = repositoryFor(protocol).probe(
                HttpProbeRequest(url = server.url("/").toString(), maxResponseBodyBytes = 4),
            )

            assertHeaderLimit(result, ResponseHeaderLimitKind.FIELD_COUNT)
        }
    }

    @Test
    fun `HTTP1 and HTTP2 count UTF8 bytes for exact and over value boundaries`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val exactServer = server(listOf(protocol))
            val exactValue = "é".repeat(8_192) // 16,384 UTF-8 bytes, 8,192 UTF-16 code units.
            exactServer.enqueue(MockResponse.Builder().code(200).addHeaderLenient("X-Test", exactValue).build())
            val exact = repositoryFor(protocol).probe(HttpProbeRequest(url = exactServer.url("/").toString()))
            assertTrue(exact is NetworkResult.Success<*>, "$protocol exact UTF-8 value boundary should be accepted")

            val overServer = server(listOf(protocol))
            overServer.enqueue(MockResponse.Builder().code(200).addHeaderLenient("X-Test", exactValue + "é").build())
            val over = repositoryFor(protocol).probe(HttpProbeRequest(url = overServer.url("/").toString()))
            assertHeaderLimit(over, ResponseHeaderLimitKind.VALUE_BYTES)
        }
    }

    @Test
    fun `HTTP1 and HTTP2 count name value and framing bytes at aggregate boundary`() = runTest {
        for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
            val exactServer = server(listOf(protocol))
            val exactResponse = MockResponse.Builder().code(200)
            repeat(3) { exactResponse.addHeader("x", "a".repeat(16_379)) }
            exactResponse.addHeader("x", "a".repeat(16_360))
            exactServer.enqueue(exactResponse.build())
            val exact = repositoryFor(protocol).probe(HttpProbeRequest(url = exactServer.url("/").toString()))
            assertTrue(exact is NetworkResult.Success<*>, "$protocol exact aggregate boundary should be accepted: $exact")
            val exactData = (exact as NetworkResult.Success<*>).data as HttpProbeResult
            val decodedMetadataBytes = exactData.responseHeaders.entries.sumOf { (name, values) ->
                values.sumOf { value -> name.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size + 4 }
            }
            assertEquals(65_536, decodedMetadataBytes, "$protocol accepted fixture must reach the aggregate limit exactly")

            val overServer = server(listOf(protocol))
            val overResponse = MockResponse.Builder().code(200)
            repeat(3) { overResponse.addHeader("x", "a".repeat(16_379)) }
            overResponse.addHeader("x", "a".repeat(16_361))
            overServer.enqueue(overResponse.build())
            val over = repositoryFor(protocol).probe(HttpProbeRequest(url = overServer.url("/").toString()))
            assertHeaderLimit(over, ResponseHeaderLimitKind.AGGREGATE_BYTES)
        }
    }

    @Test
    fun `injected response validation rejects fields before header map and security checks`() {
        val headers = Headers.Builder().apply { repeat(101) { add("X-Test", "v") } }.build()
        val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseHeaderLimitException::class.java) {
            net.aieat.netswissknife.core.network.httprobe.engine.validateResponseHeaderLimits(headers)
        }
        assertEquals(ResponseHeaderLimitKind.FIELD_COUNT, error.kind)
    }

    @Test
    fun `TLS adapter negotiates HTTP2 through ALPN and reports protocol`() = runTest {
        val heldCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(heldCertificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(heldCertificate.certificate).build()
        val server = MockWebServer().also {
            it.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            it.useHttps(serverTls.sslSocketFactory())
            it.start()
            servers += it
        }
        server.enqueue(MockResponse.Builder().code(200).body("secure").build())
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()

        val result = HttpProbeRepositoryImpl(OkHttpEngine(client)).probe(
            HttpProbeRequest(url = server.url("/").toString())
        )

        assertTrue(result is NetworkResult.Success<*>)
        val data = (result as NetworkResult.Success<*>).data as HttpProbeResult
        assertEquals("secure", data.responseBody)
        assertEquals("h2", data.protocol)
        assertTrue((data.timings.tlsMs ?: -1L) >= 0L)
    }

    @Test
    fun `OkHttp HTTPS downgrade block never sends request to HTTP destination`() = runTest {
        val heldCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(heldCertificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(heldCertificate.certificate).build()
        val source = MockWebServer().also {
            it.protocols = listOf(Protocol.HTTP_1_1)
            it.useHttps(serverTls.sslSocketFactory())
            it.start()
            servers += it
        }
        val destination = server()
        source.enqueue(MockResponse.Builder().code(302).addHeader("Location", destination.url("/private?token=secret").toString()).build())
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()

        val result = HttpProbeRepositoryImpl(OkHttpEngine(client)).probe(
            HttpProbeRequest(url = source.url("/start").toString())
        )

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(HttpProbeBlockedRedirectException.CODE, error.code)
        assertEquals(302, (error.cause as HttpProbeBlockedRedirectException).statusCode)
        assertEquals(1, source.requestCount)
        assertEquals(0, destination.requestCount)
    }

    @Test
    fun `adapter manual redirect preserves status and Location evidence`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/final").build())
        server.enqueue(MockResponse.Builder().code(200).body("done").build())

        val result = HttpProbeRepositoryImpl().probe(HttpProbeRequest(url = server.url("/start").toString()))

        assertTrue(result is net.aieat.netswissknife.core.network.NetworkResult.Success<*>)
        val data = (result as net.aieat.netswissknife.core.network.NetworkResult.Success<*>).data as HttpProbeResult
        assertEquals("done", data.responseBody)
        assertEquals(1, data.redirectHops.size)
        assertEquals(302, data.redirectHops.single().statusCode)
        assertEquals("/final", data.redirectHops.single().location)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `bodyless POST PUT and PATCH send valid empty request entities`() = runTest {
        val server = server()
        listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).forEach { method ->
            server.enqueue(MockResponse.Builder().code(200).body("ok").build())
            val result = HttpProbeRepositoryImpl().probe(
                HttpProbeRequest(url = server.url("/$method").toString(), method = method, body = null)
            )
            assertTrue(result is NetworkResult.Success<*>, "$method should accept an absent body")
            val recorded = server.takeRequest()!!
            assertEquals(method.name, recorded.method)
            assertEquals(0L, recorded.bodySize)
        }
    }

    @Test
    fun `cross origin redirect strips URL userinfo`() = runTest {
        val source = server()
        val destination = server()
        val credentialedDestination = destination.url("/target").newBuilder()
            .username("bob").password("destination-secret").build()
        source.enqueue(MockResponse.Builder().code(307).addHeader("Location", credentialedDestination.toString()).build())
        destination.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val result = HttpProbeRepositoryImpl().probe(
            HttpProbeRequest(
                url = source.url("/start").toString().replace("http://", "http://alice:source-secret@"),
                method = HttpMethod.POST,
                body = "payload",
                approveCrossOriginEntityReplay = { true },
            )
        )

        assertTrue(result is net.aieat.netswissknife.core.network.NetworkResult.Success<*>)
        assertEquals(null, source.takeRequest()!!.headers["Authorization"])
        val destinationRequest = destination.takeRequest()!!
        assertEquals(null, destinationRequest.headers["Authorization"])
        assertEquals("POST", destinationRequest.method)
        assertEquals("payload", destinationRequest.body!!.utf8())
    }

    @Test
    fun `engine does not inherit ambient cookies or authenticator credentials`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().code(401).body("unauthorized").build())
        var authenticatorCalls = 0
        val ambientClient = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: okhttp3.HttpUrl) = listOf(
                    Cookie.Builder().name("ambient").value("cookie-secret").domain("localhost").path("/").build()
                )
            })
            .authenticator(Authenticator { _, response ->
                authenticatorCalls++
                response.request.newBuilder().header("Authorization", "Bearer ambient-secret").build()
            })
            .build()

        val result = HttpProbeRepositoryImpl(OkHttpEngine(ambientClient)).probe(
            HttpProbeRequest(url = server.url("/").toString())
        )

        assertTrue(result is NetworkResult.Success<*>)
        val recorded = server.takeRequest()!!
        assertEquals(null, recorded.headers["Cookie"])
        assertEquals(null, recorded.headers["Authorization"])
        assertEquals(0, authenticatorCalls)
    }

    @Test
    fun `engine ignores configured cache`() = runTest {
        val server = server()
        repeat(2) {
            server.enqueue(
                MockResponse.Builder().code(200).addHeader("Cache-Control", "public, max-age=600")
                    .addHeader("ETag", "cached-response").body("ok").build()
            )
        }
        val cacheDirectory = Files.createTempDirectory("httprobe-cache-test").toFile()
        val cache = okhttp3.Cache(cacheDirectory, 1_000_000L)
        try {
            val engine = OkHttpEngine(OkHttpClient.Builder().cache(cache).build())
            val repository = HttpProbeRepositoryImpl(engine)
            repeat(2) {
                val result = repository.probe(HttpProbeRequest(url = server.url("/cache").toString()))
                assertTrue(result is NetworkResult.Success<*>)
            }
            assertEquals(2, server.requestCount, "HTTP probe calls must not read or populate an ambient cache")
        } finally {
            cache.close()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun `malformed URL parser errors stay URL free`() = runTest {
        val result = HttpProbeRepositoryImpl().probe(HttpProbeRequest(url = "http://example.test/[private-token"))
        assertTrue(result is net.aieat.netswissknife.core.network.NetworkResult.Error)
        assertFalse((result as net.aieat.netswissknife.core.network.NetworkResult.Error).message.contains("private-token"))
    }

    @Test
    fun `manual redirect timing sums per-call values and limits each call to request timeout`() = runTest {
        val now = AtomicLong(0L)
        val fake = SequenceEngine(
            listOf(
                HttpEngineResponse(302, "Found", mapOf("Location" to listOf("/next")), null, "http/1.1", HttpTimings(1, 2, null, 3, 4, 10)),
                HttpEngineResponse(200, "OK", emptyMap(), ByteArrayInputStream(byteArrayOf(1)), "h2", HttpTimings(5, 6, 7, 8, 9, 20)),
            )
        ) { callIndex -> if (callIndex == 0) now.addAndGet(200_000_000L) }
        val repository = HttpProbeRepositoryImpl(fake).also { it.clock = MonotonicClock { now.get() } }
        val result = repository.probe(HttpProbeRequest(url = "http://example.test/start", timeoutMs = 700))

        assertTrue(result is NetworkResult.Success<*>)
        val data = (result as NetworkResult.Success<*>).data as HttpProbeResult
        val timings = data.timings
        assertEquals(6L, timings.dnsMs)
        assertEquals(8L, timings.connectMs)
        assertEquals(7L, timings.tlsMs)
        assertEquals(11L, timings.ttfbMs)
        assertEquals(9L, timings.transferMs)
        assertEquals(listOf(700, 500), fake.requests.map { it.timeoutMs })
        assertEquals("h2", data.protocol)
    }

    @Test
    fun `session stop cancels OkHttp call and closes its response body`() = runTest {
        val server = server()
        server.enqueue(
            MockResponse.Builder().code(200).body("x".repeat(100)).throttleBody(1, 1, java.util.concurrent.TimeUnit.SECONDS).build()
        )
        val session = HttpProbeOperation.newSession(timeoutMillis = 5_000)
        val repository = HttpProbeRepositoryImpl()
        val probe = async(Dispatchers.IO) {
            runCatching { repository.probe(HttpProbeRequest(url = server.url("/").toString()), session) }.exceptionOrNull()
        }

        withContext(Dispatchers.IO) { withTimeout(5_000) { server.takeRequest() } }
        session.cancel(CancellationReason.USER_STOP)
        val failure = withContext(Dispatchers.IO) { withTimeout(5_000) { probe.await() } }
        assertTrue(failure is OperationCancellationException)
        assertEquals(CancellationReason.USER_STOP, (failure as OperationCancellationException).reason)
    }

    private class SequenceEngine(
        responses: List<HttpEngineResponse>,
        private val afterExecute: (callIndex: Int) -> Unit = {},
    ) : HttpEngine {
        private val pending = ArrayDeque(responses)
        val requests = mutableListOf<HttpEngineRequest>()
        override fun newCall(request: HttpEngineRequest): HttpEngineCall {
            requests += request
            val callIndex = requests.lastIndex
            val response = pending.removeFirst()
            return object : HttpEngineCall {
                override suspend fun execute(): HttpEngineResponse = response.also { afterExecute(callIndex) }
                override fun close() = Unit
            }
        }
    }
}
