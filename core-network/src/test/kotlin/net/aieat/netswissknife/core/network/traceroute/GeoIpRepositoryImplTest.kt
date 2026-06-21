package net.aieat.netswissknife.core.network.traceroute

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

@DisplayName("GeoIpRepositoryImpl")
class GeoIpRepositoryImplTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(handler: (String) -> Pair<Int, String>): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/") { exchange ->
            val (status, body) = handler(exchange.requestURI.path)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}"
    }

    @Test
    @DisplayName("lookup does not throw and returns null when the GeoIP API errors")
    fun `lookup does not throw when API returns non-200`() = runTest {
        val baseUrl = startServer { 500 to "error" }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        val result = repo.lookup("8.8.8.8")

        assertNull(result)
    }

    @Test
    @DisplayName("repeated lookups after an API failure keep returning null without crashing")
    fun `repeated lookups after failure do not throw`() = runTest {
        val baseUrl = startServer { 500 to "error" }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        assertNull(repo.lookup("8.8.8.8"))
        assertNull(repo.lookup("8.8.8.8"))
    }

    @Test
    @DisplayName("lookup returns null for malformed JSON without crashing")
    fun `lookup does not throw on malformed response body`() = runTest {
        val baseUrl = startServer { 200 to "{ not valid json" }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        val result = repo.lookup("8.8.8.8")

        assertNull(result)
    }

    @Test
    @DisplayName("lookup parses a successful response and caches it")
    fun `lookup parses successful response`() = runTest {
        val baseUrl = startServer {
            200 to """{"ip":"8.8.8.8","city":"Mountain View","country":"US","loc":"37.38,-122.08","org":"AS15169 Google LLC"}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        val result = repo.lookup("8.8.8.8")

        assertEquals("United States", result?.country)
        assertEquals("Mountain View", result?.city)
    }

    @Test
    @DisplayName("lookup returns null immediately for private IPs without contacting the server")
    fun `lookup short-circuits for private IPs`() = runTest {
        val repo = GeoIpRepositoryImpl(baseUrl = "http://127.0.0.1:1")

        val result = repo.lookup("192.168.1.1")

        assertNull(result)
    }
}
