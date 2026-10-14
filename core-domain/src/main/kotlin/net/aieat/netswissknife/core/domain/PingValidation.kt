package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

internal fun validatePingCommon(
    host: String,
    timeoutMs: Int,
    intervalMs: Int = 1_000,
    payloadBytes: Int = 56,
    ttl: Int = 64
): ErrorInfo? = when {
    host.isBlank() -> validationError(ErrorCode.HOST_BLANK, "Host must not be empty")
    !HostValidator.isValidHostname(host) -> validationError(ErrorCode.HOST_INVALID, "Invalid host or IP address", host)
    timeoutMs !in 100..30_000 -> validationError(ErrorCode.TIMEOUT_OUT_OF_RANGE, "Timeout must be between 100 ms and 30 000 ms", 100, 30_000)
    payloadBytes !in 0..1472 -> validationError(ErrorCode.PAYLOAD_OUT_OF_RANGE, "Payload size must be between 0 and 1472 bytes", 0, 1472)
    ttl !in 1..255 -> validationError(ErrorCode.TTL_OUT_OF_RANGE, "TTL must be between 1 and 255", 1, 255)
    intervalMs !in 100..10_000 -> validationError(ErrorCode.INTERVAL_OUT_OF_RANGE, "Interval must be between 100 ms and 10 000 ms", 100, 10_000)
    else -> null
}
