package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.operation.OperationSession

enum class PingEngineKind {
    ICMP,
    REACHABILITY
}

interface PingEngine {
    val kind: PingEngineKind
    val isAvailable: Boolean

    fun ping(request: PingRequest): Flow<PingPacketResult>

    /** Optional operation-aware probe path for engines with blocking resources. */
    fun ping(request: PingRequest, session: OperationSession): Flow<PingPacketResult> = ping(request)
}
