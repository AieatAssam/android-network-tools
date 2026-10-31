package net.aieat.netswissknife.app.ui.components

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.HostProblem

/** Plain-language message for why [HostProblem] rejected a host or address. */
@StringRes
fun HostProblem.messageRes(): Int =
    when (this) {
        HostProblem.BLANK -> R.string.error_host_blank
        HostProblem.CONTAINS_WHITESPACE -> R.string.error_invalid_host
        HostProblem.IPV4_INVALID -> R.string.error_host_ipv4_invalid
        HostProblem.IPV6_INVALID -> R.string.error_host_ipv6_invalid
        HostProblem.EMPTY_LABEL -> R.string.error_host_empty_label
        HostProblem.INVALID_LABEL -> R.string.error_host_invalid_label
        HostProblem.INVALID_CHARACTERS -> R.string.error_host_invalid_characters
        HostProblem.TOO_LONG -> R.string.error_host_too_long
        HostProblem.INVALID -> R.string.error_host_invalid
    }
