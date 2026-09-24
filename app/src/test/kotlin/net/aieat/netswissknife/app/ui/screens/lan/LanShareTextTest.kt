package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanDiagnosticReason
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanShareTextTest {
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

        val text = buildLanShareText(summary)

        assertTrue(text.contains("Confirmed hosts: 1 / 254"))
        assertTrue(text.contains("192.168.1.1 (router.local) [Router Vendor] 3ms ports:80,443"))
        assertFalse(text.contains("192.168.1.88"))
        assertFalse(text.contains("Connection refused"))
    }
}
