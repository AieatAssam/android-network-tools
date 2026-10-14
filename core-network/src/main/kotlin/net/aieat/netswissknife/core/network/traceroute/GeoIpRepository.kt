package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.operation.OperationSession

interface GeoIpRepository {
    /**
     * Looks up geographic location for a public IP address.
     * Returns null for private/reserved addresses or on lookup failure.
     */
    suspend fun lookup(ip: String): HopGeoLocation?

    /** Runs the enrichment request under a caller-owned operation when available. */
    suspend fun lookup(ip: String, operationSession: OperationSession): HopGeoLocation? = lookup(ip)
}
