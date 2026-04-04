package net.aieat.netswissknife.core.network.tls

import net.aieat.netswissknife.core.network.NetworkResult

interface TlsInspectorRepository {
    suspend fun inspect(host: String, port: Int, timeoutMs: Int): NetworkResult<TlsInspectorResult>
}
