package net.aieat.netswissknife.core.network.tls

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

class TlsCertificateParserTest {

    // ── isExpired ──────────────────────────────────────────────────────────────

    @Test
    fun `isExpired returns true when notAfter is in the past`() {
        val pastMs = System.currentTimeMillis() - 1_000
        assertTrue(TlsCertificateParser.isExpired(pastMs))
    }

    @Test
    fun `isExpired returns false when notAfter is in the future`() {
        val futureMs = System.currentTimeMillis() + 1_000_000
        assertFalse(TlsCertificateParser.isExpired(futureMs))
    }

    // ── isSelfSigned ───────────────────────────────────────────────────────────

    @Test
    fun `isSelfSigned is true when subject equals issuer`() {
        val dn = "CN=Root CA,O=Example,C=US"
        assertTrue(TlsCertificateParser.isSelfSigned(dn, dn))
    }

    @Test
    fun `isSelfSigned is false when subject differs from issuer`() {
        val subject = "CN=leaf.example.com,O=Example"
        val issuer  = "CN=Intermediate CA,O=Example"
        assertFalse(TlsCertificateParser.isSelfSigned(subject, issuer))
    }

    // ── sha256Fingerprint ─────────────────────────────────────────────────────

    @Test
    fun `sha256Fingerprint produces colon-separated uppercase hex`() {
        val bytes = ByteArray(32) { it.toByte() }
        val fp = TlsCertificateParser.sha256Fingerprint(bytes)
        // Must match pattern of uppercase hex pairs separated by colons
        assertTrue(fp.matches(Regex("([0-9A-F]{2}:)*[0-9A-F]{2}")),
            "Fingerprint '$fp' does not match colon-separated uppercase hex pattern")
    }

    @Test
    fun `sha256Fingerprint produces 95-char string for any cert bytes`() {
        // SHA-256 always produces 32 bytes → 64 hex chars + 31 colons = 95 chars
        val bytes = ByteArray(256) { it.toByte() }
        val fp = TlsCertificateParser.sha256Fingerprint(bytes)
        assertEquals(95, fp.length,
            "Expected 95 chars (32 hex pairs + 31 colons), got ${fp.length}")
    }

    // ── parseCN ───────────────────────────────────────────────────────────────

    @Test
    fun `parseCN extracts CN from distinguished name`() {
        val dn = "CN=example.com,O=Example Inc,C=US"
        assertEquals("example.com", TlsCertificateParser.parseCN(dn))
    }

    @Test
    fun `parseCN returns empty string when CN is absent`() {
        val dn = "O=Example Inc,C=US"
        assertEquals("", TlsCertificateParser.parseCN(dn))
    }

    @Test
    fun `parseCN handles wildcard CN`() {
        val dn = "CN=*.example.com,O=Example"
        assertEquals("*.example.com", TlsCertificateParser.parseCN(dn))
    }

    @Test
    fun `parseCN handles CN with spaces`() {
        val dn = "CN=Let's Encrypt Authority X3,O=Let's Encrypt,C=US"
        assertEquals("Let's Encrypt Authority X3", TlsCertificateParser.parseCN(dn))
    }

    // ── parseOrg ──────────────────────────────────────────────────────────────

    @Test
    fun `parseOrg extracts O from distinguished name`() {
        val dn = "CN=example.com,O=Example Inc,C=US"
        assertEquals("Example Inc", TlsCertificateParser.parseOrg(dn))
    }

    @Test
    fun `parseOrg returns null when O is absent`() {
        val dn = "CN=example.com,C=US"
        assertNull(TlsCertificateParser.parseOrg(dn))
    }

    @Test
    fun `parseOrg handles O with spaces and punctuation`() {
        val dn = "CN=example.com,O=DigiCert Inc,C=US"
        assertEquals("DigiCert Inc", TlsCertificateParser.parseOrg(dn))
    }

    @Test
    fun `parseCN preserves escaped commas inside a value`() {
        assertEquals("Last, First", TlsCertificateParser.parseCN("CN=Last\\, First,O=Example,C=US"))
    }

    @Test
    fun `parseCN decodes escaped UTF-8 octets`() {
        assertEquals("é.example.com", TlsCertificateParser.parseCN("CN=\\C3\\A9.example.com,O=Example"))
    }

    @Test
    fun `parseCN handles quoted commas trailing escapes and malformed components`() {
        assertEquals("Last, First", TlsCertificateParser.parseCN("CN=\"Last, First\",O=Example"))
        assertEquals("tail\\", TlsCertificateParser.parseCN("CN=tail\\"))
        assertEquals("example.com", TlsCertificateParser.parseCN("BROKEN,CN=example.com"))
        assertEquals("", TlsCertificateParser.parseCN("CN="))
    }

    @Test
    fun `parse renders an IP address SAN as a readable literal`() {
        val parsed = TlsCertificateParser.parse(TlsTestCertificates.read("ip-san-leaf"))
        assertTrue("IP:1.1.1.1" in parsed.sans)
    }

    @Test
    fun `parse handles byte-array IP SANs and skips malformed SAN entries`() {
        val parsed = TlsCertificateParser.parse(certificate(
            sans = listOf(
                listOf(7, byteArrayOf(1, 1, 1, 1)),
                listOf(2, "www.example.com"),
                listOf(8, "ignored"),
                emptyList<Any>(),
                listOf(2),
                listOf(7, byteArrayOf(1, 2, 3)),
                listOf(2, 123),
                listOf("not-an-integer", "ignored"),
                listOf(7, 123),
            ),
        ))

        assertEquals(listOf("IP:1.1.1.1", "DNS:www.example.com"), parsed.sans)
    }

    @Test
    fun `parse tolerates unavailable SANs and reports EC key size`() {
        val unavailableSans = certificate(sans = null)
        val parsedWithoutSans = TlsCertificateParser.parse(unavailableSans)
        every { unavailableSans.subjectAlternativeNames } throws CertificateParsingException("malformed")
        val parsedWithMalformedExtension = TlsCertificateParser.parse(unavailableSans)
        val ecPublicKey = KeyPairGenerator.getInstance("EC").run {
            initialize(256)
            generateKeyPair().public
        }
        val parsedEc = TlsCertificateParser.parse(certificate(publicKey = ecPublicKey))
        val edPublicKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public
        val parsedUnsupportedKey = TlsCertificateParser.parse(certificate(publicKey = edPublicKey))

        assertEquals(emptyList<String>(), parsedWithoutSans.sans)
        assertEquals(emptyList<String>(), parsedWithMalformedExtension.sans)
        assertEquals(256, parsedEc.publicKeyBits)
        assertEquals(0, parsedUnsupportedKey.publicKeyBits)
    }

    private fun certificate(
        sans: Collection<List<*>>? = emptyList(),
        publicKey: PublicKey = TlsTestCertificates.read("valid-leaf").publicKey,
    ): X509Certificate {
        val certificate = mockk<X509Certificate>()
        every { certificate.subjectX500Principal } returns X500Principal("CN=www.example.com,O=Example")
        every { certificate.issuerX500Principal } returns X500Principal("CN=Example Root,O=Example")
        every { certificate.notBefore } returns Date(0)
        every { certificate.notAfter } returns Date(Long.MAX_VALUE)
        every { certificate.subjectAlternativeNames } returns sans
        every { certificate.publicKey } returns publicKey
        every { certificate.serialNumber } returns BigInteger.ONE
        every { certificate.sigAlgName } returns "SHA256withRSA"
        every { certificate.encoded } returns byteArrayOf(1, 2, 3)
        return certificate
    }
}
