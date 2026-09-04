package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator

internal fun validatePingCommon(host: String, timeoutMs: Int): String? = when {
    host.isBlank() -> "Host must not be empty"
    !HostValidator.isValidHostname(host) -> "Invalid host or IP address"
    timeoutMs !in 100..30_000 -> "Timeout must be between 100 ms and 30 000 ms"
    else -> null
}
