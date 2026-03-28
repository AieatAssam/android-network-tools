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
    }
}
