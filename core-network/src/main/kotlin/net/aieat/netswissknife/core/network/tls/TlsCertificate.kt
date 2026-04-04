package net.aieat.netswissknife.core.network.tls

data class TlsCertificate(
    val subjectCN: String,
    val subjectOrg: String?,
    val issuerCN: String,
    val issuerOrg: String?,
    val notBefore: Long,
    val notAfter: Long,
    val isExpired: Boolean,
    val isSelfSigned: Boolean,
    val sans: List<String>,
    val serialNumber: String,
    val signatureAlgorithm: String,
    val publicKeyAlgorithm: String,
    val publicKeyBits: Int,
    val sha256Fingerprint: String
)
