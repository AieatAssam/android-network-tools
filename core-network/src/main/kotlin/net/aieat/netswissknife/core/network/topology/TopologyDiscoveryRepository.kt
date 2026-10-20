package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.operation.OperationSession

interface TopologyDiscoveryRepository {
    fun discover(params: TopologyParams): Flow<TopologyDiscoveryEvent>

    /** Caller-owned operation hook; implementations that have not adopted scoped operations
     * retain their legacy behavior through this default. */
    fun discover(params: TopologyParams, session: OperationSession): Flow<TopologyDiscoveryEvent> =
        discover(params)
}
