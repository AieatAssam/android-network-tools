package net.aieat.netswissknife.core.network.tls

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

class HostnameMatcherTest {
    private val dnsLeaf = TlsTestCertificates.read("valid-leaf")
    private val wildcardLeaf = TlsTestCertificates.read("wildcard-leaf")

    @Test
    fun `matches exact DNS SAN case-insensitively and with a root dot`() {
        assertTrue(HostnameMatcher.matches("WWW.Example.Com.", dnsLeaf))
        assertTrue(HostnameMatcher.matchesDnsName("www.example.com", "www.example.com."))
        assertFalse(HostnameMatcher.matches("other.example.com", dnsLeaf))
        assertFalse(HostnameMatcher.matchesDnsName("www.example.com", "www.example.com.."))
    }

    @Test
    fun `wildcard matches one leftmost label only`() {
        assertTrue(HostnameMatcher.matches("www.example.com", wildcardLeaf))
        assertFalse(HostnameMatcher.matches("example.com", wildcardLeaf))
        assertFalse(HostnameMatcher.matches("a.b.example.com", wildcardLeaf))
        assertFalse(HostnameMatcher.matches("www.example.com", TlsTestCertificates.read("self-signed")))
        assertFalse(HostnameMatcher.matchesDnsName("www.example.com", "*.com"))
        assertFalse(HostnameMatcher.matchesDnsName("www.example.com", "w*.example.com"))
    }

    @Test
    fun `CN fallback applies only when there are no DNS SAN entries`() {
        val cnOnlyLeaf = TlsTestCertificates.read("self-signed")
        assertTrue(HostnameMatcher.matches("self-signed.example.com", cnOnlyLeaf))
        assertFalse(HostnameMatcher.matches("cn-fallback.example.com", dnsLeaf))
    }

    @Test
    fun `IP identity matches only IP SAN and compares IPv6 bytes`() {
        val ipv4Leaf = TlsTestCertificates.read("ip-san-leaf")
        val ipv6Leaf = TlsTestCertificates.read("ipv6-san-leaf")

        assertTrue(HostnameMatcher.matches("1.1.1.1", ipv4Leaf))
        assertFalse(HostnameMatcher.matches("other.example.com", ipv4Leaf))
        assertFalse(HostnameMatcher.matches("1.1.1.1", dnsLeaf))
        assertTrue(HostnameMatcher.matches("2001:0db8:0:0:0:0:0:1", ipv6Leaf))
        assertFalse(HostnameMatcher.matches("2001:db8::2", ipv6Leaf))
    }

    @Test
    fun `matches byte-array IP SANs including IPv4-mapped IPv6`() {
        val ipv4San = certificateWithSans(listOf(listOf(7, byteArrayOf(1, 1, 1, 1))))
        val mappedIpv4 = byteArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -64, 0, 2, 1,
        )
        val mappedSan = certificateWithSans(listOf(listOf(7, mappedIpv4)))

        assertTrue(HostnameMatcher.matches("1.1.1.1", ipv4San))
        assertTrue(HostnameMatcher.matches("::ffff:192.0.2.1", mappedSan))
    }

    @Test
    fun `invalid hosts and scoped IP literals do not match`() {
        val ipv6Leaf = TlsTestCertificates.read("ipv6-san-leaf")
        assertFalse(HostnameMatcher.matches("not a host", dnsLeaf))
        assertFalse(HostnameMatcher.matches("fe80::1%wlan0", ipv6Leaf))
    }

    @Test
    fun `does not fall back to CN when SAN parsing fails or DNS SAN is malformed`() {
        val unreadableSan = mockk<X509Certificate>()
        every { unreadableSan.subjectAlternativeNames } throws CertificateParsingException("unreadable")
        every { unreadableSan.subjectX500Principal } returns X500Principal("CN=www.example.com")

        val malformedDnsSan = mockk<X509Certificate>()
        every { malformedDnsSan.subjectAlternativeNames } returns listOf(
            listOf(2, byteArrayOf(1, 2, 3)),
            listOf(2),
        )
        every { malformedDnsSan.subjectX500Principal } returns X500Principal("CN=www.example.com")

        assertFalse(HostnameMatcher.matches("www.example.com", unreadableSan))
        assertFalse(HostnameMatcher.matches("www.example.com", malformedDnsSan))
    }

    @Test
    fun `rejects whitespace padded certificate DNS names`() {
        val paddedExactSan = certificateWithSans(listOf(listOf(2, " www.example.com ")))
        val paddedWildcardSan = certificateWithSans(listOf(listOf(2, "*.example.com ")))
        val paddedCn = mockk<X509Certificate>()
        every { paddedCn.subjectAlternativeNames } returns null
        every { paddedCn.subjectX500Principal } returns X500Principal("CN=\\ www.example.com\\ ")

        assertFalse(HostnameMatcher.matches("www.example.com", paddedExactSan))
        assertFalse(HostnameMatcher.matches("www.example.com", paddedWildcardSan))
        assertFalse(HostnameMatcher.matches("www.example.com", paddedCn))
    }

    @Test
    fun `ignores malformed SAN entries and rejects malformed IP SAN values`() {
        val certificate = certificateWithSans(
            listOf(
                listOf("not-an-integer", "ignored.example.com"),
                listOf(7, byteArrayOf(1, 2, 3)),
                listOf(6, "ignored.example.com"),
            ),
        )
        assertFalse(HostnameMatcher.matches("1.1.1.1", certificate))
        assertFalse(HostnameMatcher.matchesDnsName("1.1.1.1", "*.1.1.1"))
        assertFalse(HostnameMatcher.matchesDnsName("www.example.com", "*.*.example.com"))
    }

    private fun certificateWithSans(sans: Collection<List<*>>): X509Certificate {
        val certificate = mockk<X509Certificate>()
        every { certificate.subjectAlternativeNames } returns sans
        return certificate
    }
}
