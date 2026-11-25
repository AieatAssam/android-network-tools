package net.aieat.netswissknife.app.ui.screens.mdns

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdnsDiscoveryFormattingTest {
    private fun service(
        name: String,
        type: String = "_http._tcp",
        hostname: String = "host.local",
        ips: List<String> = listOf("192.168.1.10"),
        txt: Map<String, String> = emptyMap(),
    ) = DiscoveredService(
        serviceType = type,
        instanceName = "$name.$type.local",
        displayName = name,
        hostname = hostname,
        port = 80,
        ipAddresses = ips,
        txtRecords = txt,
    )

    @Test
    fun `known service types map to their own label, case-insensitively`() {
        assertEquals(R.string.mdns_type_http, mdnsServiceTypeInfo("_HTTP._tcp").labelRes)
        assertEquals(R.string.mdns_type_ssh, mdnsServiceTypeInfo("_ssh._tcp").labelRes)
        assertTrue(mdnsServiceTypeInfo("_ssh._tcp").known)
    }

    @Test
    fun `printer protocols share the printer label`() {
        assertEquals(R.string.mdns_type_printer, mdnsServiceTypeInfo("_ipp._tcp").labelRes)
        assertEquals(R.string.mdns_type_printer, mdnsServiceTypeInfo("_ipps._tcp").labelRes)
        assertEquals(R.string.mdns_type_printer, mdnsServiceTypeInfo("_printer._tcp").labelRes)
    }

    @Test
    fun `airplay and raop both show as AirPlay`() {
        assertEquals(R.string.mdns_type_airplay, mdnsServiceTypeInfo("_airplay._tcp").labelRes)
        assertEquals(R.string.mdns_type_airplay, mdnsServiceTypeInfo("_raop._tcp").labelRes)
    }

    @Test
    fun `unknown service type falls back to the generic label`() {
        val info = mdnsServiceTypeInfo("_made-up._udp")
        assertEquals(R.string.mdns_type_generic, info.labelRes)
        assertFalse(info.known)
    }

    @Test
    fun `device grouping keeps every service of one hostname together`() {
        val printerHttp = service("Printer web", type = "_http._tcp", hostname = "printer.local.")
        val printerIpp = service("Printer", type = "_ipp._tcp", hostname = "printer.local")
        val tv = service("Living Room", type = "_airplay._tcp", hostname = "tv.local")

        val groups = groupMdnsServicesByDevice(listOf(printerHttp, tv, printerIpp))

        assertEquals(listOf("printer.local", "tv.local"), groups.keys.toList())
        assertEquals(2, groups.getValue("printer.local").size)
    }

    @Test
    fun `device without hostname is keyed by its first address, then by instance name`() {
        val byAddress = service("Nas", hostname = "", ips = listOf("10.0.0.5", "10.0.0.6"))
        val byName = service("Lonely", hostname = "  ", ips = emptyList())

        assertEquals("10.0.0.5", deviceKey(byAddress))
        assertEquals(byName.instanceName, deviceKey(byName))
    }

    @Test
    fun `device groups are sorted case-insensitively`() {
        val groups =
            groupMdnsServicesByDevice(
                listOf(
                    service("b", hostname = "beta.local"),
                    service("a", hostname = "Alpha.local"),
                ),
            )
        assertEquals(listOf("Alpha.local", "beta.local"), groups.keys.toList())
    }

    @Test
    fun `TXT entries are sorted and capped until show all is requested`() {
        val txt = (1..8).associate { "k$it" to "v$it" }

        val collapsed = visibleTxtEntries(txt, showAll = false)
        val expanded = visibleTxtEntries(txt, showAll = true)

        assertEquals(MDNS_TXT_VISIBLE_LIMIT, collapsed.size)
        assertEquals("k1", collapsed.first().first)
        assertEquals(8, expanded.size)
    }

    @Test
    fun `small TXT maps are shown whole`() {
        val txt = mapOf("path" to "/", "note" to "x")
        assertEquals(2, visibleTxtEntries(txt, showAll = false).size)
    }

    @Test
    fun `elapsed seconds round down and never go negative`() {
        assertEquals(0L, mdnsElapsedSeconds(-500L))
        assertEquals(0L, mdnsElapsedSeconds(999L))
        assertEquals(3L, mdnsElapsedSeconds(3_750L))
    }

    @Test
    fun `scan progress is a clamped fraction of the window`() {
        assertEquals(0f, mdnsScanProgress(-1L, 8_000L))
        assertEquals(0.5f, mdnsScanProgress(4_000L, 8_000L))
        assertEquals(1f, mdnsScanProgress(12_000L, 8_000L))
        assertEquals(0f, mdnsScanProgress(4_000L, 0L))
    }

    @Test
    fun `unscoped link-local IPv6 addresses are recognised across the fe80 to febf range`() {
        assertTrue(isUnscopedLinkLocalIpv6("fe80::f47e:c4ce:5d9:d27a"))
        assertTrue(isUnscopedLinkLocalIpv6("FE80::1"))
        assertTrue(isUnscopedLinkLocalIpv6("febf::1"))
        assertTrue(isUnscopedLinkLocalIpv6("fe80:0:0:0:1:2:3:4"))
    }

    @Test
    fun `scoped, global, IPv4 and near-miss addresses are not treated as unscoped link-local`() {
        assertFalse(isUnscopedLinkLocalIpv6("fe80::1%wlan0"))
        assertFalse(isUnscopedLinkLocalIpv6("fec0::1"))
        assertFalse(isUnscopedLinkLocalIpv6("fd00::1"))
        assertFalse(isUnscopedLinkLocalIpv6("2001:db8::1"))
        assertFalse(isUnscopedLinkLocalIpv6("::1"))
        assertFalse(isUnscopedLinkLocalIpv6("169.254.10.20"))
        assertFalse(isUnscopedLinkLocalIpv6("0fe80::1"))
    }
}
