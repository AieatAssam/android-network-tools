package net.aieat.netswissknife.core.network.tls

import java.security.interfaces.DSAKey
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import java.security.cert.X509Certificate
import java.util.Date

/** Certificate diagnostics that do not open sockets or consult ambient trust stores. */
object ChainAnalyzer {
    private const val EXPIRES_SOON_MILLIS = 30L * 24 * 60 * 60 * 1_000
    private const val MIN_RSA_BITS = 2_048
    private const val MIN_DSA_BITS = 2_048
    private const val MIN_EC_BITS = 224

    /**
     * Analyzes an ordered peer chain (leaf first) using the supplied trust result and clock.
     * A single untrusted leaf with a different issuer is not enough to prove an intermediate was
     * omitted: roots are normally omitted from the wire chain and may be locally trusted.
     */
    fun analyze(
        certificates: List<X509Certificate>,
        trusted: Boolean,
        nowMillis: Long,
        hostnameMatches: Boolean? = null,
    ): List<ChainIssue> {
        if (certificates.isEmpty()) return if (trusted) emptyList() else listOf(ChainIssue.UNTRUSTED)

        val issues = linkedSetOf<ChainIssue>()
        val now = Date(nowMillis)
        val soonThreshold = safeAdd(nowMillis, EXPIRES_SOON_MILLIS)

        certificates.forEach { certificate ->
            when {
                certificate.notAfter.before(now) -> issues += ChainIssue.EXPIRED
                certificate.notBefore.after(now) -> issues += ChainIssue.NOT_YET_VALID
                certificate.notAfter.time < soonThreshold -> issues += ChainIssue.EXPIRES_SOON
            }
            if (hasWeakSignature(certificate.sigAlgName)) issues += ChainIssue.WEAK_SIGNATURE
            if (hasWeakKey(certificate)) issues += ChainIssue.WEAK_KEY
        }

        val leaf = certificates.first()
        if (isCryptographicallySelfSigned(leaf)) issues += ChainIssue.SELF_SIGNED
        if (!trusted) issues += ChainIssue.UNTRUSTED
        if (hostnameMatches == false) issues += ChainIssue.HOSTNAME_MISMATCH

        if ((0 until certificates.lastIndex).any { index -> !linksTo(certificates[index], certificates[index + 1]) }) {
            issues += ChainIssue.INCOMPLETE_CHAIN
        }
        return issues.toList()
    }

    private fun hasWeakSignature(signatureAlgorithm: String): Boolean {
        val normalized = signatureAlgorithm.uppercase().replace("-", "")
        return listOf("MD2", "MD4", "MD5", "SHA1").any(normalized::contains)
    }

    private fun hasWeakKey(certificate: X509Certificate): Boolean = when (val key = certificate.publicKey) {
        is RSAKey -> key.modulus.bitLength() < MIN_RSA_BITS
        is ECKey -> key.params.curve.field.fieldSize < MIN_EC_BITS
        is DSAKey -> key.params?.p?.bitLength()?.let { it < MIN_DSA_BITS } ?: true
        else -> false
    }

    private fun isCryptographicallySelfSigned(certificate: X509Certificate): Boolean {
        if (certificate.subjectX500Principal != certificate.issuerX500Principal) return false
        return try {
            certificate.verify(certificate.publicKey)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun linksTo(child: X509Certificate, issuer: X509Certificate): Boolean {
        if (child.issuerX500Principal != issuer.subjectX500Principal) return false
        return try {
            child.verify(issuer.publicKey)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun safeAdd(value: Long, increment: Long): Long =
        if (value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
}
