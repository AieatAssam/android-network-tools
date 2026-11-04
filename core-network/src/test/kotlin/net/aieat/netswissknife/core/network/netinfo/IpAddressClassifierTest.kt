package net.aieat.netswissknife.core.network.netinfo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class IpAddressClassifierTest {
    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "10.1.2.3, PRIVATE",
        "172.16.0.1, PRIVATE",
        "172.31.255.254, PRIVATE",
        "172.32.0.1, GLOBAL",
        "192.168.1.10, PRIVATE",
        "100.64.0.1, SHARED",
        "100.127.255.255, SHARED",
        "100.128.0.1, GLOBAL",
        "127.0.0.1, LOOPBACK",
        "169.254.10.20, LINK_LOCAL",
        "0.0.0.0, UNSPECIFIED",
        "224.0.0.251, MULTICAST",
        "240.0.0.1, OTHER",
        "255.255.255.255, OTHER",
        "8.8.8.8, GLOBAL",
        "::1, LOOPBACK",
        "::, UNSPECIFIED",
        "fe80::1%wlan0, LINK_LOCAL",
        "febf::1, LINK_LOCAL",
        "fd12:3456::1, UNIQUE_LOCAL",
        "fc00::1, UNIQUE_LOCAL",
        "2001:db8::1, GLOBAL",
        "2606:4700:4700::1111, GLOBAL",
        "3fff::1, GLOBAL",
        "4000::1, OTHER",
        "ff02::1, MULTICAST",
        "::ffff:192.0.2.1, OTHER",
    )
    fun `classifies literal address scope`(
        address: String,
        expected: IpAddressScope,
    ) {
        assertEquals(expected, IpAddressClassifier.scopeOf(address))
    }

    @ParameterizedTest(name = "\"{0}\" is not a literal address")
    @ValueSource(
        strings = [
            "example.com",
            "256.1.1.1",
            "1.2.3",
            "01.2.3.4",
            "1.2.3.4.5",
            "1.2.3.4%wlan0",
            "",
            "fe80::1::2",
            "gggg::1",
            "2001:db8:1:2:3:4:5:6:7",
            "fe80::%",
        ],
    )
    fun `text that is not a literal has no scope`(text: String) {
        assertNull(IpAddressClassifier.scopeOf(text))
        assertNull(IpAddressClassifier.familyOf(text))
    }

    @Test
    fun `family follows the literal shape`() {
        assertEquals(IpFamily.IPV4, IpAddressClassifier.familyOf("192.168.0.1"))
        assertEquals(IpFamily.IPV6, IpAddressClassifier.familyOf("fe80::1%wlan0"))
    }

    @Test
    fun `display form drops the zone id`() {
        assertEquals("fe80::1", IpAddressClassifier.displayAddress("fe80::1%wlan0"))
        assertEquals("10.0.0.1", IpAddressClassifier.displayAddress("10.0.0.1"))
    }

    @ParameterizedTest(name = "/{0} is {1}")
    @CsvSource("0, 0.0.0.0", "8, 255.0.0.0", "24, 255.255.255.0", "31, 255.255.255.254", "32, 255.255.255.255")
    fun `subnet mask is derived from the prefix length`(
        prefix: Int,
        expected: String,
    ) {
        assertEquals(expected, IpAddressClassifier.ipv4MaskFromPrefix(prefix))
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 33])
    fun `out of range prefix has no mask`(prefix: Int) {
        assertNull(IpAddressClassifier.ipv4MaskFromPrefix(prefix))
    }

    @Test
    fun `local address exposes a mask only for IPv4`() {
        val v4 = LocalAddress("192.168.1.20", IpFamily.IPV4, 24, IpAddressScope.PRIVATE)
        val v6 = LocalAddress("fd00::20", IpFamily.IPV6, 64, IpAddressScope.UNIQUE_LOCAL)
        assertEquals("255.255.255.0", v4.subnetMask)
        assertNull(v6.subnetMask)
    }
}
