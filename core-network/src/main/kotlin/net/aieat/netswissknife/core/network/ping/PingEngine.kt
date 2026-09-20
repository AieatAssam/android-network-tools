package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.flow.Flow

enum class PingEngineKind {
    ICMP,
    REACHABILITY
}

interface PingEngine {
    val kind: PingEngineKind
    val isAvailable: Boolean

    fun ping(request: PingRequest): Flow<PingPacketResult>
}
