package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.app.ui.screens.mdns.MdnsShareLabels
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
        val labels =
            MdnsShareLabels(
                servicesCount = { count -> "mDNS services: $count" },
                filter = { query -> "Filter: $query" },
                stopping = "Stopping scan",
                inProgress = "Scan in progress",
                canceled = "canceled; partial results",
                complete = "Scan complete",
                addresses = { joined -> "Addresses: $joined" },
                txt = { key, value -> "TXT $key=$value" },
            )
        val report = buildMdnsShareText(listOf(service), false, false, true, null, labels = labels)
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
        val report = buildWifiShareText(result, testWifiShareLabels())
        assertTrue(report.contains("cached or stale"))
        assertTrue(report.contains("30000 ms"))
        assertTrue(report.contains("refresh FAILED"))
        assertTrue(report.contains("Wi-Fi is disabled"))
        assertTrue(report.contains("Location Services are disabled"))
    }

    @Test
    fun `Wi-Fi export takes every label from the supplied resources`() {
        val result =
            WifiScanResult(
                accessPoints = emptyList(),
                channels = emptyList(),
                connectedNetwork = null,
                scanTimestampMs = 0L,
                isWifiEnabled = true,
                isFresh = true,
                scanAgeMs = null,
                refreshStatus = WifiScanRefreshStatus.UPDATED,
                locationEnabled = true,
            )
        val report = buildWifiShareText(result, testWifiShareLabels())
        assertTrue(report.contains("0 access points"))
        assertTrue(report.contains("fresh"))
        assertTrue(report.contains("unknown"))
        assertFalse(report.contains("Wi-Fi is disabled"))
        assertFalse(report.contains("Location Services are disabled"))
    }

    private fun testWifiShareLabels() =
        WifiShareLabels(
            accessPoints = { count -> "$count access points" },
            freshness = { fresh -> if (fresh) "fresh" else "cached or stale" },
            sampleAge = { ageMs -> ageMs?.let { "$it ms" } ?: "unknown" },
            refreshStatus = { status -> "refresh ${status.name}" },
            sampleTime = { time -> "time $time" },
            wifiDisabled = "Wi-Fi is disabled",
            locationDisabled = "Location Services are disabled",
            rssi = { rssi -> "$rssi dBm" },
            bandChannel = { band, channel -> "${band.name} channel $channel" },
        )

    @Test
    fun `partial topology export does not invent query time`() {
        val graph = TopologyGraph(nodes = emptyList(), links = emptyList(), seedIp = "192.168.1.1", queriedAt = 0L)
        val report = topologyReport(graph, "Discovery canceled; partial results.")
        assertTrue(report.contains("Discovery canceled; partial results."))
        assertFalse(report.contains("Queried:"))
    }
}
