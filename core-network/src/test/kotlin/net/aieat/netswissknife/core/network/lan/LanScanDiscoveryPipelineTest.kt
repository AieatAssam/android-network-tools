package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
    fun `tcp reset discovers host when icmp is filtered`() = runTest {
        val summary = repository(
            icmp = { _, _ -> null },
            tcp = { _, _, _ -> TcpPresence.Refused(80) },
        ).scan(LanScanRequest("192.168.1.0/30", gatewayIp = "192.168.1.1"))
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary

        assertEquals(2, summary.aliveHosts)
        assertTrue(summary.hosts.all { DiscoveryMethod.TCP_REFUSED in it.discoveredVia })
        assertTrue(summary.hosts.first { it.ip == "192.168.1.1" }.isGateway)
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
