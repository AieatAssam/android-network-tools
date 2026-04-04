package net.aieat.netswissknife.core.network.tls

data class TlsInspectorResult(
    val host: String,
    val port: Int,
    val tlsVersion: String,
    val cipherSuite: String,
    val chain: List<TlsCertificate>,
    val isChainTrusted: Boolean,
    val handshakeTimeMs: Long
)
