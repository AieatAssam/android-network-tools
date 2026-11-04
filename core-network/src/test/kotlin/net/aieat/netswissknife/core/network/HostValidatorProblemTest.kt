package net.aieat.netswissknife.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HostValidator.problemOf")
class HostValidatorProblemTest {
    @Test
    fun `valid input has no problem`() {
        assertNull(HostValidator.problemOf("example.com"))
        assertNull(HostValidator.problemOf("example.com."))
        assertNull(HostValidator.problemOf("192.0.2.1"))
    }

    @Test
    fun `blank input is reported as blank`() {
        assertEquals(HostProblem.BLANK, HostValidator.problemOf(""))
        assertEquals(HostProblem.BLANK, HostValidator.problemOf("   "))
    }

    @Test
    fun `embedded whitespace is reported as whitespace`() {
        assertEquals(HostProblem.CONTAINS_WHITESPACE, HostValidator.problemOf("bad host!!"))
    }

    @Test
    fun `out of range IPv4 is reported as an IPv4 problem`() {
        assertEquals(HostProblem.IPV4_INVALID, HostValidator.problemOf("999.999.999.999"))
    }

    @Test
    fun `empty label between dots is reported as an empty label`() {
        assertEquals(HostProblem.EMPTY_LABEL, HostValidator.problemOf("a..b"))
    }

    @Test
    fun `label starting with a hyphen or longer than 63 characters is an invalid label`() {
        assertEquals(HostProblem.INVALID_LABEL, HostValidator.problemOf("-bad.example"))
        assertEquals(HostProblem.INVALID_LABEL, HostValidator.problemOf("a".repeat(64) + ".com"))
    }

    @Test
    fun `ASCII punctuation other than hyphen and dot is an invalid character`() {
        assertEquals(HostProblem.INVALID_CHARACTERS, HostValidator.problemOf("exa_mple.com"))
    }

    @Test
    fun `malformed IPv6 is reported as an IPv6 problem`() {
        assertEquals(HostProblem.IPV6_INVALID, HostValidator.problemOf("1:2:3"))
    }

    @Test
    fun `input longer than 253 characters is too long`() {
        assertEquals(HostProblem.TOO_LONG, HostValidator.problemOf(("a".repeat(63) + ".").repeat(4)))
    }
}
