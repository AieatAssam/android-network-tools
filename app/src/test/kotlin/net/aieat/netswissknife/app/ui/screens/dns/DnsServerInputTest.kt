package net.aieat.netswissknife.app.ui.screens.dns

import net.aieat.netswissknife.core.network.dns.DnsServer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DNS server field")
class DnsServerInputTest {
    @Test
    fun `a blank custom address shows no error yet but does not allow a lookup`() {
        assertFalse(customServerShowsError(DnsServer.Custom(""), ""))
        assertFalse(customServerShowsError(DnsServer.Custom(""), "   "))
        assertFalse(dnsServerAcceptsLookup(DnsServer.Custom(""), ""))
    }

    @Test
    fun `a typed custom address that is not an IP shows the error and blocks a lookup`() {
        assertTrue(customServerShowsError(DnsServer.Custom("Google"), "Google"))
        assertFalse(dnsServerAcceptsLookup(DnsServer.Custom("Google"), "Google"))
    }

    @Test
    fun `a valid custom IPv4 or IPv6 address shows no error and allows a lookup`() {
        assertFalse(customServerShowsError(DnsServer.Custom("1.0.0.1"), " 1.0.0.1 "))
        assertTrue(dnsServerAcceptsLookup(DnsServer.Custom("1.0.0.1"), " 1.0.0.1 "))
        assertTrue(dnsServerAcceptsLookup(DnsServer.Custom("2001:4860:4860::8888"), "2001:4860:4860::8888"))
    }

    @Test
    fun `a preset never shows the custom error and always allows a lookup`() {
        assertFalse(customServerShowsError(DnsServer.Google, "Google"))
        assertTrue(dnsServerAcceptsLookup(DnsServer.Google, ""))
    }
}
