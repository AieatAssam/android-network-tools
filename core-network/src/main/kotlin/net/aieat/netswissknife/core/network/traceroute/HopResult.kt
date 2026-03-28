package net.aieat.netswissknife.core.network.traceroute

/**
 * Result for a single hop in a traceroute.
 *
 * @param hopNumber    1-based index of this hop.
 * @param ip           IP address of the responding router, or null if it timed out.
 * @param hostname     Reverse-DNS hostname, or null if unavailable.
 * @param rtTimeMs     Round-trip time in milliseconds, or null on timeout/error.
 * @param status       Whether the hop responded, timed out, or produced an error.
 * @param geoLocation  Geographic location of this hop's IP, or null if unavailable.
 */
data class HopResult(
    val hopNumber: Int,
    val ip: String?,
    val hostname: String?,
    val rtTimeMs: Long?,
    val status: HopStatus,
    val geoLocation: HopGeoLocation? = null
)
