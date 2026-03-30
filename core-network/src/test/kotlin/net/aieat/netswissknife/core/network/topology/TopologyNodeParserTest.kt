package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TopologyNodeParserTest {

    @Test
    fun `sysDescr with Cisco prefix parses vendor as Cisco`() {
        val vendor = TopologyNodeParser.parseVendor("Cisco IOS Software, Version 15.2(4)M")
        assertEquals("Cisco", vendor)
    }

    @Test
    fun `sysDescr with Juniper parses vendor as Juniper`() {
        val vendor = TopologyNodeParser.parseVendor("Juniper Networks, Inc. ex2300 Ethernet Switch, kernel JUNOS 20.4R1")
        assertEquals("Juniper", vendor)
    }

    @Test
    fun `sysDescr with MikroTik parses vendor as MikroTik`() {
        val vendor = TopologyNodeParser.parseVendor("MikroTik RouterOS 7.1 (stable)")
        assertEquals("MikroTik", vendor)
    }

    @Test
    fun `sysDescr with unknown string returns null`() {
        val vendor = TopologyNodeParser.parseVendor("Some completely unknown device string xyz")
        assertNull(vendor)
    }

    @Test
    fun `empty sysDescr returns null`() {
        val vendor = TopologyNodeParser.parseVendor("")
        assertNull(vendor)
    }

    @Test
    fun `timeticks 360000 converts to human string 1h 0m 0s`() {
        // 360000 timeticks = 3600 seconds = 1 hour
        val result = TopologyNodeParser.timeticksToHuman(360000L)
        assertTrue(result.contains("1"), "Expected hours=1 in: $result")
    }

    @Test
    fun `ifHighSpeed 1000 Mbps gives speedBps 1_000_000_000`() {
        val speed = TopologyNodeParser.ifHighSpeedToSpeedBps(1000)
        assertEquals(1_000_000_000L, speed)
    }

    @Test
    fun `ifHighSpeed 10000 Mbps gives speedBps 10_000_000_000`() {
        val speed = TopologyNodeParser.ifHighSpeedToSpeedBps(10000)
        assertEquals(10_000_000_000L, speed)
    }
}
