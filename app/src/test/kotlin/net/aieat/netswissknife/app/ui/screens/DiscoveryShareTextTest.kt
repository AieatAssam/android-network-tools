package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.app.ui.screens.mdns.buildMdnsShareText
import net.aieat.netswissknife.app.ui.screens.topology.topologyReport
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.topology.TopologyGraph
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscoveryShareTextTest {
    @Test
    fun `mDNS export preserves TXT and canceled discovery context`() {
        val service =
            DiscoveredService(
                serviceType = "_http._tcp",
                instanceName = "printer._http._tcp.local",
                displayName = "Printer",
                hostname = "printer.local",
                port = 80,
                ipAddresses = listOf("192.168.1.2"),
                txtRecords = mapOf("path" to "/status", "note" to "Office"),
            )
        val report = buildMdnsShareText(listOf(service), false, false, true, emptySet())
        assertTrue(report.contains("canceled; partial results"))
        assertTrue(report.contains("TXT path=/status"))
        assertTrue(report.contains("TXT note=Office"))
        assertTrue(report.contains("192.168.1.2"))
    }

    @Test
    fun `Wi-Fi export identifies retained cache after failed refresh`() {
        val result =
            WifiScanResult(
                accessPoints = emptyList(),
                channels = emptyList(),
                connectedNetwork = null,
                scanTimestampMs = 0L,
                isWifiEnabled = false,
                isFresh = false,
                scanAgeMs = 30_000L,
                refreshStatus = WifiScanRefreshStatus.FAILED,
                locationEnabled = false,
            )
        val report = buildWifiShareText(result)
        assertTrue(report.contains("cached or stale"))
        assertTrue(report.contains("30000 ms"))
        assertTrue(report.contains("FAILED"))
        assertTrue(report.contains("Wi-Fi is disabled"))
        assertTrue(report.contains("Location Services are disabled"))
    }

    @Test
    fun `partial topology export does not invent query time`() {
        val graph = TopologyGraph(nodes = emptyList(), links = emptyList(), seedIp = "192.168.1.1", queriedAt = 0L)
        val report = topologyReport(graph, "Discovery canceled; partial results.")
        assertTrue(report.contains("Discovery canceled; partial results."))
        assertFalse(report.contains("Queried:"))
    }
}
