package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.ping.PingPacketResult

/**
 * Items emitted by [PingUseCase].
 *
 * A [ValidationError] is emitted once (and the flow then completes) when the
 * input [PingParams] fail validation.  [Packet] items are emitted as each probe
 * completes during a valid session.
 */
sealed interface PingFlowResult {
    /** A single probe result forwarded from the repository. */
    data class Packet(val packet: PingPacketResult) : PingFlowResult

    /** Validation failed before any probes were sent. */
    data class ValidationError(val message: String) : PingFlowResult
}
