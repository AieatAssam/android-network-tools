package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.ResponseHeaderLimitException
import okhttp3.ResponseHeaderLimitKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Response header, trailer and field-count budgets enforced by the OkHttp engine over HTTP/1.1 and HTTP/2. */
class OkHttpEngineResponseLimitTest {
    private val servers = MockWebServerPool()

    @AfterEach
    fun tearDown() {
        servers.closeAll()
    }

    private fun server(protocols: List<Protocol> = listOf(Protocol.HTTP_1_1)): MockWebServer = servers.start(protocols)

    private fun repositoryFor(protocol: Protocol): HttpProbeRepositoryImpl = okHttpRepositoryFor(protocol)

    private fun assertHeaderLimit(
        result: NetworkResult<HttpProbeResult>,
        kind: ResponseHeaderLimitKind,
    ) {
        assertTrue(result is NetworkResult.Error, "over-limit response must be rejected: $result")
        val error = result as NetworkResult.Error
        assertEquals(ErrorCode.HTTP_RESPONSE_HEADERS_TOO_LARGE, error.info?.code)
        var cause = error.cause
        while (cause != null && cause !is ResponseHeaderLimitException) cause = cause.cause
        assertNotNull(cause, "typed parser rejection should reach the repository")
        assertEquals(kind, (cause as ResponseHeaderLimitException).kind)
    }

    @Test
    fun `HTTP1 and HTTP2 accept exact response metadata budgets and preserve duplicate values`() =
        runTest {
            for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
                val server = server(listOf(protocol))
                val response = MockResponse.Builder().code(200)
                repeat(97) { index -> response.addHeader(if (index % 2 == 0) "X-Test" else "x-test", "v$index") }
                response.addHeader("Set-Cookie", "a=1; Expires=Wed, 21 Oct 2030 07:28:00 GMT")
                response.addHeader("Set-Cookie", "b=2")
                server.enqueue(response.build())

                val result = repositoryFor(protocol).probe(HttpProbeRequest(url = server.url("/").toString()))

                assertTrue(
                    result is NetworkResult.Success<*>,
                    "$protocol exact field-count response should be accepted: $result",
                )
                val data = (result as NetworkResult.Success<*>).data as HttpProbeResult
                assertEquals(100, data.responseHeaders.values.sumOf { it.size })
                assertEquals(
                    (0 until 97).map { "v$it" },
                    data.responseHeaders.entries
                        .first { it.key.equals("X-Test", ignoreCase = true) }
                        .value,
                )
                assertEquals(
                    listOf("a=1; Expires=Wed, 21 Oct 2030 07:28:00 GMT", "b=2"),
                    data.responseHeaders.entries
                        .first { it.key.equals("Set-Cookie", ignoreCase = true) }
                        .value,
                )
            }
        }

    @Test
    fun `HTTP1 and HTTP2 reject the 101st response field with a typed limit`() =
        runTest {
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
    fun `HTTP1 and HTTP2 aggregate informational and final response fields`() =
        runTest {
            for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
                val server = server(listOf(protocol))
                val informational =
                    MockResponse
                        .Builder()
                        .code(103)
                        .apply {
                            repeat(50) { addHeader("X-Interim", "v") }
                        }.build()
                val finalResponse =
                    MockResponse
                        .Builder()
                        .code(200)
                        .apply {
                            repeat(50) { addHeader("X-Final", "v") }
                            addInformationalResponse(informational)
                        }.build()
                server.enqueue(finalResponse)

                val result = repositoryFor(protocol).probe(HttpProbeRequest(url = server.url("/").toString()))

                assertHeaderLimit(result, ResponseHeaderLimitKind.FIELD_COUNT)
            }
        }

    @Test
    fun `HTTP1 and HTTP2 aggregate trailers with response fields`() =
        runTest {
            for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
                val server = server(listOf(protocol))
                val trailers = Headers.Builder().apply { repeat(40) { add("X-Trailer", "v") } }.build()
                val response =
                    MockResponse
                        .Builder()
                        .code(200)
                        .apply {
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
    fun `HTTP1 and HTTP2 surface over-budget trailers when closing a body-limited response`() =
        runTest {
            for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
                val server = server(listOf(protocol))
                val trailers = Headers.Builder().apply { repeat(101) { add("X-Trailer", "v") } }.build()
                val response =
                    MockResponse
                        .Builder()
                        .code(200)
                        .apply {
                            if (protocol == Protocol.HTTP_1_1) {
                                chunkedBody("abcdefgh", 1)
                            } else {
                                removeHeader("Content-Length")
                                body("abcdefgh")
                            }
                            trailers(trailers)
                        }.build()
                server.enqueue(response)

                val result =
                    repositoryFor(protocol).probe(
                        HttpProbeRequest(url = server.url("/").toString(), maxResponseBodyBytes = 4),
                    )

                assertHeaderLimit(result, ResponseHeaderLimitKind.FIELD_COUNT)
            }
        }

    @Test
    fun `HTTP1 and HTTP2 count UTF8 bytes for exact and over value boundaries`() =
        runTest {
            for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
                val exactServer = server(listOf(protocol))
                val exactValue = "é".repeat(8_192) // 16,384 UTF-8 bytes, 8,192 UTF-16 code units.
                exactServer.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .addHeaderLenient("X-Test", exactValue)
                        .build(),
                )
                val exact = repositoryFor(protocol).probe(HttpProbeRequest(url = exactServer.url("/").toString()))
                assertTrue(exact is NetworkResult.Success<*>, "$protocol exact UTF-8 value boundary should be accepted")

                val overServer = server(listOf(protocol))
                overServer.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .addHeaderLenient("X-Test", exactValue + "é")
                        .build(),
                )
                val over = repositoryFor(protocol).probe(HttpProbeRequest(url = overServer.url("/").toString()))
                assertHeaderLimit(over, ResponseHeaderLimitKind.VALUE_BYTES)
            }
        }

    @Test
    fun `HTTP1 and HTTP2 count name value and framing bytes at aggregate boundary`() =
        runTest {
            for (protocol in listOf(Protocol.HTTP_1_1, Protocol.H2_PRIOR_KNOWLEDGE)) {
                val exactServer = server(listOf(protocol))
                val exactResponse = MockResponse.Builder().code(200)
                repeat(3) { exactResponse.addHeader("x", "a".repeat(16_379)) }
                exactResponse.addHeader("x", "a".repeat(16_360))
                exactServer.enqueue(exactResponse.build())
                val exact = repositoryFor(protocol).probe(HttpProbeRequest(url = exactServer.url("/").toString()))
                assertTrue(
                    exact is NetworkResult.Success<*>,
                    "$protocol exact aggregate boundary should be accepted: $exact",
                )
                val exactData = (exact as NetworkResult.Success<*>).data as HttpProbeResult
                val decodedMetadataBytes =
                    exactData.responseHeaders.entries.sumOf { (name, values) ->
                        values.sumOf { value ->
                            name.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size + 4
                        }
                    }
                assertEquals(
                    65_536,
                    decodedMetadataBytes,
                    "$protocol accepted fixture must reach the aggregate limit exactly",
                )

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
        val error =
            org.junit.jupiter.api.Assertions.assertThrows(ResponseHeaderLimitException::class.java) {
                net.aieat.netswissknife.core.network.httprobe.engine
                    .validateResponseHeaderLimits(headers)
            }
        assertEquals(ResponseHeaderLimitKind.FIELD_COUNT, error.kind)
    }
}
