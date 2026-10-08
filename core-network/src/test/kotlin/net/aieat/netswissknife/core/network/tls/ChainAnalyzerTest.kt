package net.aieat.netswissknife.core.network.tls

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve
import java.util.Date
import javax.security.auth.x500.X500Principal

class ChainAnalyzerTest {
    private val now = 1_798_761_600_000L // 2027-01-01T00:00:00Z

    @Test
    fun `reports expired and not-yet-valid certificates using the injected clock`() {
        assertTrue(ChainIssue.EXPIRED in ChainAnalyzer.analyze(
            listOf(TlsTestCertificates.read("expired-leaf")), trusted = false, nowMillis = now,
        ))
        assertTrue(ChainIssue.NOT_YET_VALID in ChainAnalyzer.analyze(
            listOf(TlsTestCertificates.read("not-yet-valid-leaf")), trusted = false, nowMillis = now,
        ))
    }

    @Test
    fun `warns only when less than thirty days remain`() {
        val leaf = TlsTestCertificates.read("valid-leaf")
        val thirtyDays = 30L * 24 * 60 * 60 * 1_000
        val exactBoundary = leaf.notAfter.time - thirtyDays

        assertFalse(ChainIssue.EXPIRES_SOON in ChainAnalyzer.analyze(
            listOf(leaf), trusted = true, nowMillis = exactBoundary,
        ))
        assertTrue(ChainIssue.EXPIRES_SOON in ChainAnalyzer.analyze(
            listOf(leaf), trusted = true, nowMillis = exactBoundary + 1,
        ))
    }

    @Test
    fun `reports a cryptographically self-signed untrusted leaf`() {
        val issues = ChainAnalyzer.analyze(
            listOf(TlsTestCertificates.read("self-signed")), trusted = false, nowMillis = now,
        )
        assertTrue(ChainIssue.SELF_SIGNED in issues)
        assertTrue(ChainIssue.UNTRUSTED in issues)
    }

    @Test
    fun `does not call an untrusted lone leaf an incomplete chain`() {
        val issues = ChainAnalyzer.analyze(
            listOf(TlsTestCertificates.read("valid-leaf")), trusted = false, nowMillis = now,
        )
        assertTrue(ChainIssue.UNTRUSTED in issues)
        assertFalse(ChainIssue.INCOMPLETE_CHAIN in issues)
    }

    @Test
    fun `reports a broken link but accepts a supplied issuer`() {
        val leaf = TlsTestCertificates.read("valid-leaf")
        val root = TlsTestCertificates.read("root")
        val decoyRoot = mockk<X509Certificate>()
        every { decoyRoot.notBefore } returns Date(now - 1_000)
        every { decoyRoot.notAfter } returns Date(now + 365L * 24 * 60 * 60 * 1_000)
        every { decoyRoot.sigAlgName } returns "SHA256withRSA"
        every { decoyRoot.publicKey } returns TlsTestCertificates.read("self-signed").publicKey
        every { decoyRoot.subjectX500Principal } returns root.subjectX500Principal
        every { decoyRoot.issuerX500Principal } returns X500Principal("CN=decoy-root.test.invalid")
        val validIssues = ChainAnalyzer.analyze(listOf(leaf, root), trusted = true, nowMillis = now)
        val brokenIssues = ChainAnalyzer.analyze(
            listOf(leaf, TlsTestCertificates.read("self-signed")), trusted = false, nowMillis = now,
        )
        val invalidSignatureIssues = ChainAnalyzer.analyze(
            listOf(leaf, decoyRoot), trusted = true, nowMillis = now,
        )

        assertFalse(ChainIssue.INCOMPLETE_CHAIN in validIssues)
        assertTrue(ChainIssue.INCOMPLETE_CHAIN in brokenIssues)
        assertTrue(ChainIssue.INCOMPLETE_CHAIN in invalidSignatureIssues)
    }

    @Test
    fun `reports hostname mismatch and weak signature`() {
        val mismatch = ChainAnalyzer.analyze(
            listOf(TlsTestCertificates.read("valid-leaf")),
            trusted = true,
            nowMillis = now,
            hostnameMatches = false,
        )
        val weakSignature = ChainAnalyzer.analyze(
            listOf(certificate(sigAlgName = "SHA1withRSA")), trusted = true, nowMillis = now,
        )
        assertTrue(ChainIssue.HOSTNAME_MISMATCH in mismatch)
        assertTrue(ChainIssue.WEAK_SIGNATURE in weakSignature)
    }

    @Test
    fun `flags undersized RSA and accepts a standard P-256 EC key`() {
        val weakRsa = ChainAnalyzer.analyze(
            listOf(TlsTestCertificates.read("weak-rsa-1024-leaf")), trusted = false, nowMillis = now,
        )
        val ecKey = KeyPairGenerator.getInstance("EC").run {
            initialize(256)
            generateKeyPair().public
        }
        val ecIssues = ChainAnalyzer.analyze(
            listOf(certificate(publicKey = ecKey)), trusted = true, nowMillis = now,
        )
        assertTrue(ChainIssue.WEAK_KEY in weakRsa)
        assertFalse(ChainIssue.WEAK_KEY in ecIssues)
    }

    @Test
    fun `flags weak DSA and EC keys while ignoring unsupported key algorithms`() {
        val weakDsa = mockk<DSAPublicKey>()
        every { weakDsa.params } returns null
        val weakEc = mockk<ECPublicKey>()
        val field = ECFieldFp(BigInteger.ONE.shiftLeft(191).subtract(BigInteger.ONE))
        every { weakEc.params } returns ECParameterSpec(
            EllipticCurve(field, BigInteger.ZERO, BigInteger.ZERO),
            ECPoint(BigInteger.ZERO, BigInteger.ZERO),
            BigInteger.ONE,
            1,
        )
        val unknownKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public

        assertTrue(ChainIssue.WEAK_KEY in ChainAnalyzer.analyze(
            listOf(certificate(publicKey = weakDsa)), trusted = true, nowMillis = now,
        ))
        assertTrue(ChainIssue.WEAK_KEY in ChainAnalyzer.analyze(
            listOf(certificate(publicKey = weakEc)), trusted = true, nowMillis = now,
        ))
        assertFalse(ChainIssue.WEAK_KEY in ChainAnalyzer.analyze(
            listOf(certificate(publicKey = unknownKey)), trusted = true, nowMillis = now,
        ))
    }

    @Test
    fun `empty chain reports untrusted only when trust is false`() {
        assertEquals(emptyList<ChainIssue>(), ChainAnalyzer.analyze(emptyList(), trusted = true, nowMillis = now))
        assertEquals(listOf(ChainIssue.UNTRUSTED), ChainAnalyzer.analyze(emptyList(), trusted = false, nowMillis = now))
    }

    private fun certificate(
        sigAlgName: String = "SHA256withRSA",
        publicKey: java.security.PublicKey = TlsTestCertificates.read("valid-leaf").publicKey,
    ): X509Certificate {
        val certificate = mockk<X509Certificate>()
        every { certificate.notBefore } returns Date(now - 1_000)
        every { certificate.notAfter } returns Date(now + 365L * 24 * 60 * 60 * 1_000)
        every { certificate.sigAlgName } returns sigAlgName
        every { certificate.publicKey } returns publicKey
        every { certificate.subjectX500Principal } returns X500Principal("CN=leaf.test.invalid")
        every { certificate.issuerX500Principal } returns X500Principal("CN=issuer.test.invalid")
        return certificate
    }
}
