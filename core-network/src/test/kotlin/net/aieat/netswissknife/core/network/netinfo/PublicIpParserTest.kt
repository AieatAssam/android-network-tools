package net.aieat.netswissknife.core.network.netinfo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PublicIpParserTest {
    @Test
    fun `IPv4 body is accepted with surrounding whitespace`() {
        assertEquals("203.0.113.7", PublicIpParser.parse("203.0.113.7\n", IpFamily.IPV4))
    }

    @Test
    fun `IPv6 body is accepted with CRLF and spaces`() {
        assertEquals("2001:db8::1", PublicIpParser.parse(" 2001:db8::1 \r\n", IpFamily.IPV6))
    }

    @Test
    fun `address of the other family is rejected`() {
        assertNull(PublicIpParser.parse("2001:db8::1", IpFamily.IPV4))
        assertNull(PublicIpParser.parse("203.0.113.7", IpFamily.IPV6))
    }

    @Test
    fun `HTML, empty and multi-address bodies are rejected`() {
        assertNull(PublicIpParser.parse("<html>blocked</html>", IpFamily.IPV4))
        assertNull(PublicIpParser.parse("", IpFamily.IPV4))
        assertNull(PublicIpParser.parse("203.0.113.7 203.0.113.8", IpFamily.IPV4))
    }

    @Test
    fun `zone-qualified IPv6 is not a public address`() {
        assertNull(PublicIpParser.parse("fe80::1%eth0", IpFamily.IPV6))
    }
}
