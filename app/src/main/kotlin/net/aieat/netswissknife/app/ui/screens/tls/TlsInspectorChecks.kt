package net.aieat.netswissknife.app.ui.screens.tls

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.messageRes
import net.aieat.netswissknife.core.network.HostValidator

private const val SCHEME_SEPARATOR = "://"
private val LEGACY_PROTOCOLS = setOf("TLSv1", "TLSv1.1")

/** Message for a rejected host. A URL gets its own message, because its scheme is not a host problem. */
@StringRes
internal fun tlsHostErrorRes(host: String): Int =
    if (host.contains(SCHEME_SEPARATOR)) {
        R.string.tls_error_host_scheme
    } else {
        HostValidator.problemOf(host)?.messageRes() ?: R.string.error_host_invalid
    }

/** True when the server accepts TLS 1.0 or 1.1, which are deprecated. */
internal fun legacyProtocolAdvisoryNeeded(support: Map<String, Boolean>?): Boolean {
    val accepted = support.orEmpty().filterValues { it }.keys
    return accepted.any { it in LEGACY_PROTOCOLS }
}
