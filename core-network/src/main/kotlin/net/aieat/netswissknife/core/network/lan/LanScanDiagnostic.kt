package net.aieat.netswissknife.core.network.lan

/** A probe observation that is useful for troubleshooting but cannot confirm a host. */
enum class LanScanDiagnosticReason {
    TCP_REFUSED,
    TCP_TIMED_OUT,
    TCP_UNREACHABLE,
    TCP_POLICY_DENIED,
    TCP_UNKNOWN_FAILURE,
}

data class LanScanDiagnostic(
    val ip: String,
    val reason: LanScanDiagnosticReason,
    val port: Int? = null,
    val detail: String? = null,
)
