package net.aieat.netswissknife.app.ping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import me.impa.icmpenguin.ProbeResult
import me.impa.icmpenguin.ping.Pinger
import net.aieat.netswissknife.core.network.ping.PingEngine
import net.aieat.netswissknife.core.network.ping.PingEngineKind
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingRequest

sealed interface IcmpProbe {
    val sequence: Int
    val remote: String
    val probeSize: Int

    data class Success(
        override val sequence: Int,
        override val remote: String,
        override val probeSize: Int,
        val elapsedUsec: Int,
        val ttl: Int
    ) : IcmpProbe

    data class Timeout(
        override val sequence: Int,
        override val remote: String,
        override val probeSize: Int
    ) : IcmpProbe

    data class Unreachable(
        override val sequence: Int,
        override val remote: String,
        override val probeSize: Int,
        val reason: String
    ) : IcmpProbe

    data class Error(
        override val sequence: Int,
        override val remote: String,
        override val probeSize: Int,
        val message: String
    ) : IcmpProbe
}

object IcmpenguinResultMapper {
    fun toPacket(host: String, probe: IcmpProbe): PingPacketResult = when (probe) {
        is IcmpProbe.Success -> PingPacketResult(
            sequence = probe.sequence,
            host = host,
            rtTimeMs = probe.elapsedUsec / 1_000L,
            rtTimeMicros = probe.elapsedUsec,
            status = net.aieat.netswissknife.core.network.ping.PingStatus.SUCCESS,
            replyTtl = probe.ttl,
            bytes = probe.probeSize,
            fromIp = probe.remote
        )
        is IcmpProbe.Timeout -> PingPacketResult(
            sequence = probe.sequence,
            host = host,
            rtTimeMs = null,
            status = net.aieat.netswissknife.core.network.ping.PingStatus.TIMEOUT,
            bytes = probe.probeSize,
            fromIp = probe.remote
        )
        is IcmpProbe.Unreachable -> PingPacketResult(
            sequence = probe.sequence,
            host = host,
            rtTimeMs = null,
            status = net.aieat.netswissknife.core.network.ping.PingStatus.UNREACHABLE,
            errorMessage = probe.reason,
            bytes = probe.probeSize,
            fromIp = probe.remote
        )
        is IcmpProbe.Error -> PingPacketResult(
            sequence = probe.sequence,
            host = host,
            rtTimeMs = null,
            status = net.aieat.netswissknife.core.network.ping.PingStatus.ERROR,
            errorMessage = probe.message,
            bytes = probe.probeSize,
            fromIp = probe.remote
        )
    }
}

class IcmpenguinPingEngine(
    private val nativeProbeFactory: (PingRequest) -> Flow<IcmpProbe> = ::nativeProbeFlow,
) : PingEngine {
    override val kind: PingEngineKind = PingEngineKind.ICMP
    @Volatile
    private var nativeUnavailable = false

    override val isAvailable: Boolean
        get() = !nativeUnavailable

    override fun ping(request: PingRequest): Flow<PingPacketResult> = flow {
        val nativeFlow = try {
            nativeProbeFactory(request)
        } catch (failure: LinkageError) {
            nativeUnavailable = true
            flowOf(linkageFailureProbe(request, failure))
        }
        nativeFlow
            .catch { failure ->
                if (failure !is LinkageError) throw failure
                // This catch is upstream of our collector emission: only failures from
                // native flow execution are considered engine loading failures.
                nativeUnavailable = true
                emit(linkageFailureProbe(request, failure))
            }
            .collect { result -> emit(IcmpenguinResultMapper.toPacket(request.host, result)) }
    }.flowOn(Dispatchers.IO)
}

private fun linkageFailureProbe(request: PingRequest, failure: LinkageError) = IcmpProbe.Error(
    sequence = 1,
    remote = request.resolvedIp ?: request.host,
    probeSize = request.payloadBytes,
    message = "Native ICMP engine is unavailable: ${failure.message ?: failure.javaClass.simpleName}",
)

private fun nativeProbeFlow(request: PingRequest): Flow<IcmpProbe> = flow {
    val pinger = Pinger(
        host = request.resolvedIp ?: request.host,
        ttl = request.ttl,
        timeout = request.timeoutMs,
        maxPingCount = if (request.count == 0) Pinger.INFINITE else request.count,
        interval = request.intervalMs,
        probeSize = request.payloadBytes,
        pattern = null,
        sourceIp = ""
    )
    pinger.ping().collect { result -> emit(result.toMirror()) }
}

private fun ProbeResult.toMirror(): IcmpProbe = when (this) {
    is ProbeResult.Success -> IcmpProbe.Success(
        sequence = sequence,
        remote = remote,
        probeSize = probeSize,
        elapsedUsec = elapsedUsec,
        ttl = ttl
    )
    is ProbeResult.Timeout -> IcmpProbe.Timeout(sequence, remote, probeSize)
    is ProbeResult.HostUnreachable -> IcmpProbe.Unreachable(
        sequence, remote, probeSize, "Host unreachable${offender?.let { " from $it" } ?: ""}"
    )
    is ProbeResult.NetUnreachable -> IcmpProbe.Unreachable(
        sequence, remote, probeSize, "Network unreachable${offender?.let { " from $it" } ?: ""}"
    )
    is ProbeResult.ConnectionRefused -> IcmpProbe.Unreachable(
        sequence, remote, probeSize, "Connection refused${offender?.let { " from $it" } ?: ""}"
    )
    is ProbeResult.NetError -> IcmpProbe.Error(
        sequence,
        remote,
        probeSize,
        if (errType == 11) "TTL exceeded" else "Network error (errno=$errNo, code=$errCode)"
    )
    is ProbeResult.Unknown -> IcmpProbe.Error(sequence, remote, probeSize, error)
}
