package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.traceroute.HopResult

sealed interface TracerouteFlowResult {
    data class Hop(val hop: HopResult) : TracerouteFlowResult
    data class ValidationError(val message: String) : TracerouteFlowResult
}
