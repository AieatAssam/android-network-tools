package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation

sealed interface TracerouteFlowResult {
    /** The probe result, emitted before any optional reverse-DNS or GeoIP work. */
    data class Hop(val hop: HopResult) : TracerouteFlowResult
    /** Optional enrichment for a previously emitted hop, identified by [hopNumber]. */
    data class HopEnriched(
        val hopNumber: Int,
        val hostname: String?,
        val geoLocation: HopGeoLocation?,
    ) : TracerouteFlowResult
    data class ValidationError(val message: String) : TracerouteFlowResult
}
