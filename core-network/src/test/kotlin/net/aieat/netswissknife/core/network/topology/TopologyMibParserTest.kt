package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyMibParserTest {

    @Test
    fun `hex octets and dotted addresses are parsed`() {
        assertEquals("192.168.1.1", TopologyMibParser.hexOctetsToIpv4("c0:a8:01:01"))
        assertEquals("192.168.1.1", TopologyMibParser.hexOctetsToIpv4("C0:A8:01:01"))
        assertEquals("192.168.1.1", TopologyMibParser.hexOctetsToIpv4("192.168.1.1"))
        assertNull(TopologyMibParser.hexOctetsToIpv4("aa:bb"))
        assertNull(TopologyMibParser.hexOctetsToIpv4("192.168.1.999"))
    }

    @Test
    fun `LLDP management address is decoded from the row index`() {
        val walk = mapOf(
            "1.0.8802.1.1.2.1.4.2.1.4.0.1.1.1.4.192.168.1.2" to "1",
            // Same row index, but different columns. These must not create duplicate entries.
            "1.0.8802.1.1.2.1.4.2.1.3.0.1.1.1.4.192.168.1.2" to "1",
            "1.0.8802.1.1.2.1.4.2.1.5.0.1.1.1.4.192.168.1.2" to "1"
        )

        val addresses = TopologyMibParser.parseLldpManAddrTable(walk)

        assertEquals("192.168.1.2", addresses["1.1"])
        assertEquals(1, addresses.ipv4.size)
    }

    @Test
    fun `LLDP IPv6 management addresses are retained separately`() {
        val ipv6 = (1..16).joinToString(".")
        val walk = mapOf(
            "1.0.8802.1.1.2.1.4.2.1.4.0.2.7.2.16.$ipv6" to "1"
        )

        val addresses = TopologyMibParser.parseLldpManAddrTable(walk)

        assertTrue(addresses.isEmpty())
        assertEquals("2.7", addresses.ipv6.keys.single())
    }

    @Test
    fun `LLDP remote table groups columns by local port and remote index`() {
        val entries = TopologyMibParser.parseLldpRemTable(loadFixture("lldp-rem-table.txt"))

        assertEquals(2, entries.size)
        assertEquals("switch-2", entries.first { it.key == "1.1" }.sysName)
        assertEquals("Gi1/0/1", entries.first { it.key == "1.1" }.portId)
        assertEquals("chassis-2", entries.first { it.key == "1.1" }.chassisId)
    }

    @Test
    fun `CDP cache decodes hex addresses and skips non-IP rows`() {
        val entries = TopologyMibParser.parseCdpCache(loadFixture("cdp-cache.txt"))

        assertEquals(1, entries.size)
        assertEquals("192.168.1.3", entries.single().address)
        assertEquals("switch-3", entries.single().deviceId)
        assertEquals("Gi1/0/3", entries.single().port)
        assertEquals("Cisco IOS", entries.single().platform)
        assertEquals("S", entries.single().capabilities)
    }

    @Test
    fun `fixtures contain index encoded LLDP addresses`() {
        val addresses = TopologyMibParser.parseLldpManAddrTable(loadFixture("lldp-rem-man-addr.txt"))

        assertEquals(mapOf("1.1" to "192.168.1.2", "2.1" to "192.168.1.3"), addresses.ipv4)
    }

    private fun loadFixture(name: String): Map<String, String> =
        requireNotNull(javaClass.getResourceAsStream("/snmp/$name")) { "Missing fixture $name" }
            .bufferedReader()
            .useLines { lines ->
                lines.mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                    val separator = trimmed.indexOf(" = ")
                    require(separator > 0) { "Invalid fixture line: $line" }
                    trimmed.substring(0, separator) to trimmed.substring(separator + 3)
                }.toMap()
            }
}
