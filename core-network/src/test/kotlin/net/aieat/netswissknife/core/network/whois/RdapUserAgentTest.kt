package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RDAP servers reject OkHttp's default User-Agent with HTTP 403, so every RDAP request (bootstrap
 * and lookup) must carry the application's own identifier.
 */
class RdapUserAgentTest {
    private val servers = mutableListOf<MockWebServer>()

    @AfterEach
    fun tearDown() = servers.forEach(MockWebServer::close)

    @Test
    fun `IP lookups send the injected application User-Agent`() =
        runTest {
            val server = server()
            server.enqueue(MockResponse.Builder().body("{}").build())
            val client = client(server, userAgent = USER_AGENT)

            client.lookup("8.8.8.8", WhoisQueryType.IPV4)

            assertEquals(USER_AGENT, server.takeRequest().headers["User-Agent"])
        }

    @Test
    fun `domain lookups send the same User-Agent on the bootstrap and the lookup`() =
        runTest {
            val server = server()
            val bootstrap = """{"services":[[["com"],["${server.url("/")}"]]]}"""
            server.enqueue(MockResponse.Builder().body(bootstrap).build())
            server.enqueue(MockResponse.Builder().body("""{"objectClassName":"domain"}""").build())
            val client = client(server, userAgent = USER_AGENT)

            client.lookup("example.com", WhoisQueryType.DOMAIN)

            val bootstrapRequest = server.takeRequest()
            val lookupRequest = server.takeRequest()
            assertEquals("/dns.json", bootstrapRequest.url.encodedPath)
            assertEquals(USER_AGENT, bootstrapRequest.headers["User-Agent"])
            assertEquals(USER_AGENT, lookupRequest.headers["User-Agent"])
        }

    @Test
    fun `the application User-Agent names the app, its version and its project`() {
        val agent = rdapUserAgent("1.2.3")

        assertEquals("NetSwissKnife/1.2.3 (+https://github.com/AieatAssam/android-network-tools)", agent)
        assertTrue(agent.startsWith("NetSwissKnife/"))
        assertFalse(agent.contains("okhttp", ignoreCase = true))
    }

    private fun server(): MockWebServer =
        MockWebServer().also {
            servers += it
            it.start()
        }

    private fun client(
        server: MockWebServer,
        userAgent: String,
    ): RdapClient =
        RdapClient(
            OkHttpClient(),
            bootstrapUrl = server.url("/dns.json"),
            ipRedirectorUrl = server.url("/"),
            asnRedirectorUrl = server.url("/"),
            nowNanos = System::nanoTime,
            dns = Dns.SYSTEM,
            allowPrivateHostnamesForTests = setOf("localhost", "127.0.0.1", "::1"),
            userAgent = userAgent,
        )

    private companion object {
        val USER_AGENT = rdapUserAgent("9.9.9-test")
    }
}
