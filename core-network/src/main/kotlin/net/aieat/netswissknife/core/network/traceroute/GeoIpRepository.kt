package net.aieat.netswissknife.core.network.traceroute

interface GeoIpRepository {
    /**
     * Looks up geographic location for a public IP address.
     * Returns null for private/reserved addresses or on lookup failure.
     */
    suspend fun lookup(ip: String): HopGeoLocation?
}
