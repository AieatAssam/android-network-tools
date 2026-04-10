package net.aieat.netswissknife.core.network.httprobe

import net.aieat.netswissknife.core.network.NetworkResult

interface HttpProbeRepository {
    suspend fun probe(request: HttpProbeRequest): NetworkResult<HttpProbeResult>
}
