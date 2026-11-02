package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanDiagnosticReason
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanShareTextTest {
    private val labels =
        LanShareLabels(
            title = "LAN scan – 192.168.1.0/24",
            headline = "1 device found",
            facts = "254 addresses · 1.3 s",
            latencyFormat = "%d ms",
            portsFormat = "ports:%s",
        )

    @Test
    fun `share includes confirmed hosts and excludes uncertain addresses`() {
        val summary = LanScanSummary(
            subnet = "192.168.1.0/24",
            totalScanned = 254,
            aliveHosts = 1,
            scanDurationMs = 1_250,
            hosts = listOf(
                LanHost(
                    ip = "192.168.1.1",
                    hostname = "router.local",
                    macAddress = null,
                    vendor = "Router Vendor",
                    openPorts = listOf(80, 443),
                    pingTimeMs = 3,
                ),
            ),
            uncertainHosts = listOf(
                LanScanDiagnostic(
                    ip = "192.168.1.88",
                    reason = LanScanDiagnosticReason.TCP_REFUSED,
                    port = 80,
                    detail = "Connection refused",
                ),
            ),
            uncertainCount = 1,
        )

        val text = buildLanShareText(summary, labels)

        assertTrue(text.startsWith("LAN scan – 192.168.1.0/24\n1 device found\n254 addresses · 1.3 s\n"))
        assertFalse(text.contains("Confirmed hosts"))
        assertFalse(text.contains("Duration:"))
        assertTrue(text.contains("192.168.1.1 (router.local) [Router Vendor] 3 ms ports:80,443"))
        assertFalse(text.contains("192.168.1.88"))
        assertFalse(text.contains("Connection refused"))
    }

    @Test
    fun `share omits latency for hosts without an icmp reply`() {
        val summary =
            LanScanSummary(
                subnet = "192.168.1.0/24",
                totalScanned = 254,
                aliveHosts = 1,
                scanDurationMs = 1_250,
                hosts =
                    listOf(
                        LanHost(
                            ip = "192.168.1.9",
                            hostname = null,
                            macAddress = null,
                            vendor = null,
                            openPorts = listOf(443),
                            pingTimeMs = null,
                        ),
                    ),
            )

        val text = buildLanShareText(summary, labels)

        assertTrue(text.contains("192.168.1.9 ports:443"))
        assertFalse(text.contains(" ms"))
    }
}
