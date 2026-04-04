package net.aieat.netswissknife.core.network.whois

import org.junit.jupiter.api.Assertions.assertEquals
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
}
