package net.aieat.netswissknife.core.network.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.DatagramSocket
import java.net.Socket

class LocalDestinationPolicyTest {

    @Test
    fun `IPv4 destination must be private and inside the selected subnet`() {
        val policy = LocalDestinationPolicy("192.168.1.0/24")

        assertTrue(policy.isLocal("192.168.1.7"))
        assertFalse(policy.isLocal("192.168.2.7"))
        assertFalse(policy.isLocal("8.8.8.8"))
        assertFalse(LocalDestinationPolicy("8.8.8.0/24").isLocal("8.8.8.8"))
        assertTrue(LocalDestinationPolicy("169.254.0.0/16").isLocal("169.254.1.1"))
    }

    @ParameterizedTest
    @CsvSource(
        "10.2.3.0/24,10.2.3.8",
        "172.20.4.0/24,172.20.4.8",
        "192.168.5.0/24,192.168.5.8"
    )
    fun `all RFC1918 ranges are accepted when the subnet matches`(cidr: String, host: String) {
        assertTrue(LocalDestinationPolicy(cidr).isLocal(host))
    }

    @Test
    fun `missing or invalid subnet never guesses that a private host is local`() {
        assertFalse(LocalDestinationPolicy(null).isLocal("10.0.0.7"))
        assertFalse(LocalDestinationPolicy("not-a-cidr").isLocal("10.0.0.7"))
        assertFalse(LocalDestinationPolicy("not.an.ip/24").isLocal("10.0.0.7"))
        assertFalse(LocalDestinationPolicy("192.168.1.0/not-a-prefix").isLocal("192.168.1.7"))
        assertFalse(LocalDestinationPolicy("fe80::1%wlan0/64").isLocal("fe80::1"))
        assertFalse(LocalDestinationPolicy("192.168.1.0/33").isLocal("192.168.1.7"))
        assertFalse(LocalDestinationPolicy("fe80::/129").isLocal("fe80::7"))
        assertFalse(LocalDestinationPolicy("192.168.1.0/24").isLocal("10.0.0.7"))
        assertFalse(LocalDestinationPolicy("192.168.1.0/24").isLocal("999.1.1.1"))
    }

    @Test
    fun `IPv6 link local and unique local targets are scoped to the selected prefix`() {
        val linkLocalPolicy = LocalDestinationPolicy("fe80::/64")
        assertTrue(linkLocalPolicy.isLocal("fe80::1234"))
        assertTrue(linkLocalPolicy.isLocal("fe80::1234%wlan0"))
        assertFalse(linkLocalPolicy.isLocal("fe80:0:0:1::1"))

        val uniqueLocalPolicy = LocalDestinationPolicy("fd12:3456:789a::/48")
        assertTrue(uniqueLocalPolicy.isLocal("fd12:3456:789a::42"))
        assertFalse(uniqueLocalPolicy.isLocal("fd12:3456:789b::42"))

        assertTrue(LocalDestinationPolicy("fec0::/10").isLocal("fec0::42"))
        assertFalse(LocalDestinationPolicy("fe80::/10").isLocal("fec0::42"))
        assertTrue(LocalDestinationPolicy("::ffff:192.168.1.0/120").isLocal("::ffff:192.168.1.7"))
        assertFalse(LocalDestinationPolicy("::/0").isLocal("::1"))
        assertFalse(LocalDestinationPolicy("::/0").isLocal("::ff00:1"))

        val narrowIpv4Policy = LocalDestinationPolicy("192.168.1.0/25")
        assertTrue(narrowIpv4Policy.isLocal("192.168.1.127"))
        assertFalse(narrowIpv4Policy.isLocal("192.168.1.128"))
    }

    @Test
    fun `global IPv6 is never treated as local even when it matches the subnet`() {
        assertFalse(LocalDestinationPolicy("2001:db8::/32").isLocal("2001:db8::42"))
    }

    @Test
    fun `hostnames and address-family mismatches are not resolved or treated as local`() {
        assertFalse(LocalDestinationPolicy("192.168.1.0/24").isLocal("router.local"))
        assertFalse(LocalDestinationPolicy("192.168.1.0/24").isLocal("fe80::1"))
        assertFalse(LocalDestinationPolicy("fe80::/64").isLocal("192.168.1.1"))
    }

    @Test
    fun `fake binder records TCP and UDP binds and has configurable selection`() {
        val tcpSocket = Socket()
        val udpSocket = DatagramSocket()
        try {
            val binder = FakeNetworkBinder(shouldBindResult = true)

            assertTrue(binder.shouldBind("192.168.1.7"))
            binder.bind(tcpSocket)
            binder.bind(udpSocket)

            assertEquals(listOf(tcpSocket), binder.boundTcpSockets)
            assertEquals(listOf(udpSocket), binder.boundDatagramSockets)
            assertFalse(FakeNetworkBinder(shouldBindResult = false).shouldBind("192.168.1.7"))
        } finally {
            tcpSocket.close()
            udpSocket.close()
        }
    }

    @Test
    fun `no op binder preserves default route behavior`() {
        val tcpSocket = Socket()
        val udpSocket = DatagramSocket()
        try {
            assertFalse(NoOpNetworkBinder.isAvailable)
            assertEquals(null, NoOpNetworkBinder.localSubnet())
            assertFalse(NoOpNetworkBinder.shouldBind("192.168.1.7"))
            assertEquals(null, NoOpNetworkBinder.localInterface())
            assertEquals(null, NoOpNetworkBinder.localAddress())

            NoOpNetworkBinder.bind(tcpSocket)
            NoOpNetworkBinder.bind(udpSocket)

            assertFalse(tcpSocket.isClosed)
            assertFalse(udpSocket.isClosed)
        } finally {
            tcpSocket.close()
            udpSocket.close()
        }
    }
}
