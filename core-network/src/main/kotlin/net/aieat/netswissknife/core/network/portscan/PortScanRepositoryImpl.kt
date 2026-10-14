package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newTcpSocket
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.tls.SocketTlsHandshakeEngine
import net.aieat.netswissknife.core.network.tls.TlsCertificateParser
import net.aieat.netswissknife.core.network.tls.TlsHandshakeEngine
import net.aieat.netswissknife.core.network.tls.TlsHandshakeConnection
import net.aieat.netswissknife.core.network.tls.TlsInspectorSocketFactory
import javax.net.ssl.SSLSocket

/** Functional type for a single TCP port probe. Injected for testability. */
typealias PortConnectChecker = (address: InetAddress, port: Int) -> PortConnectResult

/** Raw result of a single TCP connection attempt. */
data class PortConnectResult(
    val status: PortStatus,
    val responseTimeMs: Long,
    val banner: String?,
    val bannerTruncated: Boolean = false,
    val tlsSubject: String? = null,
    val probeKind: ProbeKind = ProbeKind.PASSIVE,
)

fun interface TlsSubjectProbe {
    fun inspect(
        connectAddress: InetAddress,
        peerHost: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): String?
}

/** A short certificate peek that shares operation cancellation and deadline ownership. */
internal class P10TlsSubjectProbe(
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> Socket = { Socket() },
    private val engine: TlsHandshakeEngine = SocketTlsHandshakeEngine(
        TlsInspectorSocketFactory { context -> context.socketFactory.createSocket() as SSLSocket },
    ),
) : TlsSubjectProbe {
    override fun inspect(
        connectAddress: InetAddress,
        peerHost: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): String? {
        if (timeoutMs < 2) return null
        // P10 applies this timeout to both connect and handshake independently. Split
        // the remaining aggregate budget across those two blocking phases.
        val phaseTimeoutMs = minOf(timeoutMs, MAX_TLS_PEEK_MILLIS) / 2
        val rawSocket = try {
            binder.newTcpSocket(connectAddress.hostAddress) { socketFactory() }
        } catch (denied: LocalNetworkPermissionDeniedException) {
            throw denied
        } catch (security: SecurityException) {
            throw LocalNetworkPermissionDeniedException(security)
        }
        operationSession.resources.register(rawSocket)
        var connection: TlsHandshakeConnection? = null
        try {
            operationSession.budget.throwIfExpired()
            try {
                rawSocket.connect(InetSocketAddress(connectAddress, port), phaseTimeoutMs)
            } catch (security: SecurityException) {
                throw LocalNetworkPermissionDeniedException(security)
            }
            operationSession.budget.throwIfExpired()
            connection = engine.openConnectionOverSocket(peerHost, port, phaseTimeoutMs, rawSocket)
            operationSession.resources.register(connection)
            connection.connect()
            operationSession.budget.throwIfExpired()
            connection.handshake()
            operationSession.budget.throwIfExpired()
            return connection.snapshot().peerCertificates.firstOrNull()?.let { certificate ->
                TlsCertificateParser.parseCN(certificate.subjectX500Principal.name)
                    .let(TlsSubjectSanitizer::sanitize)
                    .takeIf(String::isNotBlank)
            }
        } finally {
            connection?.let { tls ->
                if (operationSession.resources.release(tls)) runCatching { tls.close() }
            }
            if (operationSession.resources.release(rawSocket)) runCatching { rawSocket.close() }
        }
    }

    private companion object { const val MAX_TLS_PEEK_MILLIS = 2_000 }
}

/**
 * Production [PortScanRepository] that uses TCP socket connections to determine port status.
 *
 * Up to [concurrency] ports are tested simultaneously using a bounded worker
 * pool; each progress event is emitted as its probe completes.
 * For each open port, a brief banner read is attempted on well-known service ports.
 *
 * @param checker  Functional hook for the TCP probe. Pass null to use the real socket
 *                 implementation, which honours the [scan] `timeoutMs` parameter.
 * @param operationTimeoutMillis Maximum duration for the legacy concurrency-only session API.
 *                                Request-aware scan overloads derive their deadline from work size.
 */
