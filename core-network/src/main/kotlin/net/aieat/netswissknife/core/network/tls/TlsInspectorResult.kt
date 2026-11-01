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
    /** Named issuing root when the only chain finding is an unknown root; null otherwise. */
    val untrustedRootName: String? = null,
) {
    /**
     * Overall verdict. A chain the device trusts is still a failed connection when the certificate
     * is for another hostname, is expired or not yet valid, or does not match the expected pin.
     * [isChainTrusted] reports only the trust-store decision.
     */
    val isConnectionTrusted: Boolean
        get() {
            if (!isChainTrusted || hostnameMatches == false || pinMatch == false) return false
            return chainIssues.none { it in BLOCKING_ISSUES }
        }

    private companion object {
        val BLOCKING_ISSUES =
            setOf(
                ChainIssue.UNTRUSTED,
                ChainIssue.EXPIRED,
                ChainIssue.NOT_YET_VALID,
                ChainIssue.HOSTNAME_MISMATCH,
                ChainIssue.PIN_MISMATCH,
            )
    }
}
