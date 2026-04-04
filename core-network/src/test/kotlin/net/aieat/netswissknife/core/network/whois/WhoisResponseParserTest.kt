package net.aieat.netswissknife.core.network.whois

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

@DisplayName("WhoisResponseParser")
class WhoisResponseParserTest {

    @Test
    @DisplayName("parseDomainName extracts value after Domain Name key")
    fun `parseDomainName extracts value after Domain Name key`() {
        val response = """
            Domain Name: EXAMPLE.COM
            Registry Domain ID: 2336799_DOMAIN_COM-VRSN
        """.trimIndent()
        assertEquals("EXAMPLE.COM", WhoisResponseParser.parseDomainName(response))
    }

    @Test
    @DisplayName("parseRegistrar extracts registrar name")
    fun `parseRegistrar extracts registrar name`() {
        val response = """
            Registrar: MarkMonitor Inc.
            Registrar IANA ID: 292
        """.trimIndent()
        assertEquals("MarkMonitor Inc.", WhoisResponseParser.parseRegistrar(response))
    }

    @Test
    @DisplayName("parseExpiryDate parses ISO-8601 date to epoch ms")
    fun `parseExpiryDate parses ISO-8601 date to epoch ms`() {
        val response = "Registry Expiry Date: 2025-08-13T04:00:00Z"
        val result = WhoisResponseParser.parseExpiresOn(response)
        assertNotNull(result)
        assertTrue(result!! > 0)
    }

    @Test
    @DisplayName("parseExpiryDate parses dd-MMM-yyyy format")
    fun `parseExpiryDate parses dd-MMM-yyyy format`() {
        val response = "Expiry Date: 13-Aug-2025"
        val result = WhoisResponseParser.parseExpiresOn(response)
        assertNotNull(result)
        val expected = LocalDate.of(2025, 8, 13).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("parseExpiryDate returns null for unparseable value")
    fun `parseExpiryDate returns null for unparseable value`() {
        val response = "Registry Expiry Date: not-a-date"
        assertNull(WhoisResponseParser.parseExpiresOn(response))
    }

    @Test
    @DisplayName("parseNameServers collects all Name Server lines deduplicated uppercase")
    fun `parseNameServers collects all Name Server lines deduplicated uppercase`() {
        val response = """
            Name Server: ns1.example.com
            Name Server: NS2.EXAMPLE.COM
            Name Server: ns1.example.com
        """.trimIndent()
        val result = WhoisResponseParser.parseNameServers(response)
        assertEquals(2, result.size)
        assertTrue(result.contains("NS1.EXAMPLE.COM"))
        assertTrue(result.contains("NS2.EXAMPLE.COM"))
    }

    @Test
    @DisplayName("parseStatusCodes strips trailing URL from status line")
    fun `parseStatusCodes strips trailing URL from status line`() {
        val response = """
            Domain Status: clientTransferProhibited https://icann.org/epp#clientTransferProhibited
            Domain Status: clientDeleteProhibited https://icann.org/epp#clientDeleteProhibited
        """.trimIndent()
        val result = WhoisResponseParser.parseStatusCodes(response)
        assertEquals(2, result.size)
        assertTrue(result.contains("clientTransferProhibited"))
        assertTrue(result.contains("clientDeleteProhibited"))
    }

    @Test
    @DisplayName("parseReferral extracts refer value")
    fun `parseReferral extracts refer value`() {
        val response = """
            domain:       COM
            refer:        whois.verisign-grs.com
        """.trimIndent()
        assertEquals("whois.verisign-grs.com", WhoisResponseParser.parseReferral(response))
    }

    @Test
    @DisplayName("parseRegistrarWhoisServer extracts hostname")
    fun `parseRegistrarWhoisServer extracts hostname`() {
        val response = """
            Registrar: MarkMonitor Inc.
            Registrar WHOIS Server: whois.markmonitor.com
        """.trimIndent()
        assertEquals("whois.markmonitor.com", WhoisResponseParser.parseRegistrarWhoisServer(response))
    }

    @Test
    @DisplayName("parseRirReferral strips whois scheme prefix")
    fun `parseRirReferral strips whois scheme prefix`() {
        val response = "ReferralServer: whois://whois.ripe.net"
        assertEquals("whois.ripe.net", WhoisResponseParser.parseRirReferral(response))
    }
}
