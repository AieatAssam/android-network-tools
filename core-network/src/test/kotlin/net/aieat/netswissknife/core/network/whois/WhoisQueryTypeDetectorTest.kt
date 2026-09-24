package net.aieat.netswissknife.core.network.whois

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WhoisQueryTypeDetector")
class WhoisQueryTypeDetectorTest {

    @Test
    @DisplayName("detectType returns DOMAIN for plain hostname")
    fun `detectType returns DOMAIN for plain hostname`() {
        assertEquals(WhoisQueryType.DOMAIN, WhoisQueryTypeDetector.detect("example.com"))
    }

    @Test
    @DisplayName("detectType returns DOMAIN for subdomain")
    fun `detectType returns DOMAIN for subdomain`() {
        assertEquals(WhoisQueryType.DOMAIN, WhoisQueryTypeDetector.detect("sub.example.co.uk"))
    }

    @Test
    @DisplayName("detectType returns IPV4 for dotted-quad")
    fun `detectType returns IPV4 for dotted-quad`() {
        assertEquals(WhoisQueryType.IPV4, WhoisQueryTypeDetector.detect("8.8.8.8"))
    }

    @Test
    @DisplayName("detectType returns IPV6 for colon-notation address")
    fun `detectType returns IPV6 for colon-notation address`() {
        assertEquals(WhoisQueryType.IPV6, WhoisQueryTypeDetector.detect("2001:4860:4860::8888"))
    }

    @Test
    @DisplayName("detectType returns ASN for AS12345")
    fun `detectType returns ASN for AS12345`() {
        assertEquals(WhoisQueryType.ASN, WhoisQueryTypeDetector.detect("AS12345"))
    }

    @Test
    @DisplayName("detectType returns ASN for lowercase as99")
    fun `detectType returns ASN for lowercase as99`() {
        assertEquals(WhoisQueryType.ASN, WhoisQueryTypeDetector.detect("as99"))
    }

    @Test
    @DisplayName("detectType returns DOMAIN for domain with numeric labels")
    fun `detectType returns DOMAIN for domain with numeric labels`() {
        assertEquals(WhoisQueryType.DOMAIN, WhoisQueryTypeDetector.detect("123abc.io"))
    }

    @Test
    @DisplayName("normalize trims surrounding ordinary spaces and removes the DNS root dot")
    fun `normalize trims surrounding spaces and removes root dot`() {
        val normalized = WhoisQueryTypeDetector.normalize(" Example.COM. ")
        assertEquals("example.com", normalized?.value)
        assertEquals(WhoisQueryType.DOMAIN, normalized?.type)
    }

    @Test
    @DisplayName("invalid whitespace, controls, and numeric IPv4-shaped input are rejected")
    fun `invalid whitespace controls and IPv4 are rejected`() {
        assertNull(WhoisQueryTypeDetector.normalize("foo bar"))
        assertNull(WhoisQueryTypeDetector.normalize("a\r\nb"))
        assertNull(WhoisQueryTypeDetector.normalize("999.1.1.1"))
        assertNull(WhoisQueryTypeDetector.normalize("1.2.3"))
        assertNull(WhoisQueryTypeDetector.normalize("1.2.3.4.5"))
    }

    @Test
    @DisplayName("equivalent IPv6 spellings have the same canonical lookup key")
    fun `equivalent IPv6 spellings have the same canonical lookup key`() {
        val compressed = WhoisQueryTypeDetector.normalize("2001:db8::1")
        val expanded = WhoisQueryTypeDetector.normalize("2001:0db8:0:0:0:0:0:1")

        assertEquals("2001:db8::1", compressed?.value)
        assertEquals(compressed, expanded)
    }

    @Test
    @DisplayName("IDNA Unicode root separators normalize to an ASCII dot-free domain")
    fun `IDNA Unicode root separators normalize to ASCII root dot free domain`() {
        listOf('\u3002', '\uff0e', '\uff61').forEach { separator ->
            assertEquals("example.com", WhoisQueryTypeDetector.normalize("example.com$separator")?.value)
        }
    }
}
