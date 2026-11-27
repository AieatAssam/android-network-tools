package net.aieat.netswissknife.app.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WifiConnectionInfoMapperTest {
    @Test
    fun `an IPv4 gateway is chosen over an IPv6 gateway regardless of route order`() {
        val ipv6First = WifiConnectionInfoMapper.selectGateway(listOf("fe80::b696:91ff:fe3c:3ef6", "10.109.208.1"))
        val ipv4First = WifiConnectionInfoMapper.selectGateway(listOf("10.109.208.1", "fe80::b696:91ff:fe3c:3ef6"))

        assertEquals("10.109.208.1", ipv6First)
        assertEquals("10.109.208.1", ipv4First)
    }

    @Test
    fun `several IPv4 gateways resolve to the same value in any order`() {
        val forward = WifiConnectionInfoMapper.selectGateway(listOf("192.168.1.1", "10.0.0.1"))
        val reversed = WifiConnectionInfoMapper.selectGateway(listOf("10.0.0.1", "192.168.1.1"))

        assertEquals("10.0.0.1", forward)
        assertEquals(forward, reversed)
    }

    @Test
    fun `an IPv6 gateway is used when no IPv4 gateway exists`() {
        val gateway = WifiConnectionInfoMapper.selectGateway(listOf("fe80::1"))

        assertEquals("fe80::1", gateway)
    }

    @Test
    fun `blank candidates and an empty list give no gateway`() {
        assertNull(WifiConnectionInfoMapper.selectGateway(emptyList()))
        assertNull(WifiConnectionInfoMapper.selectGateway(listOf("", "  ")))
    }

    @Test
    fun `surrounding whitespace is removed before choosing`() {
        val gateway = WifiConnectionInfoMapper.selectGateway(listOf(" fe80::1 ", " 10.0.0.1 "))

        assertEquals("10.0.0.1", gateway)
    }
}
