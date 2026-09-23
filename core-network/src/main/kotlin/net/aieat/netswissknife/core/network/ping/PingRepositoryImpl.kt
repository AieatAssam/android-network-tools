package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.aieat.netswissknife.core.network.HostResolver
import net.aieat.netswissknife.core.network.InetAddressHostResolver
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
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
        val request = PingRequest(host, count = count, timeoutMs = timeoutMs)
        if (legacyChecker == null) return ping(request)
        return legacyFlow(request, legacyChecker)
    }

    /** Legacy adapter retained for callers compiled against the old API. */
    @Deprecated("Use continuousPing(PingRequest)")
    override fun continuousPing(host: String, timeoutMs: Int): Flow<PingPacketResult> {
        val legacyChecker = checker
        val request = PingRequest(host, count = 0, timeoutMs = timeoutMs)
        if (legacyChecker == null) return continuousPing(request)
        return legacyFlow(request, legacyChecker)
    }

    override fun ping(request: PingRequest): Flow<PingPacketResult> =
        runSession(request.copy(count = request.count.coerceAtLeast(1)))

    override fun ping(request: PingRequest, session: OperationSession): Flow<PingPacketResult> =
        runSession(request.copy(count = request.count.coerceAtLeast(1)), session)

    override fun continuousPing(request: PingRequest): Flow<PingPacketResult> =
        runSession(request.copy(count = 0))

    override fun continuousPing(request: PingRequest, session: OperationSession): Flow<PingPacketResult> =
        runSession(request.copy(count = 0), session)

    private fun legacyFlow(
        request: PingRequest,
        legacyChecker: (String, Int) -> ReachabilityResult
    ): Flow<PingPacketResult> {
        val configuredRequest = request.copy(
            intervalMs = delayBetweenProbesMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
        return withOperationSession(configuredRequest, suppliedSession = null) { session ->
            ReachabilityPingEngine(legacyChecker).ping(configuredRequest, session)
                .let { upstream ->
                    kotlinx.coroutines.flow.flow {
                        upstream.collect { emit(it.copy(engine = PingEngineKind.REACHABILITY)) }
                    }
                }
        }
    }

    private fun runSession(request: PingRequest, session: OperationSession? = null): Flow<PingPacketResult> =
        withOperationSession(request, session) { operationSession ->
            if (request.count == 0) runContinuousSession(request, operationSession)
            else runBoundedSession(request, operationSession)
        }

    private fun withOperationSession(
        request: PingRequest,
        suppliedSession: OperationSession?,
        source: (OperationSession) -> Flow<PingPacketResult>,
    ): Flow<PingPacketResult> = channelFlow {
        val session = suppliedSession ?: PingOperation.newSession(request)
        OperationRunner.runOrJoin(session) {
            source(session).collect { packet ->
                ensureCurrentOperationActive()
                send(packet)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun runBoundedSession(request: PingRequest, session: OperationSession): Flow<PingPacketResult> = flow {
        val resolvedIp = try {
            request.resolvedIp ?: PingBlockingCallExecutor.run(session) { resolver.resolve(request.host) }
        } catch (e: CancellationException) {
            throw e
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
        val failureMessages = mutableListOf<String>()
        for (engine in candidates) {
            var emittedUsablePacket = false
            var lastSequence = 0
            try {
                engine.ping(resolvedRequest, session).collect { packet ->
                    lastSequence = packet.sequence
                    if (packet.status == PingStatus.ERROR && !emittedUsablePacket) {
                        packet.errorMessage?.takeIf { it.isNotBlank() }?.let(failureMessages::add)
                        throw EngineUnavailableException(packet.errorMessage)
                    }
                    emittedUsablePacket = true
                    selected = true
                    _lastEngineUsed.value = engine.kind
                    emit(packet.copy(engine = engine.kind))
                }
                if (selected) return@flow
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e !is EngineUnavailableException) {
                    e.message?.takeIf { it.isNotBlank() }?.let(failureMessages::add)
                }
                if (emittedUsablePacket) {
                    _lastEngineUsed.value = engine.kind
                    emit(errorPacket(resolvedRequest, e.message ?: e.javaClass.simpleName, lastSequence + 1))
                    return@flow
                }
            }
        }
        emit(errorPacket(resolvedRequest, allEnginesFailedMessage(failureMessages)))
    }

    private fun runContinuousSession(request: PingRequest, session: OperationSession): Flow<PingPacketResult> = flow {
        val candidates = configuredEngines
        if (candidates.none { it.isAvailable }) {
            emit(errorPacket(request, "No ping engine is available"))
            return@flow
        }

        var selectedEngine: PingEngine? = null
        var sequence = 1
        while (true) {
            val resolvedIp = try {
                request.resolvedIp ?: PingBlockingCallExecutor.run(session) { resolver.resolve(request.host) }
            } catch (e: UnknownHostException) {
                emit(errorPacket(request, e.message ?: "Unknown host: ${request.host}", sequence))
                sequence++
                delay(request.intervalMs.toLong().coerceAtLeast(0L))
                continue
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emit(errorPacket(request, e.message ?: e.javaClass.simpleName, sequence))
                sequence++
                delay(request.intervalMs.toLong().coerceAtLeast(0L))
                continue
            }

            val probeRequest = request.copy(resolvedIp = resolvedIp, count = 1)
            // Availability can change after an engine reports a native linkage failure.
            // Recheck every probe so a continuous session does not keep retrying an
            // engine that already declared itself unavailable.
            val availableCandidates = candidates.filter { it.isAvailable }
            val enginesToTry = buildList {
                selectedEngine?.takeIf { it.isAvailable }?.let(::add)
                availableCandidates.filter { it != selectedEngine }.forEach(::add)
            }
            var selectedPacket: PingPacketResult? = null
            var engineForPacket: PingEngine? = null
            val failureMessages = mutableListOf<String>()

            for (engine in enginesToTry) {
                var usablePacket: PingPacketResult? = null
                try {
                    engine.ping(probeRequest, session).collect { packet ->
                        if (packet.status == PingStatus.ERROR && usablePacket == null) {
                            packet.errorMessage?.takeIf { it.isNotBlank() }?.let(failureMessages::add)
                            throw EngineUnavailableException(packet.errorMessage)
                        }
                        usablePacket = packet
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (e !is EngineUnavailableException) {
                        e.message?.takeIf { it.isNotBlank() }?.let(failureMessages::add)
                    }
                    if (usablePacket != null) {
                        selectedPacket = errorPacket(probeRequest, e.message ?: e.javaClass.simpleName)
                        engineForPacket = engine
                    }
                }
                if (usablePacket != null) {
                    selectedPacket = usablePacket
                    engineForPacket = engine
                    selectedEngine = engine
                    _lastEngineUsed.value = engine.kind
                    break
                }
            }

            emit(
                (selectedPacket ?: errorPacket(
                    probeRequest,
                    allEnginesFailedMessage(failureMessages)
                )).copy(
                    sequence = sequence,
                    engine = engineForPacket?.kind
                )
            )
            sequence++
            delay(request.intervalMs.toLong().coerceAtLeast(0L))
        }
    }

    private fun errorPacket(request: PingRequest, message: String, sequence: Int = 1) = PingPacketResult(
        sequence = sequence,
        host = request.host,
        rtTimeMs = null,
        status = PingStatus.ERROR,
        errorMessage = message,
        fromIp = request.resolvedIp
    )

    private fun allEnginesFailedMessage(failureMessages: List<String>): String =
        failureMessages.distinct().takeIf { it.isNotEmpty() }?.let {
            "All ping engines failed: ${it.joinToString("; ")}"
        } ?: "All ping engines failed before producing a result"

    private class EngineUnavailableException(message: String?) : Exception(message)
}
