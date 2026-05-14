package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator

internal fun validatePingCommon(host: String, timeoutMs: Int, packetSize: Int): String? = when {
    host.isBlank() -> "Host must not be empty"
    !HostValidator.isValidHostname(host) -> "Invalid host or IP address"
    timeoutMs !in 100..30_000 -> "Timeout must be between 100 ms and 30 000 ms"
    packetSize !in 1..65_507 -> "Packet size must be between 1 and 65 507 bytes"
    else -> null
}
