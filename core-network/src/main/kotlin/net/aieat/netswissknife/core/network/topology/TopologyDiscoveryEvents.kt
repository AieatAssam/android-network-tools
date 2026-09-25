package net.aieat.netswissknife.core.network.topology

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

sealed class TopologyDiscoveryEvent {
    data class NodeDiscovered(val node: TopologyNode) : TopologyDiscoveryEvent()
    data class LinkDiscovered(val link: TopologyLink) : TopologyDiscoveryEvent()
    data class Progress(val message: String, val nodesDone: Int) : TopologyDiscoveryEvent()
    data class Complete(val graph: TopologyGraph) : TopologyDiscoveryEvent()
    /** Operation deadline won; includes everything retained before cancellation. */
    data class TimeLimit(val partialGraph: TopologyGraph) : TopologyDiscoveryEvent()
    data class Error(val errors: List<ErrorInfo>, val cause: Throwable? = null) : TopologyDiscoveryEvent() {
        /** Existing developer-facing copy retained for callers that still display/log a message. */
        val message: String
            get() = errors.joinToString("; ") { it.developerMessage ?: it.code.name.lowercase() }

        constructor(message: String, cause: Throwable? = null) : this(
            errors = listOf(ErrorInfo(ErrorCode.UNKNOWN, developerMessage = message)),
            cause = cause,
        )
    }
}
