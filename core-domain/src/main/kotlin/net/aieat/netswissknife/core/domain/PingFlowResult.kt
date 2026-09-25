package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

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
    data class ValidationError(val info: ErrorInfo) : PingFlowResult {
        val message: String get() = info.developerCopy()
        constructor(message: String) : this(ErrorInfo(ErrorCode.UNKNOWN, developerMessage = message))
    }
}
