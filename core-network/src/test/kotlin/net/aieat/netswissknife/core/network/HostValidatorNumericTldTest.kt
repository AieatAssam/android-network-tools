package net.aieat.netswissknife.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HostValidator numeric-only names")
class HostValidatorNumericTldTest {
    @Test
    fun `a concatenated dotted address is not a host`() {
        // Seen on device: two IPv4 addresses typed one after another. Every label is digits, so
        // the old hostname regex accepted it and Ping resolved it as a name.
        assertNull(HostValidator.normalize("10.109.208.25010.109.208.200"))
        assertFalse(HostValidator.isValidHostname("10.109.208.25010.109.208.200"))
        assertEquals(HostProblem.IPV4_INVALID, HostValidator.problemOf("10.109.208.25010.109.208.200"))
    }

    @Test
    fun `dotted all-numeric names with the wrong number of parts are rejected`() {
        assertNull(HostValidator.normalize("1.2.3.4.5"))
        assertNull(HostValidator.normalize("1.2.3"))
        assertNull(HostValidator.normalize("123"))
    }

    @Test
    fun `a numeric label inside a name with a letter TLD is still a valid host`() {
        assertEquals("host1.123.example.com", HostValidator.normalize("host1.123.example.com"))
        assertEquals("a1.b2.example", HostValidator.normalize("a1.b2.example"))
    }

    @Test
    fun `valid IPv4 literals are unaffected`() {
        assertEquals("10.109.208.250", HostValidator.normalize("10.109.208.250"))
        assertEquals("192.0.2.1", HostValidator.normalize("192.0.2.1."))
    }
}
