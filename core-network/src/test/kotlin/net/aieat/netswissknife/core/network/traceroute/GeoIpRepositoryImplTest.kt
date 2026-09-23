package net.aieat.netswissknife.core.network.traceroute

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    @DisplayName("multiple hop lookups join one caller-owned operation")
    fun `multiple lookups share one session`() = runTest {
        val calls = AtomicInteger()
        val baseUrl = startServer {
            calls.incrementAndGet()
            200 to """{"country":"US","loc":"37.38,-122.08"}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)
        val session = newSession()

        val locations = OperationRunner.run(session) {
            listOf(
                repo.lookup("8.8.8.8", session),
                repo.lookup("1.1.1.1", session),
            )
        }

        assertEquals(2, locations.size)
        assertTrue(locations.all { it?.country == "United States" })
        assertEquals(2, calls.get())
        assertTrue(session.resources.isClosed)
    }

    @Test
    @DisplayName("lookup accepts a public response with bogon false")
    fun `lookup accepts explicit false bogon flag`() = runTest {
        val baseUrl = startServer {
            200 to """{"ip":"8.8.8.8","city":"Mountain View","country":"US","loc":"37.38,-122.08","org":"AS15169 Google LLC","bogon":false}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        val result = repo.lookup("8.8.8.8")

        assertEquals("United States", result?.country)
        assertEquals("Mountain View", result?.city)
    }

    @Test
    @DisplayName("lookup returns null when bogon is explicitly true")
    fun `lookup rejects explicit true bogon flag`() = runTest {
        val baseUrl = startServer {
            200 to """{"ip":"8.8.8.8","country":"US","loc":"37.38,-122.08","bogon":true}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        assertNull(repo.lookup("8.8.8.8"))
    }

    @Test
    @DisplayName("lookup accepts a response with no bogon field")
    fun `lookup accepts missing bogon flag`() = runTest {
        val baseUrl = startServer {
            200 to """{"ip":"8.8.8.8","country":"US","loc":"37.38,-122.08"}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        assertEquals("United States", repo.lookup("8.8.8.8")?.country)
    }

    @Test
    @DisplayName("lookup ignores bogon text inside an escaped JSON string")
    fun `lookup ignores bogon text inside string`() = runTest {
        val baseUrl = startServer {
            200 to """{"ip":"8.8.8.8","city":"Mountain View","note":"text says \"bogon\":true","country":"US","loc":"37.38,-122.08"}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        val result = repo.lookup("8.8.8.8")

        assertEquals("United States", result?.country)
        assertEquals("Mountain View", result?.city)
    }

    @Test
    @DisplayName("lookup ignores a nested bogon field")
    fun `lookup ignores nested bogon flag`() = runTest {
        val baseUrl = startServer {
            200 to """{"ip":"8.8.8.8","meta":{"bogon":true},"country":"US","loc":"37.38,-122.08"}"""
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        assertEquals("United States", repo.lookup("8.8.8.8")?.country)
    }

    @Test
    @DisplayName("lookup returns null immediately for private IPs without contacting the server")
    fun `lookup short-circuits for private IPs`() = runTest {
        val repo = GeoIpRepositoryImpl(baseUrl = "http://127.0.0.1:1")

        val result = repo.lookup("192.168.1.1")

        assertNull(result)
    }

    @Test
    @DisplayName("Stop disconnects a blocked GeoIP response and remains typed cancellation")
    fun `stop closes blocked response once without a late location`() = runTest {
        val connection = BlockingGeoIpConnection()
        val repo = GeoIpRepositoryImpl(
            baseUrl = "https://ipinfo.io",
            connectionFactory = GeoIpConnectionFactory { connection },
        )
        val session = newSession()
        val lookup = async(Dispatchers.IO) { repo.lookup("8.8.8.8", session) }

        assertTrue(withContext(Dispatchers.IO) { connection.readEntered.await(2, TimeUnit.SECONDS) })
        session.cancel(CancellationReason.USER_STOP)
        val failure = runCatching { withTimeoutIo { lookup.await() } }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test
    @DisplayName("deadline checks close-induced I/O before returning optional GeoIP data")
    fun `expired caller deadline remains typed and closes connection once`() = runTest {
        val fakeClock = MutableGeoIpClock()
        val connection = ExpiringGeoIpConnection(fakeClock)
        val repo = GeoIpRepositoryImpl(
            baseUrl = "https://ipinfo.io",
            connectionFactory = GeoIpConnectionFactory { connection },
            clock = fakeClock,
        )
        val session = newSession(clock = fakeClock)

        val failure = runCatching { repo.lookup("8.8.8.8", session) }.exceptionOrNull()

        assertTrue(
            failure is OperationDeadlineExceededException ||
                (failure is OperationCancellationException && failure.reason == CancellationReason.DEADLINE_EXCEEDED),
            "Expected typed deadline, got ${failure?.javaClass?.name}: ${failure?.message}",
        )
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test
    @DisplayName("lookup rejects a response body beyond its operation byte limit")
    fun `oversized body is not parsed or cached`() = runTest {
        val calls = AtomicInteger()
        val response = """{"city":"${"c".repeat(66_000)}","country":"US","loc":"37.38,-122.08"}"""
        val baseUrl = startServer {
            calls.incrementAndGet()
            200 to response
        }
        val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

        assertNull(repo.lookup("8.8.8.8"))
        assertNull(repo.lookup("8.8.8.8"))
        assertEquals(2, calls.get())
    }

    private fun newSession(
        clock: MonotonicClock = MonotonicClock { System.nanoTime() },
        timeoutMillis: Long = 5_000L,
    ) = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.INTERNET,
            timeoutMillis = timeoutMillis,
            maxConcurrentProbes = 1,
            maxResponseBytes = 65_536,
            clock = clock,
        )
    )

    private suspend fun <T> withTimeoutIo(block: suspend () -> T): T =
        kotlinx.coroutines.withTimeout(2_000) { block() }

    private class MutableGeoIpClock : MonotonicClock {
        @Volatile var nowNanos: Long = 0
        override fun nowNanos(): Long = nowNanos
    }

    private open class FakeGeoIpConnection(url: URL) : HttpURLConnection(url) {
        val disconnectCount = AtomicInteger()
        override fun connect() = Unit
        override fun disconnect() { disconnectCount.incrementAndGet() }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = HTTP_OK
    }

    private class BlockingGeoIpConnection : FakeGeoIpConnection(URL("https://ipinfo.io/8.8.8.8/json")) {
        val readEntered = CountDownLatch(1)
        private val disconnected = CountDownLatch(1)
        private val response = object : InputStream() {
            override fun read(): Int {
                readEntered.countDown()
                disconnected.await(2, TimeUnit.SECONDS)
                throw IOException("connection closed while reading")
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = read()
        }

        override fun getInputStream(): InputStream = response
        override fun disconnect() {
            super.disconnect()
            disconnected.countDown()
        }
    }

    private class ExpiringGeoIpConnection(
        private val clock: MutableGeoIpClock,
    ) : FakeGeoIpConnection(URL("https://ipinfo.io/8.8.8.8/json")) {
        private val response = object : InputStream() {
            override fun read(): Int {
                clock.nowNanos = 5_000_000_000L
                throw IOException("socket closed at deadline")
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = read()
        }

        override fun getInputStream(): InputStream = response
    }
}
