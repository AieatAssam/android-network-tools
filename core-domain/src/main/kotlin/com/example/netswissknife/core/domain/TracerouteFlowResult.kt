package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.traceroute.HopResult

sealed interface TracerouteFlowResult {
    data class Hop(val hop: HopResult) : TracerouteFlowResult
    data class ValidationError(val message: String) : TracerouteFlowResult
}
