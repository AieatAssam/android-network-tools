package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanHost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanHostSortTest {
    private val hosts = listOf(
        LanHost("192.168.1.10", "zeta", "00:00:00:00:00:10", "Beta", listOf(80), 20),
        LanHost("192.168.1.2", "Alpha", "00:00:00:00:00:02", "Gamma", listOf(22, 443), 80),
        LanHost("192.168.1.3", null, null, "Alpha", emptyList(), 5),
    )

    @Test
    fun supportsEveryRequestedSortOrder() {
        assertEquals(listOf("192.168.1.2", "192.168.1.3", "192.168.1.10"), sortLanHosts(hosts, LanHostSortOrder.IP).map { it.ip })
        assertEquals(listOf("192.168.1.3", "192.168.1.10", "192.168.1.2"), sortLanHosts(hosts, LanHostSortOrder.LATENCY).map { it.ip })
        assertEquals(listOf("192.168.1.3", "192.168.1.2", "192.168.1.10"), sortLanHosts(hosts, LanHostSortOrder.HOSTNAME).map { it.ip })
        assertEquals(listOf("192.168.1.3", "192.168.1.10", "192.168.1.2"), sortLanHosts(hosts, LanHostSortOrder.VENDOR).map { it.ip })
        assertEquals(listOf("192.168.1.2", "192.168.1.10", "192.168.1.3"), sortLanHosts(hosts, LanHostSortOrder.OPEN_PORTS).map { it.ip })
    }

    @Test
    fun tlsActionsAreLimitedToConventionalTlsPorts() {
        listOf(443, 465, 636, 853, 989, 990, 992, 993, 995, 5061, 5223, 8443, 8883, 9443)
            .forEach { assertTrue(isLikelyTlsPort(it), "Expected TLS port $it") }
        listOf(22, 25, 53, 80, 110, 143, 8080, 9000)
            .forEach { assertFalse(isLikelyTlsPort(it), "Unexpected TLS port $it") }
    }
}
