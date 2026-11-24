package net.aieat.netswissknife.app.ui.screens.tls

import net.aieat.netswissknife.core.network.tls.TlsCertificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TlsCertificateRoleTest {
    @Test
    fun `first certificate is the leaf`() {
        val chain = listOf(cert("leaf", selfSigned = false), cert("issuer", selfSigned = false))
        assertEquals(TlsCertificateRole.LEAF, certificateRoleOf(0, chain))
    }

    @Test
    fun `a lone certificate is the leaf even when self-signed`() {
        assertEquals(TlsCertificateRole.LEAF, certificateRoleOf(0, listOf(cert("solo", selfSigned = true))))
    }

    @Test
    fun `a served last certificate that is not self-signed is an intermediate, not a root`() {
        val chain = listOf(
            cert("leaf", selfSigned = false),
            cert("intermediate", selfSigned = false),
        )
        assertEquals(TlsCertificateRole.INTERMEDIATE, certificateRoleOf(1, chain))
    }

    @Test
    fun `a self-signed last certificate is the root`() {
        val chain = listOf(
            cert("leaf", selfSigned = false),
            cert("intermediate", selfSigned = false),
            cert("root", selfSigned = true),
        )
        assertEquals(TlsCertificateRole.ROOT, certificateRoleOf(2, chain))
        assertEquals(TlsCertificateRole.INTERMEDIATE, certificateRoleOf(1, chain))
    }

    private fun cert(name: String, selfSigned: Boolean) = TlsCertificate(
        subjectCN = name,
        subjectOrg = null,
        issuerCN = if (selfSigned) name else "issuer",
        issuerOrg = null,
        notBefore = 0L,
        notAfter = 1L,
        isExpired = false,
        isSelfSigned = selfSigned,
        sans = emptyList(),
        serialNumber = "01",
        signatureAlgorithm = "SHA256withRSA",
        publicKeyAlgorithm = "RSA",
        publicKeyBits = 2048,
        sha256Fingerprint = "AA",
    )
}
