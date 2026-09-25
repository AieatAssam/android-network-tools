package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.tls.TlsInspectorRepository
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import net.aieat.netswissknife.core.network.tls.TlsInspectorOptions
import net.aieat.netswissknife.core.network.operation.OperationSession

data class TlsInspectorParams(
    val host: String,
    val port: Int = 443,
    val timeoutMs: Int = 10_000,
    val probeProtocols: Boolean = false,
    val expectedPinSha256: String? = null,
)

object TlsInspectorErrorKeys {
    const val PIN_INVALID_CODE = "TLS_PIN_INVALID"
    const val PIN_INVALID_DESCRIPTION = "tls_pin_invalid"
}

class TlsInspectorUseCase(private val repository: TlsInspectorRepository) {

    suspend operator fun invoke(params: TlsInspectorParams): NetworkResult<TlsInspectorResult> {
        return invokeValidated(params, null)
    }

    /** Caller-owned operation variant; the one-argument overload remains source-compatible. */
    suspend operator fun invoke(
        params: TlsInspectorParams,
        operationSession: OperationSession,
    ): NetworkResult<TlsInspectorResult> = invokeValidated(params, operationSession)

    private suspend fun invokeValidated(
        params: TlsInspectorParams,
        operationSession: OperationSession?,
    ): NetworkResult<TlsInspectorResult> {
        val host = HostValidator.normalize(params.host) ?: params.host.trim()
        if (host.isBlank()) return NetworkResult.error(ErrorCode.HOST_BLANK, "Host must not be blank")
        if (!HostValidator.isValidHostname(host)) return NetworkResult.error(ErrorCode.HOST_INVALID, "Invalid host or IP address")
        if (params.port !in 1..65_535) return NetworkResult.error(ErrorCode.PORT_OUT_OF_RANGE, "Port must be between 1 and 65535", args = listOf(params.port, 1, 65_535))
        if (params.timeoutMs !in 500..30_000) return NetworkResult.error(ErrorCode.TIMEOUT_OUT_OF_RANGE, "Timeout must be between 500 ms and 30 000 ms", args = listOf(500, 30_000))

        val normalizedPin = params.expectedPinSha256?.let { supplied ->
            supplied.trim().replace(":", "").uppercase()
        }
        if (normalizedPin != null && !normalizedPin.matches(SHA256_HEX_PATTERN)) {
            return NetworkResult.error(
                code = ErrorCode.TLS_PIN_INVALID,
                developerMessage = "SHA-256 pin must contain exactly 64 hexadecimal characters",
                legacyCode = TlsInspectorErrorKeys.PIN_INVALID_CODE,
                descriptionKey = TlsInspectorErrorKeys.PIN_INVALID_DESCRIPTION,
            )
        }

        val options = TlsInspectorOptions(params.probeProtocols, normalizedPin)
        val hasAdvancedOptions = params.probeProtocols || normalizedPin != null
        return if (operationSession == null && !hasAdvancedOptions) {
            repository.inspect(host, params.port, params.timeoutMs)
        } else if (operationSession == null) {
            repository.inspect(host, params.port, params.timeoutMs, options)
        } else if (!hasAdvancedOptions) {
            repository.inspect(host, params.port, params.timeoutMs, operationSession)
        } else {
            repository.inspect(host, params.port, params.timeoutMs, operationSession, options)
        }
    }

    private companion object {
        val SHA256_HEX_PATTERN = Regex("[0-9A-F]{64}")
    }
}
