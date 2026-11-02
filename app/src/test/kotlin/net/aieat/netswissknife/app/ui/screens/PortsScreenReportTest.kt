package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.portscan.PortStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortsScreenReportTest {
    @Test
    fun `shared report includes discovered TLS subject`() {
        val report =
            buildScanReport(
                durationText = "20 ms",
                summary = PortScanSummary(
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

    @Test
    fun `report prints the duration in the same human format as the screen`() {
        val summary =
            PortScanSummary(
                host = "example.test",
                resolvedIp = null,
                scannedPorts = listOf(443),
                openPorts = 0,
                closedPorts = 1,
                filteredPorts = 0,
                scanDurationMs = 2083,
                results = emptyList(),
            )

        val report = buildScanReport(summary, durationText = "2.1 s")

        assertTrue(report.contains("Duration: 2.1 s\n"))
        assertFalse(report.contains("2083"))
    }
}
