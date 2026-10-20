package net.aieat.netswissknife.core.network.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalSubnetTest {
    private fun candidate(
        address: String,
        interfaceName: String,
        prefixLength: Int = 16,
    ) = SubnetUtils.InterfaceAddressCandidate(
        address = address,
        prefixLength = prefixLength,
        interfaceName = interfaceName,
        interfaceIsLoopback = false,
        interfaceIsUp = true,
        interfaceIsVirtual = false,
        addressIsLoopback = false,
    )

    @Test
    fun `deviceAddress skips tunnels like subnet detection`() {
        val source = { sequenceOf(candidate("10.1.2.3", "tun0"), candidate("10.1.77.9", "wlan0")) }

        assertEquals("10.1.77.9", LocalSubnet.deviceAddress(source))
        assertEquals("10.1.0.0/16", SubnetUtils.getCurrentSubnet(source))
    }

    @Test
    fun `hostSlice picks the slash-24 that contains the host`() {
        assertEquals("10.1.77.0/24", LocalSubnet.hostSlice("10.1.0.0/16", "10.1.77.9"))
    }

    @Test
    fun `hostSlice falls back to the first slash-24 without a host`() {
        assertEquals("10.1.0.0/24", LocalSubnet.hostSlice("10.1.0.0/16", null))
    }

    @Test
    fun `hostSlice ignores a host outside the subnet`() {
        assertEquals("10.1.0.0/24", LocalSubnet.hostSlice("10.1.0.0/16", "192.168.1.5"))
    }

    @Test
    fun `hostSlice handles prefixes broader than slash-16`() {
        assertEquals("10.12.34.0/24", LocalSubnet.hostSlice("10.0.0.0/8", "10.12.34.56"))
    }

    @Test
    fun `hostSlice returns small subnets unchanged`() {
        assertEquals("192.168.1.0/26", LocalSubnet.hostSlice("192.168.1.0/26", "192.168.1.9"))
    }

    @Test
    fun `hostSlice rejects malformed input`() {
        assertNull(LocalSubnet.hostSlice("10.1.0.0", "10.1.0.1"))
        assertNull(LocalSubnet.hostSlice("10.1.0.0/40", "10.1.0.1"))
        assertNull(LocalSubnet.hostSlice("10.1.0.300/16", "10.1.0.1"))
    }
}
