package net.aieat.netswissknife.core.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * TDD example tests demonstrating red-green-refactor for HostValidator.
 *
 * These tests were written BEFORE the implementation (red phase),
 * then the implementation was added to make them pass (green phase),
 * and finally both tests and implementation were reviewed (refactor phase).
 */
@DisplayName("HostValidator")
class HostValidatorTest {

    @Nested
    @DisplayName("isValidIpv4")
    inner class IsValidIpv4 {

        @ParameterizedTest(name = "{0} is a valid IPv4 address")
        @ValueSource(strings = ["192.168.1.1", "10.0.0.1", "0.0.0.0", "255.255.255.255", "8.8.8.8"])
        fun `valid IPv4 addresses are accepted`(address: String) {
            assertTrue(HostValidator.isValidIpv4(address))
        }

        @ParameterizedTest(name = "{0} is NOT a valid IPv4 address")
        @ValueSource(strings = ["256.0.0.1", "192.168.1", "not-an-ip", "", "192.168.1.1.1"])
        fun `invalid IPv4 addresses are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv4(address))
        }
    }

    @Nested
    @DisplayName("isValidHostname")
    inner class IsValidHostname {

        @ParameterizedTest(name = "{0} is a valid host")
        @ValueSource(strings = ["google.com", "example.org", "192.168.1.1", "localhost", "sub.domain.example.com"])
        fun `valid hostnames are accepted`(host: String) {
            assertTrue(HostValidator.isValidHostname(host))
        }

        @ParameterizedTest(name = "{0} is NOT a valid host")
        @ValueSource(strings = ["", "  ", "-invalid.com", "256.256.256.256"])
        fun `invalid hostnames are rejected`(host: String) {
            assertFalse(HostValidator.isValidHostname(host))
        }

        @Test
        fun `blank string is rejected`() {
            assertFalse(HostValidator.isValidHostname("   "))
        }

        @ParameterizedTest(name = "{0} is a valid IPv6 host")
        @ValueSource(strings = ["::1", "fe80::1", "2001:db8::1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334"])
        fun `valid IPv6 addresses are accepted as hostnames`(host: String) {
            assertTrue(HostValidator.isValidHostname(host))
        }

        @ParameterizedTest(name = "{0} is NOT a valid IPv6 address")
        @ValueSource(strings = [":::1", "gggg::1", "2001:db8::xyz"])
        fun `invalid IPv6 addresses are rejected`(host: String) {
            assertFalse(HostValidator.isValidHostname(host))
        }
    }

    @Nested
    @DisplayName("isValidIpv6")
    inner class IsValidIpv6 {

        @ParameterizedTest(name = "{0} is a valid IPv6 address")
        @ValueSource(strings = ["::1", "fe80::1", "2001:db8::1", "::"])
        fun `valid IPv6 addresses are accepted`(address: String) {
            assertTrue(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} is NOT a valid IPv6 address")
        @ValueSource(strings = ["192.168.1.1", "localhost", "", "gggg::1"])
        fun `non-IPv6 strings are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        // ── Pure-parser behaviour ────────────────────────────────────────────
        // isValidIpv6 used to delegate to InetAddress.getByName, which falls
        // back to a blocking DNS query for anything it cannot parse. It is now
        // a pure parse, so these cases pin its exact shape.

        @ParameterizedTest(name = "{0} is a valid IPv6 address")
        @ValueSource(
            strings = [
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                "2001:db8:85a3:0:0:8a2e:370:7334",
                "1:2:3:4:5:6:7:8",
                "1::8",
                "1::",
                "::8",
                "fe80::",
                "0:0:0:0:0:0:0:1"
            ]
        )
        fun `full and compressed forms are accepted`(address: String) {
            assertTrue(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} is a valid IPv4-mapped IPv6 address")
        @ValueSource(
            strings = [
                "::ffff:192.168.0.1",
                "::192.168.0.1",
                "64:ff9b::192.0.2.33",
                "0:0:0:0:0:ffff:192.168.0.1"
            ]
        )
        fun `IPv4-mapped forms are accepted`(address: String) {
            assertTrue(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has an invalid IPv4 tail")
        @ValueSource(strings = ["::ffff:192.168.0.256", "::ffff:192.168.0", "::ffff:1.2.3.4.5"])
        fun `malformed IPv4 tails are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @Test
        fun `an IPv4 tail is only allowed in the last group`() {
            assertFalse(HostValidator.isValidIpv6("192.168.0.1::1"))
        }

        @ParameterizedTest(name = "{0} is bracketed")
        @ValueSource(strings = ["[::1]", "[2001:db8::1]", "[::ffff:192.168.0.1]"])
        fun `bracketed literals are accepted`(address: String) {
            assertTrue(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has unbalanced or empty brackets")
        @ValueSource(strings = ["[]", "[::1", "::1]"])
        fun `malformed brackets are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} carries a zone id")
        @ValueSource(strings = ["fe80::1%eth0", "fe80::1%1", "[fe80::1%wlan0]"])
        fun `zone ids are accepted`(address: String) {
            assertTrue(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has a malformed zone id")
        @ValueSource(strings = ["fe80::1%", "%eth0"])
        fun `malformed zone ids are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has too many or too few groups")
        @ValueSource(
            strings = [
                "1:2:3:4:5:6:7",
                "1:2:3:4:5:6:7:8:9",
                "1:2:3:4:5:6:7::8",
                "1:2:3:4:5:6:7:8::"
            ]
        )
        fun `wrong group counts are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has more than one compression run")
        @ValueSource(strings = ["1::2::3", "::1::", ":::1", "1:::2"])
        fun `multiple compression runs are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has a malformed hextet")
        @ValueSource(strings = ["12345::1", "2001:db8::xyz", "1:2:3:4:5:6:7:zz", "::-1"])
        fun `malformed hextets are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @ParameterizedTest(name = "{0} has a dangling colon")
        @ValueSource(strings = [":1:2:3:4:5:6:7:8", "1:2:3:4:5:6:7:8:", ":", "1:"])
        fun `dangling colons are rejected`(address: String) {
            assertFalse(HostValidator.isValidIpv6(address))
        }

        @Test
        fun `the all-zeros address is accepted`() {
            assertTrue(HostValidator.isValidIpv6("::"))
        }

        @Test
        fun `hex digits are accepted in either case`() {
            assertTrue(HostValidator.isValidIpv6("2001:DB8:ABCD::1"))
            assertTrue(HostValidator.isValidIpv6("2001:db8:abcd::1"))
        }

        @Test
        fun `resolvable hostnames containing a colon are not IPv6`() {
            // The old getByName-backed check would have issued a DNS query here.
            assertFalse(HostValidator.isValidIpv6("example.com:80"))
            assertFalse(HostValidator.isValidIpv6("localhost:8080"))
        }
    }
}
