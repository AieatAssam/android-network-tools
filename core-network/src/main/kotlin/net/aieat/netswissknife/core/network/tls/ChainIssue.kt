package net.aieat.netswissknife.core.network.tls

/** Findings produced by the pure TLS certificate-chain analyzer. */
enum class ChainIssue {
    EXPIRED,
    NOT_YET_VALID,
    EXPIRES_SOON,
    SELF_SIGNED,
    UNTRUSTED,
    HOSTNAME_MISMATCH,
    WEAK_SIGNATURE,
    WEAK_KEY,
    INCOMPLETE_CHAIN,
}