class PortScanRepositoryImpl(
    private val checker: PortConnectChecker? = null,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val hostResolver: (String) -> InetAddress = InetAddress::getByName,
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> Socket = { Socket() },
    private val operationTimeoutMillis: Long = OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS,
    private val resolverExecutor: java.util.concurrent.ThreadPoolExecutor = PortScanBlockingResolver.productionExecutor,
    private val tlsSubjectProbe: TlsSubjectProbe = if (checker == null) {
        P10TlsSubjectProbe(binder, socketFactory)
    } else {
        TlsSubjectProbe { _, _, _, _, _ -> null }
    },
) : PortScanRepository {

    companion object {
        /**
         * Returns a TCP checker that uses [timeoutMs] for the connection timeout.
         */
        private fun scopedDefaultChecker(
            timeoutMs: Int,
            clock: MonotonicClock,
            binder: NetworkBinder,
            socketFactory: () -> Socket,
            activeSocket: ActivePortScanSocket,
            host: String,
            aggressiveProbes: Boolean,
        ): PortConnectChecker = { address, port ->
            val start = clock.nowNanos()
            var socket: Socket? = null
            try {
                socket = binder.newTcpSocket(address.hostAddress) {
                    socketFactory().also { created ->
                        if (!activeSocket.attach(created)) {
                            throw CancellationException("Port scan stopped")
                        }
                    }
                }
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                val responseTime = clock.elapsedMillisSince(start)

                // A banner read shares the configured per-port timeout with connect.
                // Convert the precise remaining budget to whole milliseconds without
                // rounding up, since Socket.soTimeout cannot express sub-ms timeouts.
                val elapsedNanos = (clock.nowNanos() - start).coerceAtLeast(0L)
                val remainingNanos = timeoutMs.toLong() * NANOS_PER_MILLISECOND - elapsedNanos
                val bannerTimeoutMs = minOf(
                    PortScanOperationBudget.MAX_BANNER_READ_TIMEOUT_MILLIS,
                    remainingNanos.coerceAtLeast(0L) / NANOS_PER_MILLISECOND,
                ).toInt()

                val probeKind = if (aggressiveProbes) ServiceProbes.kindFor(port) else ProbeKind.PASSIVE
                // Optional exchanges happen only after TCP accepts the connection. Their
                // reads share the existing per-port deadline and a 300 ms banner cap.
                val bannerRead = try {
                    if (bannerTimeoutMs <= 0) {
                        BannerReadResult(banner = null, truncated = false)
                    } else {
                        val input = socket.getInputStream()
                        val activeProbe = aggressiveProbes && probeKind in setOf(ProbeKind.HTTP, ProbeKind.SMTP)
                        if (activeProbe && probeKind == ProbeKind.HTTP) {
                            val output = socket.getOutputStream()
                            output.write(checkNotNull(ServiceProbes.request(port, host)))
                            output.flush()
                        }
                        fun prepareRead(): Boolean {
                            // SO_TIMEOUT applies to each individual read. Recompute it
                            // before every partial read so a slow banner cannot spend
                            // the full cap repeatedly and overrun the per-port budget.
                            val elapsedNanos = (clock.nowNanos() - start).coerceAtLeast(0L)
                            val remainingMillis = (
                                timeoutMs.toLong() * NANOS_PER_MILLISECOND - elapsedNanos
                            ).coerceAtLeast(0L) / NANOS_PER_MILLISECOND
                            val nextReadTimeoutMs = minOf(
                                PortScanOperationBudget.MAX_BANNER_READ_TIMEOUT_MILLIS,
                                remainingMillis,
                            ).toInt()
                            return if (nextReadTimeoutMs <= 0) {
                                false
                            } else {
                                socket.soTimeout = nextReadTimeoutMs
                                true
                            }
                        }
                        if (activeProbe && probeKind == ProbeKind.SMTP) {
                            val statusLine = Regex("^(\\d{3})([ -])")
                            var smtpReplyCode: String? = null
                            var finalSmtpReplyCode: String? = null
                            val greeting = BannerReader.read(
                                input,
                                stopWhenLine = { line ->
                                    val match = statusLine.find(line)
                                    if (match == null) {
                                        true
                                    } else {
                                        val code = match.groupValues[1]
                                        val separator = match.groupValues[2]
                                        val firstCode = smtpReplyCode
                                        if (firstCode == null) {
                                            smtpReplyCode = code
                                            if (separator != "-") finalSmtpReplyCode = code
                                            separator != "-"
                                        } else if (code != firstCode) {
                                            finalSmtpReplyCode = code
                                            true
                                        } else if (separator == " ") {
                                            finalSmtpReplyCode = code
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                },
                                lineSeparator = " ",
                                prepareRead = ::prepareRead,
                            )
                            val isComplete220Greeting = greeting.stopConditionMet &&
                                smtpReplyCode == "220" && finalSmtpReplyCode == "220"
                            val bytesRemaining = BannerReader.MAX_BYTES - greeting.bytesRead
                            if (isComplete220Greeting && bytesRemaining > 0 && prepareRead()) {
                                val output = socket.getOutputStream()
                                output.write(checkNotNull(ServiceProbes.request(port, host)))
                                output.flush()
                                val reply = BannerReader.read(
                                    input,
                                    maxBytes = bytesRemaining,
                                    prepareRead = ::prepareRead,
                                )
                                val combinedBanner = listOfNotNull(greeting.banner, reply.banner).joinToString(" ")
                                val sanitizedCombined = BannerSanitizer.sanitizeWithTruncation(combinedBanner)
                                BannerReadResult(
                                    banner = sanitizedCombined.text.takeIf(String::isNotBlank),
                                    truncated = greeting.truncated || reply.truncated || sanitizedCombined.truncated,
                                    bytesRead = greeting.bytesRead + reply.bytesRead,
                                )
                            } else greeting
                        } else {
                            BannerReader.read(input, prepareRead = ::prepareRead)
                        }
                    }
                } catch (error: SecurityException) {
                    throw LocalNetworkPermissionDeniedException(error)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { BannerReadResult(banner = null, truncated = false) }

                PortConnectResult(
                    status = PortStatus.OPEN,
                    responseTimeMs = responseTime,
                    banner = bannerRead.banner,
                    bannerTruncated = bannerRead.truncated,
                    probeKind = if (aggressiveProbes) ServiceProbes.kindFor(port) else ProbeKind.PASSIVE,
                )
            } catch (e: ConnectException) {
                PortConnectResult(PortStatus.CLOSED, clock.elapsedMillisSince(start), null)
            } catch (e: SocketTimeoutException) {
                PortConnectResult(PortStatus.FILTERED, clock.elapsedMillisSince(start), null)
            } catch (e: LocalNetworkPermissionDeniedException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                throw LocalNetworkPermissionDeniedException(e)
            } catch (_: Exception) {
                PortConnectResult(PortStatus.FILTERED, clock.elapsedMillisSince(start), null)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                socket?.let(activeSocket::detach)
            }
        }

        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val MAX_TLS_PEEK_TIMEOUT_MILLIS = 2_000L
    }

    override fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int
    ): Flow<PortScanUpdate> = scanInternal(host, ports, timeoutMs, concurrency, null)

    override fun newSession(
        concurrency: Int,
        clock: MonotonicClock,
    ): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = operationTimeoutMillis,
            maxConcurrentProbes = concurrency.coerceIn(1, 500),
            clock = clock,
        )
    )

    override fun newSession(
        portCount: Int,
        timeoutMs: Int,
        concurrency: Int,
        clock: MonotonicClock,
    ): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = PortScanOperationBudget.sessionTimeoutMillis(portCount, timeoutMs, concurrency),
            maxConcurrentProbes = concurrency.coerceIn(1, PortScanOperationBudget.MAX_CONCURRENCY),
            clock = clock,
        )
    )

    override fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        operationSession: OperationSession,
    ): Flow<PortScanUpdate> = scanInternal(host, ports, timeoutMs, concurrency, operationSession)

    override fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        aggressiveProbes: Boolean,
        operationSession: OperationSession,
    ): Flow<PortScanUpdate> = scanInternal(host, ports, timeoutMs, concurrency, operationSession, aggressiveProbes)

    private fun scanInternal(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        callerSession: OperationSession?,
        aggressiveProbes: Boolean = false,
    ): Flow<PortScanUpdate> = channelFlow {
        require(timeoutMs > 0) { "Per-port timeout must be positive" }
        val startTime = clock.nowNanos()
        val results = mutableListOf<PortScanResult>()
        val requestedConcurrency = concurrency.coerceIn(1, 500)
        val estimate = PortScanOperationBudget.requireWithinCeiling(
            portCount = ports.size.coerceAtLeast(1),
            timeoutMs = timeoutMs.coerceAtLeast(1),
            requestedConcurrency = requestedConcurrency,
            sessionConcurrency = callerSession?.budget?.maxConcurrentProbes ?: requestedConcurrency,
        )
        val session = callerSession ?: OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = estimate.timeoutMillis,
                maxConcurrentProbes = requestedConcurrency,
                clock = clock,
            )
        )
        val effectiveConcurrency = requestedConcurrency.coerceAtMost(session.budget.maxConcurrentProbes)
        var completedSummary: PortScanSummary? = null

        OperationRunner.run(session) {
            ensureOperationActive()
            val resolvedAddress = try {
                PortScanBlockingResolver.resolve(session, resolverExecutor) { hostResolver(host) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (deadline: OperationDeadlineExceededException) {
                throw deadline
            } catch (timeout: PortScanHostResolutionTimeoutException) {
                throw timeout
            } catch (error: Exception) {
                throw PortScanHostResolutionException(host, error)
            }
            ensureOperationActive()
            val resolvedIp = resolvedAddress.hostAddress

            // A bounded work queue keeps very large scans from launching one
            // coroutine per port, while the bounded result queue provides
            // backpressure if a collector is slower than the probes. Results are
            // consumed in channel-send order, i.e. in completion order rather than
            // the order in which ports were supplied.
            coroutineScope {
                val pending = Channel<Int>(capacity = effectiveConcurrency)
                val completed = Channel<PortScanResult>(capacity = effectiveConcurrency)
                val producer = launch {
                    try {
                        for (port in ports) {
                            ensureOperationActive()
                            pending.send(port)
                        }
                    } finally {
                        pending.close()
                    }
                }

                val workerCount = minOf(effectiveConcurrency, ports.size)
                val socketSlots = List(workerCount) {
                    resources.register(ActivePortScanSocket())
                }
                // Publish Started only after operation-owned worker slots are registered. A
                // first()-only collector may cancel as soon as it sees this event.
                send(PortScanUpdate.Started(resolvedIp = resolvedIp, totalCount = ports.size))
                val workers = socketSlots.map { socketSlot ->
                    launch(Dispatchers.IO) {
                        val effectiveChecker = checker ?: scopedDefaultChecker(
                            timeoutMs = timeoutMs,
                            clock = clock,
                            binder = binder,
                            socketFactory = socketFactory,
                            activeSocket = socketSlot,
                            host = host,
                            aggressiveProbes = aggressiveProbes,
                        )
                        for (port in pending) {
                            ensureOperationActive()
                            val probeStartedAt = clock.nowNanos()
                            val connectResult = try {
                                effectiveChecker(resolvedAddress, port)
                            } catch (cancelled: CancellationException) {
                                // A checker-local cancellation does not cancel its parent Job.
                                // Fail the operation explicitly instead of silently losing a worker.
                                ensureOperationActive()
                                throw IllegalStateException(
                                    "Port checker cancelled outside scan cancellation",
                                    cancelled,
                                )
                            } catch (failure: Exception) {
                                ensureOperationActive()
                                throw failure
                            }
                            // A close during connect/read can look like a normal filtered
                            // result; cancellation/deadline must win before result mapping.
                            ensureOperationActive()
                            val probeKind = if (aggressiveProbes && connectResult.status == PortStatus.OPEN) {
                                ServiceProbes.kindFor(port)
                            } else ProbeKind.PASSIVE
                            val tlsSubject = if (probeKind == ProbeKind.TLS_PEEK) {
                                val elapsedNanos = (clock.nowNanos() - probeStartedAt).coerceAtLeast(0L)
                                val perPortRemainingNanos = (
                                    timeoutMs.toLong() * NANOS_PER_MILLISECOND - elapsedNanos
                                ).coerceAtLeast(0L)
                                val peekBudgetNanos = minOf(
                                    MAX_TLS_PEEK_TIMEOUT_MILLIS * NANOS_PER_MILLISECOND,
                                    perPortRemainingNanos,
                                    session.budget.remainingNanos(),
                                )
                                val peekTimeout = (peekBudgetNanos / NANOS_PER_MILLISECOND).toInt()
                                if (peekTimeout > 0) try {
                                    tlsSubjectProbe.inspect(resolvedAddress, host, port, peekTimeout, session)
                                } catch (denied: LocalNetworkPermissionDeniedException) {
                                    throw denied
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (security: SecurityException) {
                                    throw LocalNetworkPermissionDeniedException(security)
                                } catch (_: Exception) { null } else null
                            } else null
                            ensureOperationActive()
                            val portInfo = WellKnownPorts.getInfo(port)
                            completed.send(
                                PortScanResult(
                                    port = port,
                                    status = connectResult.status,
                                    serviceName = portInfo?.serviceName ?: WellKnownPorts.getServiceName(port),
                                    serviceDescription = portInfo?.description,
                                    banner = connectResult.banner,
                                    responseTimeMs = connectResult.responseTimeMs,
                                    bannerTruncated = connectResult.bannerTruncated,
                                    tlsSubject = (tlsSubject ?: connectResult.tlsSubject)
                                        ?.let(TlsSubjectSanitizer::sanitize)
                                        ?.takeIf(String::isNotBlank),
                                    probeKind = connectResult.probeKind.takeIf { it != ProbeKind.PASSIVE } ?: probeKind,
                                )
                            )
                        }
                    }
                }
                launch {
                    producer.join()
                    workers.joinAll()
                    completed.close()
                }

                var scannedCount = 0
                for (portResult in completed) {
                    ensureOperationActive()
                    results += portResult
                    scannedCount++
                    send(
                        PortScanUpdate.PortResult(
                            result = portResult,
                            scannedCount = scannedCount,
                            totalCount = ports.size
                        )
                    )
                }
            }

            ensureOperationActive()
            completedSummary = PortScanSummary(
                host = host,
                resolvedIp = resolvedIp,
                scannedPorts = ports,
                openPorts = results.count { it.status == PortStatus.OPEN },
                closedPorts = results.count { it.status == PortStatus.CLOSED },
                filteredPorts = results.count { it.status == PortStatus.FILTERED },
                scanDurationMs = clock.elapsedMillisSince(startTime),
                results = results.sortedBy { it.port }
            )
        }
        // The terminal update is deliberately sent only after OperationRunner closes the scope.
        send(PortScanUpdate.Complete(checkNotNull(completedSummary)))
    }.flowOn(Dispatchers.IO)
}

/** One worker's currently blocking socket; closing the operation lease interrupts it. */
private class ActivePortScanSocket : AutoCloseable {
    private val lock = Any()
    private var socket: Socket? = null
    private var closed = false

    fun attach(candidate: Socket): Boolean {
        val attached = synchronized(lock) {
            if (closed) false else {
                socket = candidate
                true
            }
        }
        if (!attached) {
            try {
                candidate.close()
            } catch (_: Exception) {
                // The operation scope is already closing; continue unwinding cancellation.
            }
        }
        return attached
    }

    fun detach(candidate: Socket) {
        synchronized(lock) {
            if (socket === candidate) socket = null
        }
    }

    override fun close() {
        val activeSocket = synchronized(lock) {
            closed = true
            socket.also { socket = null }
        }
        activeSocket?.close()
    }
}

class PortScanHostResolutionException(host: String, cause: Throwable) :
    IOException("Could not resolve port scan target '$host'", cause)
