package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanScanDiscoveryPipelineTest {
    @Test
    fun `icmp discovery records method and uses supplied gateway`() = runTest {
        val summary = repository(
            icmp = { ip, _ -> if (ip == "192.168.1.1") 7L else null },
            tcp = { _, _, _ -> TcpPresence.None },
        ).scan(LanScanRequest("192.168.1.0/30", gatewayIp = "192.168.1.2"))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertEquals(1, summary.aliveHosts)
        assertEquals(setOf(DiscoveryMethod.ICMP), summary.hosts.single().discoveredVia)
        assertFalse(summary.hosts.single().isGateway)
    }

    @Test
    fun `tcp refusal does not discover host when icmp is filtered`() = runTest {
        val updates = repository(
            icmp = { _, _ -> null },
            tcp = { _, _, _ -> TcpPresence.Refused(80) },
        ).scan(LanScanRequest("192.168.1.0/30", gatewayIp = "192.168.1.1"))
            .toList()

        val summary = updates.filterIsInstance<LanScanUpdate.ScanComplete>().single().summary
        assertEquals(0, summary.aliveHosts)
        assertTrue(summary.hosts.isEmpty())
        val progress = updates.filterIsInstance<LanScanUpdate.ScanProgress>()
        assertEquals(2, progress.size)
        assertEquals(2, progress.last().scannedCount)
        assertEquals(2, progress.last().totalCount)
        assertEquals(2, progress.last().uncertainCount)
        assertEquals(2, summary.uncertainHosts.size)
        assertTrue(summary.uncertainHosts.all { it.reason == LanScanDiagnosticReason.TCP_REFUSED })
    }

    @Test
    fun `one completed tcp connection confirms only that endpoint`() = runTest {
        val summary = repository(
            icmp = { _, _ -> null },
            tcp = { ip, _, _ ->
            if (ip == "192.168.1.1") TcpPresence.Open(443) else TcpPresence.Refused(80)
            },
        ).scan(LanScanRequest("192.168.1.0/30"))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertEquals(1, summary.aliveHosts)
        assertEquals("192.168.1.1", summary.hosts.single().ip)
        assertEquals(setOf(DiscoveryMethod.TCP_OPEN), summary.hosts.single().discoveredVia)
        assertEquals(1, summary.uncertainHosts.size)
    }

    @Test
    fun `uncertain endpoints do not invoke enrichment`() = runTest {
        var portCalls = 0
        var nameEnrichmentCalls = 0
        val summary = LanScanRepositoryImpl(
            icmpProbe = IcmpProbe { _, _ -> null },
            tcpProbe = TcpPresenceProbe { _, _, _ -> TcpPresence.Refused(80) },
            nameProbes = listOf(NameProbe { _, _ -> nameEnrichmentCalls++; "untrusted-name" }),
            macResolver = object : MacResolver {
                override val supported = true
                override suspend fun resolve(ip: String): String? = error("uncertain host enriched")
            },
            portChecker = { _, _, _ -> portCalls++; false },
        ).scan(LanScanRequest("192.168.1.0/30"))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertEquals(0, summary.aliveHosts)
        assertEquals(2, summary.uncertainCount)
        assertEquals(0, portCalls)
        assertEquals(0, nameEnrichmentCalls)
    }

    @Test
    fun `uncertain diagnostic details are bounded while counts continue`() = runTest {
        val summary = repository(
            icmp = { _, _ -> null },
            tcp = { _, _, _ -> TcpPresence.Refused(80) },
        ).scan(LanScanRequest("192.168.1.0/24", concurrency = 8))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertEquals(254, summary.totalScanned)
        assertEquals(254, summary.uncertainCount)
        assertEquals(100, summary.uncertainHosts.size)
    }

    @Test
    fun `cancellation from a local name probe cancels the full scan`() = runTest {
        val nameProbe = object : PresenceNameProbe {
            override suspend fun resolveName(ip: String, timeoutMs: Int): String? = null
            override suspend fun probePresence(ip: String, timeoutMs: Int): LocalProtocolReply? {
                throw CancellationException("probe cancelled")
            }
        }
        val scan = LanScanRepositoryImpl(
            icmpProbe = IcmpProbe { _, _ -> null },
            tcpProbe = TcpPresenceProbe { _, _, _ -> TcpPresence.None },
            nameProbes = listOf(nameProbe),
            macResolver = object : MacResolver {
                override val supported = false
                override suspend fun resolve(ip: String): String? = null
            },
            portChecker = { _, _, _ -> false },
        )

        var wasCancelled = false
        try {
            scan.scan(LanScanRequest("192.168.1.0/30")).toList()
        } catch (_: CancellationException) {
            wasCancelled = true
        }
        assertTrue(wasCancelled)
    }

    @Test
    fun `icmp success is confirmed without a tcp refusal method`() = runTest {
        val tcpTargets = mutableListOf<String>()
        val summary = LanScanRepositoryImpl(
            icmpProbe = IcmpProbe { ip, _ -> if (ip == "192.168.1.1") 3L else null },
            tcpProbe = TcpPresenceProbe { ip, _, _ -> tcpTargets += ip; TcpPresence.Refused(80) },
            nameProbes = emptyList(),
            macResolver = object : MacResolver {
                override val supported = false
                override suspend fun resolve(ip: String): String? = null
            },
            portChecker = { _, _, _ -> false },
        ).scan(LanScanRequest("192.168.1.0/30"))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertEquals(1, summary.aliveHosts)
        assertEquals(setOf(DiscoveryMethod.ICMP), summary.hosts.single().discoveredVia)
        assertEquals(listOf("192.168.1.2"), tcpTargets)
        assertTrue(summary.uncertainHosts.size <= 1)
    }

    @Test
    fun `mac is retried after probes and summary exposes resolver support`() = runTest {
        var calls = 0
        val resolver = object : MacResolver {
            override val supported = true
            override suspend fun resolve(ip: String): String? {
                calls++
                return if (calls > 1 && ip == "192.168.1.1") "B8:27:EB:00:00:01" else null
            }
        }
        val summary = LanScanRepositoryImpl(
            icmpProbe = IcmpProbe { _, _ -> 5L },
            tcpProbe = TcpPresenceProbe { _, _, _ -> TcpPresence.None },
            nameProbes = emptyList(),
            macResolver = resolver,
            portChecker = { _, _, _ -> false },
        ).scan(LanScanRequest("192.168.1.0/30"))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertTrue(summary.macResolutionSupported)
        assertEquals("B8:27:EB:00:00:01", summary.hosts.first { it.ip == "192.168.1.1" }.macAddress)
        assertEquals("Raspberry Pi Foundation", summary.hosts.first { it.ip == "192.168.1.1" }.vendor)
    }

    private fun repository(
        icmp: suspend (String, Int) -> Long?,
        tcp: suspend (String, List<Int>, Int) -> TcpPresence,
    ) = LanScanRepositoryImpl(
        icmpProbe = IcmpProbe(icmp),
        tcpProbe = TcpPresenceProbe(tcp),
        nameProbes = emptyList(),
        macResolver = object : MacResolver {
            override val supported = false
            override suspend fun resolve(ip: String): String? = null
        },
        portChecker = { _, _, _ -> false },
    )
}
