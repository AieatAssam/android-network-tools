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
    @DisplayName("private and reserved IPv4 ranges are skipped without opening a connection")
    fun `private and reserved address cases short-circuit lookup`() = runTest {
        val connectionAttempts = AtomicInteger()
        val repo = GeoIpRepositoryImpl("https://example.invalid") {
            connectionAttempts.incrementAndGet()
            error("Must not connect")
        }

        listOf(
            "192.168.1.100", // RFC 1918 192.168/16
            "10.0.0.1",      // RFC 1918 10/8
            "172.16.5.5",    // RFC 1918 172.16/12
            "127.0.0.1",     // loopback
            "169.254.1.1",   // link-local
        ).forEach { ip ->
            assertNull(repo.lookup(ip), "Expected $ip to be skipped")
        }

        assertEquals(0, connectionAttempts.get(), "Private-range lookups must not open connections")
    }

    @Test
    @DisplayName("all non-public IPv4 special-use ranges are skipped before opening a connection")
    fun `special use IPv4 ranges short circuit lookup`() = runTest {
        val connectionAttempts = AtomicInteger()
        val repo = GeoIpRepositoryImpl("https://example.invalid") {
            connectionAttempts.incrementAndGet()
            error("Must not connect")
        }

        listOf(
            "100.64.0.1",     // Shared address space / CGNAT (100.64.0.0/10)
            "100.127.255.254",
            "192.0.2.5",      // Documentation (TEST-NET-1)
            "198.51.100.7",   // Documentation (TEST-NET-2)
            "203.0.113.9",    // Documentation (TEST-NET-3)
            "198.18.0.1",     // Benchmarking (198.18.0.0/15)
            "198.19.255.254",
            "224.0.0.1",      // Multicast (224.0.0.0/4)
            "239.255.255.250",
        ).forEach { ip ->
            assertNull(repo.lookup(ip), "Expected special-use address $ip to be skipped")
        }

        assertEquals(0, connectionAttempts.get(), "Special-use IPv4 addresses must not open connections")
    }

    @Test
    @DisplayName("non-public IPv6 ranges and IPv4-mapped private addresses are skipped")
    fun `special use IPv6 and mapped IPv4 ranges short circuit lookup`() = runTest {
        val connectionAttempts = AtomicInteger()
        val repo = GeoIpRepositoryImpl("https://example.invalid") {
            connectionAttempts.incrementAndGet()
            error("Must not connect")
        }

        listOf(
            "::1",                    // IPv6 loopback
            "fe80::1",                // IPv6 link-local
            "febf:ffff::1",           // Last address in fe80::/10
            "fc00::1",                // IPv6 unique-local
            "fdff:ffff::1",           // Last address in fc00::/7
            "2001:db8::1",            // IPv6 documentation (2001:db8::/32)
            "::ffff:10.0.0.1",        // IPv4-mapped RFC 1918 address
            "::ffff:100.64.0.1",      // IPv4-mapped CGNAT address
            "::ffff:192.0.2.1",       // IPv4-mapped documentation address
            "64:ff9b::a00:1",         // NAT64 with embedded RFC 1918 address
            "64:ff9b::c000:201",      // NAT64 with embedded documentation address
        ).forEach { ip ->
            assertNull(repo.lookup(ip), "Expected special-use IPv6/mapped address $ip to be skipped")
        }

        assertEquals(0, connectionAttempts.get(), "Special-use IPv6 addresses must not open connections")
    }

    @Test
    @DisplayName("public IPv4 and IPv6 literals reach the injected connection factory")
    fun `public address literals are passed to the connection factory`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val repo = GeoIpRepositoryImpl(
            baseUrl = "https://geo.test",
            connectionFactory = GeoIpConnectionFactory { url ->
                requestedPaths += url.path
                ResponseGeoIpConnection(url)
            },
        )

        assertEquals("United States", repo.lookup("8.8.8.8")?.country)
        assertEquals("United States", repo.lookup("2001:4860:4860::8888")?.country)
        assertEquals("United States", repo.lookup("::ffff:8.8.8.8")?.country)
        assertEquals(
            listOf("/8.8.8.8/json", "/2001:4860:4860::8888/json", "/::ffff:8.8.8.8/json"),
            requestedPaths,
            "Public IPv4, IPv6, and IPv4-mapped IPv6 literals must reach the injected transport",
        )
    }

    @Test
    @DisplayName("malformed address input fails closed before opening a connection")
    fun `malformed address inputs do not reach DNS or connection factory`() = runTest {
        val connectionAttempts = AtomicInteger()
        val repo = GeoIpRepositoryImpl("https://example.invalid") {
            connectionAttempts.incrementAndGet()
            error("Malformed addresses must be rejected before transport")
        }

        listOf(
            "not-an-address.example",
            "bad host",
            "256.1.1.1",
            "8.8.8.999",
            "2001:::1",
        ).forEach { input ->
            assertNull(repo.lookup(input), "Malformed input $input must fail closed")
        }

        assertEquals(0, connectionAttempts.get(), "Malformed addresses must not cause DNS/network activity")
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

    private class ResponseGeoIpConnection(url: URL) : FakeGeoIpConnection(url) {
        override fun getInputStream(): InputStream =
            """{"country":"US","loc":"37.38,-122.08"}""".byteInputStream()
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
