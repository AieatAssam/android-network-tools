package net.aieat.netswissknife.core.network.netinfo

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OkHttpPublicIpRepositoryTest {
    private val servers = mutableListOf<MockWebServer>()

    @AfterEach
    fun tearDown() = servers.forEach(MockWebServer::close)

    private fun server(): MockWebServer {
        val server = MockWebServer()
        servers += server
        server.start()
        return server
    }

    /** Without a client the production defaults apply, including redirects being refused. */
    private fun repository(
        server: MockWebServer,
        client: OkHttpClient? = null,
    ): OkHttpPublicIpRepository {
        val v4 = server.url("/v4")
        val v6 = server.url("/v6")
        return if (client == null) {
            OkHttpPublicIpRepository(ipv4Url = v4, ipv6Url = v6)
        } else {
            OkHttpPublicIpRepository(client = client, ipv4Url = v4, ipv6Url = v6)
        }
    }

    private fun body(text: String): MockResponse =
        MockResponse
            .Builder()
            .body(text)
            .build()

    @Test
    fun `IPv4 lookup returns the address from the body`() =
        runTest {
            val server = server()
            server.enqueue(body("203.0.113.7\n"))

            val result = repository(server).fetch(IpFamily.IPV4)

            assertEquals("203.0.113.7", (result as NetworkResult.Success).data)
            val request = server.takeRequest()
            assertEquals("/v4", request.url.encodedPath)
            assertEquals("GET", request.method)
        }

    @Test
    fun `IPv6 lookup uses its own endpoint`() =
        runTest {
            val server = server()
            server.enqueue(body("2001:db8::7"))

            val result = repository(server).fetch(IpFamily.IPV6)

            assertEquals("2001:db8::7", (result as NetworkResult.Success).data)
            assertEquals("/v6", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `non-success status is a request failure`() =
        runTest {
            val server = server()
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(503)
                    .body("busy")
                    .build(),
            )

            val result = repository(server).fetch(IpFamily.IPV4)

            val error = assertInstanceOf(NetworkResult.Error::class.java, result)
            assertEquals(ErrorCode.NETWORK_REQUEST_FAILED, error.info?.code)
        }

    @Test
    fun `redirects are not followed`() =
        runTest {
            val server = server()
            val elsewhere = server.url("/elsewhere").toString()
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(302)
                    .addHeader("Location", elsewhere)
                    .build(),
            )

            val result = repository(server).fetch(IpFamily.IPV4)

            assertInstanceOf(NetworkResult.Error::class.java, result)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `body that is not an address is a failure`() =
        runTest {
            val server = server()
            server.enqueue(body("<html>captive portal</html>"))

            val result = repository(server).fetch(IpFamily.IPV4)

            assertInstanceOf(NetworkResult.Error::class.java, result)
        }

    @Test
    fun `oversized body is rejected`() =
        runTest {
            val server = server()
            server.enqueue(body("1".repeat(4096)))

            val result = repository(server).fetch(IpFamily.IPV4)

            assertInstanceOf(NetworkResult.Error::class.java, result)
        }

    @Test
    fun `slow response reports a timeout`() =
        runTest {
            val server = server()
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("203.0.113.7")
                    .bodyDelay(5, TimeUnit.SECONDS)
                    .build(),
            )
            val client =
                OkHttpClient
                    .Builder()
                    .readTimeout(200, TimeUnit.MILLISECONDS)
                    .build()

            val result = repository(server, client).fetch(IpFamily.IPV4)

            val error = assertInstanceOf(NetworkResult.Error::class.java, result)
            assertEquals(ErrorCode.NETWORK_TIMEOUT, error.info?.code)
        }
}
