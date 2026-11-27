package net.aieat.netswissknife.core.network.whois

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhoisNotRegisteredTest {
    @Test
    fun `an IANA answer with no objects and no parsed domain name means the name is not registered`() {
        assertTrue(isUnregisteredDomain(listOf("This query returned 0 objects."), domainName = null))
    }

    @Test
    fun `a registry no-match answer means the name is not registered`() {
        assertTrue(isUnregisteredDomain(listOf("No match for \"NOSUCH-ZZ.EXAMPLE\"."), domainName = null))
    }

    @Test
    fun `a parsed domain name means the name is registered even if a marker appears elsewhere`() {
        assertFalse(isUnregisteredDomain(listOf("Domain Name: EXAMPLE.COM", "not found in cache"), "EXAMPLE.COM"))
    }

    @Test
    fun `an answer without a no-record marker is not treated as unregistered`() {
        assertFalse(isUnregisteredDomain(listOf("refer: whois.verisign-grs.com"), domainName = null))
        assertFalse(isUnregisteredDomain(emptyList(), domainName = null))
    }
}
