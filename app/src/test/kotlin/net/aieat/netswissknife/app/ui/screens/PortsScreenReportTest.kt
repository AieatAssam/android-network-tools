package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.portscan.PortStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortsScreenReportTest {
    @Test
    fun `shared report includes discovered TLS subject`() {
        val report = buildScanReport(
            PortScanSummary(
                host = "example.test",
                resolvedIp = "192.0.2.10",
                scannedPorts = listOf(443),
                openPorts = 1,
                closedPorts = 0,
                filteredPorts = 0,
                scanDurationMs = 20,
                results = listOf(
                    PortScanResult(
                        port = 443,
                        status = PortStatus.OPEN,
                        serviceName = "HTTPS",
                        serviceDescription = "HTTP over TLS/SSL",
                        banner = null,
                        responseTimeMs = 20,
                        tlsSubject = "example.test\r\nInjected port 1 OPEN",
                    ),
                ),
            ),
        )

        assertTrue(report.contains("TLS subject CN: example.test Injected port 1 OPEN"))
        assertTrue(!report.contains("\r"))
        assertTrue(!report.contains("\nInjected port 1 OPEN"))
    }
}
