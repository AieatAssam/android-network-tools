package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.HostValidator
import com.example.netswissknife.core.network.traceroute.GeoIpRepository
import com.example.netswissknife.core.network.traceroute.TracerouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Validates [TracerouteParams], then streams [TracerouteFlowResult]s by:
 *   1. Running the traceroute via [TracerouteRepository].
 *   2. Enriching each hop with geolocation data from [GeoIpRepository].
 *
 * Validation rules:
 *   - host must not be blank and must be a valid hostname or IPv4 address
 *   - maxHops must be in 1..64
 *   - timeoutMs must be in 500..30_000
 */
class TracerouteUseCase(
    private val tracerouteRepository: TracerouteRepository,
    private val geoIpRepository: GeoIpRepository
) {
    operator fun invoke(params: TracerouteParams): Flow<TracerouteFlowResult> {
        val trimmedHost = params.host.trim()

        val errorMessage: String? = when {
            trimmedHost.isBlank()                        -> "Host must not be empty"
            !HostValidator.isValidHostname(trimmedHost)  -> "Invalid host or IP address"
            params.maxHops !in 1..64                     -> "Max hops must be between 1 and 64"
            params.timeoutMs !in 500..30_000             -> "Timeout must be between 500 ms and 30 000 ms"
            else                                         -> null
        }

        if (errorMessage != null) {
            return flow { emit(TracerouteFlowResult.ValidationError(errorMessage)) }
        }

        return flow {
            tracerouteRepository.trace(
                host          = trimmedHost,
                maxHops       = params.maxHops,
                timeoutMs     = params.timeoutMs,
                queriesPerHop = params.queriesPerHop
            ).collect { hop ->
                val hopIp = hop.ip
                val enriched = if (hopIp != null) {
                    val geo = geoIpRepository.lookup(hopIp)
                    hop.copy(geoLocation = geo)
                } else hop
                emit(TracerouteFlowResult.Hop(enriched))
            }
        }
    }
}
