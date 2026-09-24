package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.operation.OperationSession

/** Resolves a hop IP address to a reverse-DNS hostname under the trace operation. */
fun interface TracerouteReverseDnsRepository {
    suspend fun lookup(ip: String, operationSession: OperationSession): String?
}
