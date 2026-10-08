package net.aieat.netswissknife.core.network.tls

data class TlsInspectorResult(
    val host: String,
    val port: Int,
    val tlsVersion: String,
    val cipherSuite: String,
    val chain: List<TlsCertificate>,
    val isChainTrusted: Boolean,
    val handshakeTimeMs: Long,
    val hostnameMatches: Boolean? = null,
    val chainIssues: List<ChainIssue> = emptyList(),
    val connectTimeMs: Long = 0,
    val alpn: String? = null,
    val protocolSupport: Map<String, Boolean>? = null,
    val protocolProbeUnknown: Set<String> = emptySet(),
    val protocolProbeNotTestable: Set<String> = emptySet(),
    val ocspStapled: Boolean? = null,
    val pinMatch: Boolean? = null,
)
