package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import net.aieat.netswissknife.core.network.HostResolver
import net.aieat.netswissknife.core.network.InetAddressHostResolver
import java.net.UnknownHostException

/**
 * Resolves a target once and selects the first available ping engine. An
 * engine may fail before emitting anything, in which case the next engine is
 * tried; failures after an emission become an error packet and are not
 * replayed through a second engine.
 */
class PingRepositoryImpl(
    private val checker: ((host: String, timeoutMs: Int) -> ReachabilityResult)? = null,
    private val delayBetweenProbesMs: Long = 1_000L,
    private val engines: List<PingEngine>? = null,
    private val resolver: HostResolver = InetAddressHostResolver
) : PingRepository {

    private val _lastEngineUsed = MutableStateFlow<PingEngineKind?>(null)
    override val lastEngineUsed: StateFlow<PingEngineKind?> = _lastEngineUsed.asStateFlow()

    private val configuredEngines: List<PingEngine>
        get() = engines ?: listOf(
            ReachabilityPingEngine(checker ?: ReachabilityPingEngine.DEFAULT_CHECKER)
        )

    /** Legacy adapter retained for callers compiled against the old API. */
    @Deprecated("Use ping(PingRequest)")
    override fun ping(host: String, count: Int, timeoutMs: Int): Flow<PingPacketResult> {
        val legacyChecker = checker
        if (legacyChecker == null) return ping(PingRequest(host, count = count, timeoutMs = timeoutMs))
        return legacyFlow(PingRequest(host, count = count, timeoutMs = timeoutMs), legacyChecker)
    }

    /** Legacy adapter retained for callers compiled against the old API. */
    @Deprecated("Use continuousPing(PingRequest)")
    override fun continuousPing(host: String, timeoutMs: Int): Flow<PingPacketResult> {
        val legacyChecker = checker
        if (legacyChecker == null) return continuousPing(PingRequest(host, count = 0, timeoutMs = timeoutMs))
        return legacyFlow(PingRequest(host, count = 0, timeoutMs = timeoutMs), legacyChecker)
    }

    override fun ping(request: PingRequest): Flow<PingPacketResult> = runSession(request.copy(count = request.count.coerceAtLeast(1)))

    override fun continuousPing(request: PingRequest): Flow<PingPacketResult> = runSession(request.copy(count = 0))

    private fun legacyFlow(
        request: PingRequest,
        legacyChecker: (String, Int) -> ReachabilityResult
    ): Flow<PingPacketResult> = ReachabilityPingEngine(legacyChecker).ping(
        request.copy(intervalMs = delayBetweenProbesMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    ).let { upstream ->
        kotlinx.coroutines.flow.flow {
            upstream.collect { emit(it.copy(engine = PingEngineKind.REACHABILITY)) }
        }
    }

    private fun runSession(request: PingRequest): Flow<PingPacketResult> = flow {
        val resolvedIp = try {
            request.resolvedIp ?: resolver.resolve(request.host)
        } catch (e: UnknownHostException) {
            emit(
                PingPacketResult(
                    sequence = 1,
                    host = request.host,
                    rtTimeMs = null,
                    status = PingStatus.ERROR,
                    errorMessage = e.message ?: "Unknown host: ${request.host}"
                )
            )
            return@flow
        } catch (e: Exception) {
            emit(
                PingPacketResult(
                    sequence = 1,
                    host = request.host,
                    rtTimeMs = null,
                    status = PingStatus.ERROR,
                    errorMessage = e.message ?: e.javaClass.simpleName
                )
            )
            return@flow
        }

        val resolvedRequest = request.copy(resolvedIp = resolvedIp)
        val candidates = configuredEngines.filter { it.isAvailable }
        if (candidates.isEmpty()) {
            emit(errorPacket(resolvedRequest, "No ping engine is available"))
            return@flow
        }

        var selected = false
        for (engine in candidates) {
            var emitted = false
            var lastSequence = 0
            try {
                engine.ping(resolvedRequest).collect { packet ->
                    emitted = true
                    lastSequence = packet.sequence
                    selected = true
                    _lastEngineUsed.value = engine.kind
                    emit(packet.copy(engine = engine.kind))
                }
                if (selected) return@flow
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (emitted) {
                    _lastEngineUsed.value = engine.kind
                    emit(errorPacket(resolvedRequest, e.message ?: e.javaClass.simpleName, lastSequence + 1))
                    return@flow
                }
            }
        }
        emit(errorPacket(resolvedRequest, "All ping engines failed before producing a result"))
    }.flowOn(Dispatchers.IO)

    private fun errorPacket(request: PingRequest, message: String, sequence: Int = 1) = PingPacketResult(
        sequence = sequence,
        host = request.host,
        rtTimeMs = null,
        status = PingStatus.ERROR,
        errorMessage = message,
        fromIp = request.resolvedIp
    )
}
