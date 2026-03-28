package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PortScanRepositoryImpl")
class PortScanRepositoryImplTest {

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

    // ── Emission count ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("emission count")
    inner class EmissionCount {

        @Test
        fun `emits one PortResult per port plus one Complete`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val updates = repo.scan("example.com", ports, timeoutMs = 1000, concurrency = 10).toList()
            val portResults = updates.filterIsInstance<PortScanUpdate.PortResult>()
            val completes = updates.filterIsInstance<PortScanUpdate.Complete>()
            assertEquals(3, portResults.size)
            assertEquals(1, completes.size)
        }

        @Test
        fun `complete is the last event`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(22, 80), timeoutMs = 1000, concurrency = 10).toList()
            assertTrue(updates.last() is PortScanUpdate.Complete)
        }

        @Test
        fun `scanning empty port list emits only Complete`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", emptyList(), timeoutMs = 1000, concurrency = 10).toList()
            assertEquals(1, updates.size)
            assertTrue(updates.first() is PortScanUpdate.Complete)
        }
    }

    // ── Status mapping ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status mapping")
    inner class StatusMapping {

        @Test
        fun `open checker produces OPEN status`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals(PortStatus.OPEN, result.status)
        }

        @Test
        fun `closed checker produces CLOSED status`() = runTest {
            val repo = PortScanRepositoryImpl(checker = closedChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals(PortStatus.CLOSED, result.status)
        }

        @Test
        fun `filtered checker produces FILTERED status`() = runTest {
            val repo = PortScanRepositoryImpl(checker = filteredChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals(PortStatus.FILTERED, result.status)
        }
    }

    // ── Service resolution ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("service resolution")
    inner class ServiceResolution {

        @Test
        fun `port 80 resolves to HTTP`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals("HTTP", result.serviceName)
        }

        @Test
        fun `port 443 resolves to HTTPS`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(443), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals("HTTPS", result.serviceName)
        }

        @Test
        fun `port 22 resolves to SSH`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(22), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals("SSH", result.serviceName)
        }

        @Test
        fun `unknown port has non-null service name fallback`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(12345), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertNotNull(result.serviceName)
        }
    }

    // ── Banner grabbing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("banner grabbing")
    inner class BannerGrabbing {

        @Test
        fun `banner from checker is propagated to result`() = runTest {
            val repo = PortScanRepositoryImpl(checker = bannerChecker("SSH-2.0-OpenSSH_9.0"))
            val updates = repo.scan("host", listOf(22), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertEquals("SSH-2.0-OpenSSH_9.0", result.banner)
        }

        @Test
        fun `null banner is preserved`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = (updates.first() as PortScanUpdate.PortResult).result
            assertTrue(result.banner == null)
        }
    }

    // ── Progress tracking ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("progress tracking")
    inner class ProgressTracking {

        @Test
        fun `scannedCount increments per emission`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val portResults = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .toList()
            val counts = portResults.map { it.scannedCount }.sorted()
            assertEquals(listOf(1, 2, 3), counts)
        }

        @Test
        fun `totalCount matches port list size`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val portResults = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .toList()
            assertTrue(portResults.all { it.totalCount == 3 })
        }
    }

    // ── Summary ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("scan summary")
    inner class ScanSummary {

        @Test
        fun `summary open count matches open results`() = runTest {
            var callIndex = 0
            val alternating: PortConnectChecker = { _, _ ->
                callIndex++
                if (callIndex % 2 == 1) PortConnectResult(PortStatus.OPEN, 5L, null)
                else PortConnectResult(PortStatus.CLOSED, 1L, null)
            }
            val repo = PortScanRepositoryImpl(checker = alternating)
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
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val complete = repo.scan("example.com", listOf(80), timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals("example.com", complete.summary.host)
        }

        @Test
        fun `summary scannedPorts matches input ports`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
            val ports = listOf(22, 80, 443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(ports.sorted(), complete.summary.scannedPorts.sorted())
        }

        @Test
        fun `summary results count matches port list size`() = runTest {
            val repo = PortScanRepositoryImpl(checker = openChecker())
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
