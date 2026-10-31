package net.aieat.netswissknife.core.network.dns

import net.aieat.netswissknife.core.network.HostProblem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DnsNameValidator.problemOf")
class DnsNameValidatorTest {
    @Test
    fun `valid names have no problem`() {
        assertNull(DnsNameValidator.problemOf("example.com", DnsRecordType.A))
        assertNull(DnsNameValidator.problemOf("bücher.de", DnsRecordType.MX))
        assertNull(DnsNameValidator.problemOf("8.8.8.8", DnsRecordType.PTR))
    }

    @Test
    fun `SRV and TXT owner labels with a leading underscore are valid`() {
        assertNull(DnsNameValidator.problemOf("_dmarc.example.com", DnsRecordType.TXT))
        assertNull(DnsNameValidator.problemOf("_sip._tcp.example.com", DnsRecordType.SRV))
    }

    @Test
    fun `blank input is reported as blank`() {
        assertEquals(HostProblem.BLANK, DnsNameValidator.problemOf("   ", DnsRecordType.A))
    }

    @Test
    fun `embedded whitespace is reported as whitespace`() {
        assertEquals(HostProblem.CONTAINS_WHITESPACE, DnsNameValidator.problemOf("bad host!!", DnsRecordType.A))
    }

    @Test
    fun `empty label is reported as an empty label`() {
        assertEquals(HostProblem.EMPTY_LABEL, DnsNameValidator.problemOf("a..b", DnsRecordType.A))
    }

    @Test
    fun `ASCII punctuation is reported as invalid characters`() {
        assertEquals(HostProblem.INVALID_CHARACTERS, DnsNameValidator.problemOf("exa!mple.com", DnsRecordType.A))
    }

    @Test
    fun `out of range IPv4 is rejected for PTR lookups`() {
        assertEquals(HostProblem.IPV4_INVALID, DnsNameValidator.problemOf("999.1.1.1", DnsRecordType.PTR))
    }

    @Test
    fun `malformed IPv6 is rejected for PTR lookups`() {
        assertEquals(HostProblem.IPV6_INVALID, DnsNameValidator.problemOf("2001:db8::xyz", DnsRecordType.PTR))
    }

    @Test
    fun `IPv6 literal is rejected for non-PTR lookups`() {
        assertEquals(HostProblem.INVALID, DnsNameValidator.problemOf("2001:db8::1", DnsRecordType.A))
    }
}
