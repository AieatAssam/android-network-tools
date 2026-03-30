package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.flow.Flow

interface TopologyDiscoveryRepository {
    fun discover(params: TopologyParams): Flow<TopologyDiscoveryEvent>
}
