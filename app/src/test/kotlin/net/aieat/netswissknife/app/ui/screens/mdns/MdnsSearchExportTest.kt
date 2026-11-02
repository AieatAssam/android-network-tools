package net.aieat.netswissknife.app.ui.screens.mdns

import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdnsSearchExportTest {
    private val printer =
        DiscoveredService(
            serviceType = "_ipp._tcp",
            instanceName = "HP LaserJet._ipp._tcp.local",
            displayName = "HP LaserJet",
            hostname = "printer.local",
            port = 631,
            ipAddresses = listOf("192.168.1.20"),
        )
    private val television =
        DiscoveredService(
            serviceType = "_airplay._tcp",
            instanceName = "Living Room TV._airplay._tcp.local",
            displayName = "Living Room TV",
            hostname = "tv.local",
            port = 7000,
            ipAddresses = listOf("192.168.1.30"),
        )
    private val byType = mapOf("_airplay._tcp" to listOf(television), "_ipp._tcp" to listOf(printer))

    @Test
    fun `copy text carries the limit sentence in plain words, never the enum name`() {
        val report =
            buildMdnsShareText(
                services = emptyList(),
                isScanning = false,
                isCanceling = false,
                scanCanceled = false,
                truncationNote = "Some devices reported more than we show (first 16 addresses per service).",
            )

        assertTrue(report.contains("Some devices reported more than we show"), report)
        assertFalse(report.contains("ADDRESS_LIMIT"), report)
    }

    @Test
    fun `export only contains services that match the search filter`() {
        val filtered = filterMdnsServices(byType, "laserjet")

        val report =
            buildMdnsShareText(
                services = filtered.values.flatten(),
                isScanning = false,
                isCanceling = false,
                scanCanceled = false,
                truncationNote = null,
                filterQuery = "laserjet",
            )

        assertTrue(report.contains("HP LaserJet"), report)
        assertFalse(report.contains("Living Room TV"), report)
        assertTrue(report.contains("mDNS services: 1"), report)
        assertTrue(report.contains("Filter: laserjet"), report)
    }

    @Test
    fun `unfiltered export has no filter line and includes every service`() {
        val report =
            buildMdnsShareText(
                services = byType.values.flatten(),
                isScanning = false,
                isCanceling = false,
                scanCanceled = false,
                truncationNote = null,
            )

        assertTrue(report.contains("HP LaserJet"), report)
        assertTrue(report.contains("Living Room TV"), report)
        assertTrue(report.contains("mDNS services: 2"), report)
        assertFalse(report.contains("Filter:"), report)
    }

    @Test
    fun `no-match query leaves nothing to export`() {
        assertTrue(filterMdnsServices(byType, "zzqq").isEmpty())
    }

    @Test
    fun `blank query keeps every service`() {
        val filtered = filterMdnsServices(byType, "   ")
        assertEquals(2, filtered.values.flatten().size)
    }
}
