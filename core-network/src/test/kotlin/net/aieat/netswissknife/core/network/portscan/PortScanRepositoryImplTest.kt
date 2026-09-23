package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Collections
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PortScanRepositoryImpl")
class PortScanRepositoryImplTest {

    @Test
    fun `default checker binds only selected local sockets before connect`() {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        var boundBeforeConnect = false
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                boundBeforeConnect = binder.boundTcpSockets.singleOrNull() === this
            }
        }

        val result = PortScanRepositoryImpl.defaultChecker(
            timeoutMs = 100,
            binder = binder,
            socketFactory = { socket }
        )(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 7)), 80)

        assertEquals(PortStatus.OPEN, result.status)
        assertTrue(boundBeforeConnect, "the selected socket must be bound before connect")
        assertEquals(listOf(socket), binder.boundTcpSockets)

        val defaultRouteBinder = FakeNetworkBinder(shouldBindResult = false)
        val unboundSocket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
        }
        PortScanRepositoryImpl.defaultChecker(
            timeoutMs = 100,
            binder = defaultRouteBinder,
            socketFactory = { unboundSocket }
        )(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 7)), 80)
        assertTrue(defaultRouteBinder.boundTcpSockets.isEmpty())
    }

    @Test
    fun `default checker surfaces local permission denial from socket creation`() {
        val checker = PortScanRepositoryImpl.defaultChecker(
            timeoutMs = 100,
            binder = FakeNetworkBinder(shouldBindResult = true),
            socketFactory = { throw SecurityException("permission denied") }
        )

        val error = assertThrows(LocalNetworkPermissionDeniedException::class.java) {
            checker(InetAddress.getLoopbackAddress(), 80)
        }
        assertEquals("permission denied", error.cause?.message)
    }

    // ── Helper checkers ────────────────────────────────────────────────────────

    private fun openChecker(rtMs: Long = 5L): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.OPEN, responseTimeMs = rtMs, banner = null)
    }

    private fun closedChecker(): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.CLOSED, responseTimeMs = 1L, banner = null)
    }

    private fun filteredChecker(): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.FILTERED, responseTimeMs = 2000L, banner = null)
    }

    private fun bannerChecker(banner: String): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.OPEN, responseTimeMs = 10L, banner = banner)
    }

    private fun testRepository(checker: PortConnectChecker) = PortScanRepositoryImpl(
        checker = checker,
        hostResolver = { java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 10)) }
    )

    // ── Emission count ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("emission count")
    inner class EmissionCount {

        @Test
        fun `emits Started one PortResult per port and one Complete`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val updates = repo.scan("example.com", ports, timeoutMs = 1000, concurrency = 10).toList()
            val portResults = updates.filterIsInstance<PortScanUpdate.PortResult>()
            val started = updates.filterIsInstance<PortScanUpdate.Started>()
            val completes = updates.filterIsInstance<PortScanUpdate.Complete>()
            assertEquals(1, started.size)
            assertEquals("192.0.2.10", started.single().resolvedIp)
            assertEquals(ports.size, started.single().totalCount)
            assertTrue(updates.first() is PortScanUpdate.Started)
            assertEquals(3, portResults.size)
            assertEquals(1, completes.size)
        }

        @Test
        fun `complete is the last event`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(22, 80), timeoutMs = 1000, concurrency = 10).toList()
            assertTrue(updates.last() is PortScanUpdate.Complete)
        }

        @Test
        fun `scanning empty port list emits Started and Complete`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", emptyList(), timeoutMs = 1000, concurrency = 10).toList()
            assertEquals(2, updates.size)
            assertTrue(updates.first() is PortScanUpdate.Started)
            assertTrue(updates.last() is PortScanUpdate.Complete)
        }
    }

    // ── Status mapping ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status mapping")
    inner class StatusMapping {

        @Test
        fun `open checker produces OPEN status`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals(PortStatus.OPEN, result.status)
        }

        @Test
        fun `closed checker produces CLOSED status`() = runTest {
            val repo = testRepository(checker = closedChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals(PortStatus.CLOSED, result.status)
        }

        @Test
        fun `filtered checker produces FILTERED status`() = runTest {
            val repo = testRepository(checker = filteredChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals(PortStatus.FILTERED, result.status)
        }
    }

    // ── Service resolution ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("service resolution")
    inner class ServiceResolution {

        @Test
        fun `port 80 resolves to HTTP`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("HTTP", result.serviceName)
        }

        @Test
        fun `port 443 resolves to HTTPS`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(443), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("HTTPS", result.serviceName)
        }

        @Test
        fun `port 22 resolves to SSH`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(22), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("SSH", result.serviceName)
        }

        @Test
        fun `unknown port has non-null service name fallback`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(12345), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertNotNull(result.serviceName)
        }
    }

    // ── Banner grabbing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("banner grabbing")
    inner class BannerGrabbing {

        @Test
        fun `banner from checker is propagated to result`() = runTest {
            val repo = testRepository(checker = bannerChecker("SSH-2.0-OpenSSH_9.0"))
            val updates = repo.scan("host", listOf(22), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("SSH-2.0-OpenSSH_9.0", result.banner)
        }

        @Test
        fun `null banner is preserved`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertTrue(result.banner == null)
        }
    }

    // ── Progress tracking ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("progress tracking")
    inner class ProgressTracking {

        @Test
        fun `emits each port in completion order`() = runTest {
            // Port 80 is supplied first but is held until port 443 has actually
            // been emitted downstream. A latch rather than a sleep keeps this
            // deterministic on a loaded CI runner: probes really do block, since
            // production socket probes run on Dispatchers.IO.
            val fastPortEmitted = CountDownLatch(1)
            val checker: PortConnectChecker = { _, port ->
                if (port == 80) {
                    assertTrue(
                        fastPortEmitted.await(10, TimeUnit.SECONDS),
                        "port 443 was never emitted"
                    )
                }
                PortConnectResult(PortStatus.OPEN, 1L, null)
            }
            val repo = testRepository(checker = checker)

            val results = repo.scan("host", listOf(80, 443), 1000, concurrency = 2)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .onEach { if (it.result.port == 443) fastPortEmitted.countDown() }
                .toList()

            assertEquals(listOf(443, 80), results.map { it.result.port })
            assertEquals(listOf(1, 2), results.map { it.scannedCount })
        }

        @Test
        fun `scannedCount increments per emission`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val portResults = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .toList()
            val counts = portResults.map { it.scannedCount }.sorted()
            assertEquals(listOf(1, 2, 3), counts)
        }

        @Test
        fun `totalCount matches port list size`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val portResults = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .toList()
            assertTrue(portResults.all { it.totalCount == 3 })
        }
    }

    // ── Summary ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolved endpoint")
    inner class ResolvedEndpoint {

        @Test
        fun `resolves hostname once and probes the same IPv4 address in the summary`() = runTest {
            val resolveCount = AtomicInteger()
            val selectedAddress = java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 44))
            val probeAddresses = Collections.synchronizedList(mutableListOf<java.net.InetAddress>())
            val checker: PortConnectChecker = { address, _ ->
                probeAddresses.add(address)
                PortConnectResult(PortStatus.OPEN, 1L, null)
            }
            val repo = PortScanRepositoryImpl(
                checker = checker,
                hostResolver = {
                    if (resolveCount.incrementAndGet() == 1) selectedAddress
                    else java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 45))
                }
            )

            val updates = repo.scan("router.example", listOf(80, 443, 8080), 1000, concurrency = 3).toList()
            val summary = updates.filterIsInstance<PortScanUpdate.Complete>().single().summary

            assertEquals(1, resolveCount.get())
            assertEquals(3, probeAddresses.size)
            assertTrue(probeAddresses.all { it === selectedAddress })
            assertEquals("router.example", summary.host)
            assertEquals(selectedAddress.hostAddress, summary.resolvedIp)
        }

        @Test
        fun `passes a selected IPv6 address unchanged to every probe`() = runTest {
            val selectedAddress = java.net.InetAddress.getByName("2001:db8::42")
            val probeAddresses = Collections.synchronizedList(mutableListOf<java.net.InetAddress>())
            val repo = PortScanRepositoryImpl(
                checker = { address, _ ->
                    probeAddresses.add(address)
                    PortConnectResult(PortStatus.OPEN, 1L, null)
                },
                hostResolver = { selectedAddress }
            )

            val summary = repo.scan("v6.example", listOf(22, 443), 1000, concurrency = 2)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .single()
                .summary

            assertEquals(2, probeAddresses.size)
            assertTrue(probeAddresses.all { it === selectedAddress })
            assertEquals(selectedAddress.hostAddress, summary.resolvedIp)
        }

        @Test
        fun `resolution failure stops before invoking any port checker`() = runTest {
            val probeCount = AtomicInteger()
            val repo = PortScanRepositoryImpl(
                checker = { _, _ ->
                    probeCount.incrementAndGet()
                    PortConnectResult(PortStatus.OPEN, 1L, null)
                },
                hostResolver = { throw java.net.UnknownHostException("no DNS answer") }
            )

            val error = assertThrows(PortScanHostResolutionException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repo.scan("missing.example", listOf(80), 1000, concurrency = 1).toList()
                }
            }

            assertTrue(error.message!!.contains("missing.example"))
            assertEquals(0, probeCount.get())
        }
    }

    @Nested
    @DisplayName("scan summary")
    inner class ScanSummary {

        @Test
        fun `summary open count matches open results`() = runTest {
            // Use port number to decide open/closed so the result is deterministic
            // regardless of concurrent execution order: 80 and 8080 are even → OPEN
            val alternating: PortConnectChecker = { _, port ->
                if (port % 2 == 0) PortConnectResult(PortStatus.OPEN, 5L, null)
                else PortConnectResult(PortStatus.CLOSED, 1L, null)
            }
            val repo = testRepository(checker = alternating)
            val ports = listOf(80, 443, 8080, 8443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(2, complete.summary.openPorts)
            assertEquals(2, complete.summary.closedPorts)
        }

        @Test
        fun `summary host matches input host`() = runTest {
            val repo = testRepository(checker = openChecker())
            val complete = repo.scan("example.com", listOf(80), timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals("example.com", complete.summary.host)
        }

        @Test
        fun `summary scannedPorts matches input ports`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(22, 80, 443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(ports.sorted(), complete.summary.scannedPorts.sorted())
        }

        @Test
        fun `summary results count matches port list size`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(22, 80, 443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(3, complete.summary.results.size)
        }
    }

    // ── WellKnownPorts ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WellKnownPorts lookup")
    inner class WellKnownPortsLookup {

        @Test
        fun `port 3306 returns MySQL`() {
            assertEquals("MySQL", WellKnownPorts.getInfo(3306)?.serviceName)
        }

        @Test
        fun `port 5432 returns PostgreSQL`() {
            assertEquals("PostgreSQL", WellKnownPorts.getInfo(5432)?.serviceName)
        }

        @Test
        fun `port 27017 returns MongoDB`() {
            assertEquals("MongoDB", WellKnownPorts.getInfo(27017)?.serviceName)
        }

        @Test
        fun `unknown port returns null info`() {
            assertTrue(WellKnownPorts.getInfo(12345) == null)
        }

        @Test
        fun `getServiceName for unknown port returns non-null fallback`() {
            assertTrue(WellKnownPorts.getServiceName(12345).isNotBlank())
        }
    }
}
