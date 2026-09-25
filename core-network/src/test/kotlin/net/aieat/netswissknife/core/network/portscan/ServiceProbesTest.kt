package net.aieat.netswissknife.core.network.portscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceProbesTest {
    @Test
    fun `web ports receive bounded HEAD request with host header`() {
        val request = ServiceProbes.request(80, "example.com")!!.toString(Charsets.US_ASCII)

        assertEquals(ProbeKind.HTTP, ServiceProbes.kindFor(8080))
        assertTrue(request.startsWith("HEAD / HTTP/1.0\r\n"))
        assertTrue(request.contains("Host: example.com\r\n"))
        assertTrue(request.endsWith("\r\n\r\n"))
    }

    @Test
    fun `HTTP Host authority brackets IPv6 and includes a nondefault scanned port`() {
        val ipv6 = ServiceProbes.request(8080, "2001:db8::10")!!.toString(Charsets.US_ASCII)
        val defaultPort = ServiceProbes.request(80, "example.com")!!.toString(Charsets.US_ASCII)

        assertTrue(ipv6.contains("Host: [2001:db8::10]:8080\r\n"))
        assertTrue(defaultPort.contains("Host: example.com\r\n"))
        assertTrue(!defaultPort.contains("example.com:80"))
    }

    @Test
    fun `mail services send EHLO after passive greeting and legacy greeting ports stay passive`() {
        assertEquals(ProbeKind.SMTP, ServiceProbes.kindFor(25))
        assertEquals("EHLO netswissknife\r\n", ServiceProbes.request(587, "host")!!.toString(Charsets.US_ASCII))
        assertEquals(ProbeKind.FTP, ServiceProbes.kindFor(21))
        assertEquals(ProbeKind.SSH, ServiceProbes.kindFor(22))
        assertEquals(ProbeKind.POP3, ServiceProbes.kindFor(110))
        assertEquals(ProbeKind.IMAP, ServiceProbes.kindFor(143))
        assertNull(ServiceProbes.request(22, "host"))
    }

    @Test
    fun `known TLS ports use TLS peek without an application greeting`() {
        listOf(443, 8443, 993, 995, 465, 636).forEach { port ->
            assertEquals(ProbeKind.TLS_PEEK, ServiceProbes.kindFor(port))
            assertNull(ServiceProbes.request(port, "host"))
        }
    }
}
