package net.aieat.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ReservedRanges")
class ReservedRangesTest {

    @Test
    fun `IPv4 private special use and non-global boundaries are rejected`() {
        listOf(
            "0.0.0.0", "0.255.255.255",
            "10.0.0.1", "10.255.255.254",
            "100.64.0.0", "100.127.255.255",
            "127.0.0.1", "127.255.255.255",
            "169.254.0.0", "169.254.255.255",
            "172.16.0.0", "172.31.255.255",
            "192.0.0.0", "192.0.0.8", "192.0.0.11", "192.0.0.255",
            "192.0.2.0", "192.0.2.255",
            "192.88.99.0", "192.88.99.255",
            "192.168.0.1", "192.168.255.254",
            "198.18.0.0", "198.19.255.255",
            "198.51.100.0", "198.51.100.255",
            "203.0.113.0", "203.0.113.255",
            "224.0.0.0", "239.255.255.255",
            "240.0.0.0", "255.255.255.255",
        ).forEach { address ->
            assertFalse(ReservedRanges.isPublicGlobalLiteral(address), "$address must not be GeoIP queried")
        }
    }

    @Test
    fun `globally reachable IPv4 boundary addresses and protocol exceptions are accepted`() {
        listOf(
            "1.0.0.0", "9.255.255.255",
            "100.63.255.255", "100.128.0.0",
            "126.255.255.255", "128.0.0.0",
            "169.253.255.255", "169.255.0.0",
            "172.15.255.255", "172.32.0.0",
            "192.0.1.255", "192.0.0.9", "192.0.0.10", "192.0.3.0",
            "192.88.98.255", "192.88.100.0",
            "192.167.255.255", "192.169.0.0",
            "198.17.255.255", "198.20.0.0",
            "198.51.99.255", "198.51.101.0",
            "203.0.112.255", "203.0.114.0",
            "223.255.255.255",
        ).forEach { address ->
            assertTrue(ReservedRanges.isPublicGlobalLiteral(address), "$address should remain eligible for GeoIP")
        }
    }

    @Test
    fun `IPv6 non-global ranges and boundaries are rejected`() {
        listOf(
            "::", "::1", "::2",
            "::ffff:10.0.0.1", "::ffff:100.64.0.1", "::ffff:192.0.2.1",
            "100:0:0:1::", "100:0:0:1::ffff:ffff",
            "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fe80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fec0::", "feff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "ff00::", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "100::", "100:0:0:0:ffff:ffff:ffff:ffff",
            "2001:db8::", "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff",
            "3fff::", "3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001::", "2001:0:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001:1::", "2001:2::1", "2001:4:111::1", "2001:40::1",
            "2002::1", "4000::1", "5f00::1", "64:ff9b:1::1",
            "64:ff9b::a00:1", "64:ff9b::c000:201", // NAT64 private and documentation targets
        ).forEach { address ->
            assertFalse(ReservedRanges.isPublicGlobalLiteral(address), "$address must not be GeoIP queried")
        }
    }

    @Test
    fun `public IPv6 ranges and globally reachable special block exceptions are accepted`() {
        listOf(
            "2001:4860:4860::8888",
            "2001:1::1", "2001:1::2", "2001:1::3",
            "2001:3::1",
            "2001:4:112::1",
            "2001:20::1", "2001:2f::1",
            "2001:30::1", "2001:3f::1",
            "2001:200::1",
            "3fff:1000::1",
            "64:ff9b::808:808",
            "::ffff:8.8.8.8",
            "::ffff:c000:0009", // Mapped globally reachable 192.0.0.9 exception
        ).forEach { address ->
            assertTrue(ReservedRanges.isPublicGlobalLiteral(address), "$address should be eligible for GeoIP")
        }
    }

    @Test
    fun `malformed hostnames and noncanonical literals fail closed without DNS`() {
        listOf(
            "example.com", "bad host", "8.8.8", "8.8.8.999", "256.1.1.1", "01.2.3.4",
            "2001:::1", "2001:db8::g", "fe80::1%wlan0", "1:2:3:4:5:6:7:8:9",
        ).forEach { input ->
            assertFalse(ReservedRanges.isPublicGlobalLiteral(input), "$input is not a public numeric literal")
        }
    }
}
