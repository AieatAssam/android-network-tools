package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Reachability fallback used when the platform ICMP socket is unavailable. */
class ReachabilityPingEngine(
    private val checker: (ip: String, timeoutMs: Int) -> ReachabilityResult = DEFAULT_CHECKER
) : PingEngine {
    override val kind: PingEngineKind = PingEngineKind.REACHABILITY
    override val isAvailable: Boolean = true

    override fun ping(request: PingRequest): Flow<PingPacketResult> = pingInternal(request, session = null)

    override fun ping(request: PingRequest, session: OperationSession): Flow<PingPacketResult> =
        pingInternal(request, session)

    private fun pingInternal(request: PingRequest, session: OperationSession?): Flow<PingPacketResult> = flow {
        var sequence = 1
        while (request.count == 0 || sequence <= request.count) {
            val ip = request.resolvedIp ?: request.host
            val startedNs = System.nanoTime()
            val packet = try {
                val result = if (session == null) checker(ip, request.timeoutMs) else {
                    PingBlockingCallExecutor.run(session) { checker(ip, request.timeoutMs) }
                }
                val measuredMs = (System.nanoTime() - startedNs) / 1_000_000L
                val elapsedMs = result.rtTimeMs.coerceAtLeast(measuredMs)
                val status = when {
                    result.errorMessage != null -> PingStatus.ERROR
                    result.reachable -> PingStatus.SUCCESS
                    elapsedMs >= request.timeoutMs * 0.9 -> PingStatus.TIMEOUT
                    else -> PingStatus.UNREACHABLE
                }
                PingPacketResult(
                    sequence = sequence,
                    host = request.host,
                    rtTimeMs = elapsedMs.takeIf { status == PingStatus.SUCCESS },
                    status = status,
                    errorMessage = result.errorMessage,
                    fromIp = ip
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                PingPacketResult(
                    sequence = sequence,
                    host = request.host,
                    rtTimeMs = null,
                    status = PingStatus.ERROR,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    fromIp = ip
                )
            }
            emit(packet)
            if (request.count == 0 || sequence < request.count) {
                delay(request.intervalMs.toLong())
            }
            sequence++
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        val DEFAULT_CHECKER: (String, Int) -> ReachabilityResult = { ip, timeoutMs ->
            val startedNs = System.nanoTime()
            try {
                val reachable = java.net.InetAddress.getByName(ip).isReachable(timeoutMs)
                ReachabilityResult(
                    reachable = reachable,
                    rtTimeMs = (System.nanoTime() - startedNs) / 1_000_000L
                )
            } catch (e: Exception) {
                ReachabilityResult(
                    reachable = false,
                    rtTimeMs = (System.nanoTime() - startedNs) / 1_000_000L,
                    errorMessage = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }
}
