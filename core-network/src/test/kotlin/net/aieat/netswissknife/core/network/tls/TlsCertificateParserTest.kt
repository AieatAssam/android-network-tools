package net.aieat.netswissknife.core.network.tls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
