package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.PORT_CONCURRENCY_STEPS
import net.aieat.netswissknife.core.network.lan.DiscoveryMethod
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanOperationBudget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanHostFilterTest {
    private val hosts =
        listOf(
            LanHost("10.0.0.1", "router", null, "Intel Corporate", listOf(80), 2, isGateway = true),
            LanHost("10.0.0.20", "pi400", null, null, emptyList(), 9),
            LanHost("10.0.0.21", "3dprinter", null, "Raspberry Pi Trading Ltd", listOf(22), 12),
        )

    @Test
    fun `search ignores spaces around the query`() {
        assertEquals(
            filterLanHosts(hosts, "pi", LanHostFilter.All),
            filterLanHosts(hosts, " pi ", LanHostFilter.All),
        )
        assertEquals(listOf("10.0.0.20", "10.0.0.21"), filterLanHosts(hosts, " pi ", LanHostFilter.All).map { it.ip })
    }

    @Test
    fun `filters select the gateway or hosts with open ports`() {
        assertEquals(listOf("10.0.0.1"), filterLanHosts(hosts, "", LanHostFilter.Gateway).map { it.ip })
        assertEquals(
            listOf("10.0.0.1", "10.0.0.21"),
            filterLanHosts(hosts, "", LanHostFilter.HasPorts).map { it.ip },
        )
    }

    @Test
    fun `stored filter names fall back to all`() {
        assertEquals(LanHostFilter.HasPorts, LanHostFilter.fromStoredName("HasPorts"))
        assertEquals(LanHostFilter.All, LanHostFilter.fromStoredName("Removed"))
        assertEquals(LanHostFilter.All, LanHostFilter.fromStoredName(null))
    }

    @Test
    fun `presence labels follow probe order and leave out reverse DNS`() {
        val labels = lanPresenceLabels(setOf(DiscoveryMethod.RDNS, DiscoveryMethod.MDNS, DiscoveryMethod.ICMP))

        assertEquals(listOf(R.string.lan_discovery_icmp, R.string.lan_discovery_mdns), labels)
        assertTrue(lanPresenceLabels(setOf(DiscoveryMethod.RDNS)).isEmpty())
    }

    @Test
    fun `concurrency that already fits is kept`() {
        assertEquals(50, lanConcurrencyToFit(targetCount = 254, timeoutMs = 2_000, current = 50))
    }

    @Test
    fun `concurrency is raised to the smallest slider position that fits`() {
        val raised = lanConcurrencyToFit(targetCount = 254, timeoutMs = 10_000, current = 2)

        assertEquals(25, raised)
        assertFalse(LanScanOperationBudget.estimate(254, 10_000, raised!!).exceedsHardCeiling)
        assertTrue(LanScanOperationBudget.estimate(254, 10_000, 10).exceedsHardCeiling)
    }

    @Test
    fun `a raised concurrency is always a slider position shared with Port Scanner and Settings`() {
        listOf(1, 3, 7, 30, 120).forEach { current ->
            listOf(254, 1_022, 4_094).forEach { count ->
                lanConcurrencyToFit(count, timeoutMs = 10_000, current = current)?.let { raised ->
                    assertTrue(raised == current || raised in PORT_CONCURRENCY_STEPS, "$current for $count: $raised")
                }
            }
        }
    }

    @Test
    fun `no concurrency fits a network too large for the limit`() {
        assertNull(lanConcurrencyToFit(targetCount = 65_534, timeoutMs = 10_000, current = 2))
    }
}
