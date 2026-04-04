package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.tls.TlsInspectorRepository
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult

data class TlsInspectorParams(
    val host: String,
    val port: Int = 443,
    val timeoutMs: Int = 10_000
)

class TlsInspectorUseCase(private val repository: TlsInspectorRepository) {

    suspend operator fun invoke(params: TlsInspectorParams): NetworkResult<TlsInspectorResult> {
        val host = params.host.trim()
        if (host.isBlank()) return NetworkResult.Error("Host must not be blank")
        if (!HostValidator.isValidHostname(host)) return NetworkResult.Error("Invalid host or IP address")
        if (params.port !in 1..65_535) return NetworkResult.Error("Port must be between 1 and 65535")
        if (params.timeoutMs !in 500..30_000) return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        return repository.inspect(host, params.port, params.timeoutMs)
    }
}
