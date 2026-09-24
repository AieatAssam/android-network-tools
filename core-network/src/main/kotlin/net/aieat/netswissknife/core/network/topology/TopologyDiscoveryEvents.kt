package net.aieat.netswissknife.core.network.topology

sealed class TopologyDiscoveryEvent {
    data class NodeDiscovered(val node: TopologyNode) : TopologyDiscoveryEvent()
    data class LinkDiscovered(val link: TopologyLink) : TopologyDiscoveryEvent()
    data class Progress(val message: String, val nodesDone: Int) : TopologyDiscoveryEvent()
    data class Complete(val graph: TopologyGraph) : TopologyDiscoveryEvent()
    /** Operation deadline won; includes everything retained before cancellation. */
    data class TimeLimit(val partialGraph: TopologyGraph) : TopologyDiscoveryEvent()
    data class Error(val message: String, val cause: Throwable? = null) : TopologyDiscoveryEvent()
}
