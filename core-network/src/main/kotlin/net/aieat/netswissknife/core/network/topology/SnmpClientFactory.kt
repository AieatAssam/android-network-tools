package net.aieat.netswissknife.core.network.topology

import net.aieat.netswissknife.core.network.operation.OperationSession

fun interface SnmpClientFactory {
    fun create(params: TopologyParams): SnmpClient

    /** Existing factories remain source-compatible; production factories can receive the run deadline. */
    fun create(params: TopologyParams, session: OperationSession): SnmpClient = create(params)
}
