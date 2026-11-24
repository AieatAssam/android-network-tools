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

    /** The leaf certificate's SHA-256 does not match the expected pin supplied by the user. */
    PIN_MISMATCH,
    ;

    /** A finding that fails the connection verdict. The others are advisory and do not change it. */
    val isBlocking: Boolean
        get() = this in BLOCKING

    private companion object {
        val BLOCKING = setOf(UNTRUSTED, EXPIRED, NOT_YET_VALID, HOSTNAME_MISMATCH, PIN_MISMATCH)
    }
}
