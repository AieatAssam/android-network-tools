package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator

internal fun validatePingCommon(
    host: String,
    timeoutMs: Int,
    intervalMs: Int = 1_000,
    payloadBytes: Int = 56,
    ttl: Int = 64
): String? = when {
    host.isBlank() -> "Host must not be empty"
    !HostValidator.isValidHostname(host) -> "Invalid host or IP address"
    timeoutMs !in 100..30_000 -> "Timeout must be between 100 ms and 30 000 ms"
    payloadBytes !in 0..1472 -> "Payload size must be between 0 and 1472 bytes"
    ttl !in 1..255 -> "TTL must be between 1 and 255"
    intervalMs !in 100..10_000 -> "Interval must be between 100 ms and 10 000 ms"
    else -> null
}
