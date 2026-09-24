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
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Functional type for a single TCP port probe. Injected for testability. */
typealias PortConnectChecker = (address: InetAddress, port: Int) -> PortConnectResult

/** Raw result of a single TCP connection attempt. */
data class PortConnectResult(
    val status: PortStatus,
    val responseTimeMs: Long,
    val banner: String?
)

/**
 * Production [PortScanRepository] that uses TCP socket connections to determine port status.
 *
 * Up to [concurrency] ports are tested simultaneously using a bounded worker
 * pool; each progress event is emitted as its probe completes.
 * For each open port, a brief banner read is attempted on well-known service ports.
 *
 * @param checker  Functional hook for the TCP probe. Pass null to use the real socket
 *                 implementation, which honours the [scan] `timeoutMs` parameter.
 * @param operationTimeoutMillis Maximum duration of one interactive scan, including resolution
 *                                and result collection. The default is the shared 120-second cap.
 */
class PortScanRepositoryImpl(
    private val checker: PortConnectChecker? = null,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val hostResolver: (String) -> InetAddress = InetAddress::getByName,
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> Socket = { Socket() },
    private val operationTimeoutMillis: Long = OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS,
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

                // Attempt banner grab for open port (short read)
                val banner: String? = try {
                    socket.soTimeout = 300
                    val inputStream = socket.getInputStream()
                    val bytes = ByteArray(256)
                    val read = inputStream.read(bytes)
                    if (read > 0) BannerSanitizer.sanitize(String(bytes, 0, read)) else null
                } catch (error: SecurityException) {
                    throw LocalNetworkPermissionDeniedException(error)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { null }

                PortConnectResult(PortStatus.OPEN, responseTime, banner)
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

    override fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        operationSession: OperationSession,
    ): Flow<PortScanUpdate> = scanInternal(host, ports, timeoutMs, concurrency, operationSession)

    private fun scanInternal(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        callerSession: OperationSession?,
    ): Flow<PortScanUpdate> = channelFlow {
        val startTime = clock.nowNanos()
        val results = mutableListOf<PortScanResult>()
        val requestedConcurrency = concurrency.coerceIn(1, 500)
        val session = callerSession ?: OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = operationTimeoutMillis,
                maxConcurrentProbes = requestedConcurrency,
                clock = clock,
            )
        )
        val effectiveConcurrency = requestedConcurrency.coerceAtMost(session.budget.maxConcurrentProbes)
        var completedSummary: PortScanSummary? = null

        OperationRunner.run(session) {
            ensureOperationActive()
            val resolvedAddress = try {
                hostResolver(host)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                        )
                        for (port in pending) {
                            ensureOperationActive()
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
                            val portInfo = WellKnownPorts.getInfo(port)
                            completed.send(
                                PortScanResult(
                                    port = port,
                                    status = connectResult.status,
                                    serviceName = portInfo?.serviceName ?: WellKnownPorts.getServiceName(port),
                                    serviceDescription = portInfo?.description,
                                    banner = connectResult.banner,
                                    responseTimeMs = connectResult.responseTimeMs
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
