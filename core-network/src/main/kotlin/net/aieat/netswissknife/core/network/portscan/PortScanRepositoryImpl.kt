package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince

/** Functional type for a single TCP port probe. Injected for testability. */
typealias PortConnectChecker = (host: String, port: Int) -> PortConnectResult

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
 */
class PortScanRepositoryImpl(
    private val checker: PortConnectChecker? = null,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : PortScanRepository {

    companion object {
        /**
         * Returns a TCP checker that uses [timeoutMs] for the connection timeout.
         */
        fun defaultChecker(
            timeoutMs: Int,
            clock: MonotonicClock = SystemMonotonicClock,
        ): PortConnectChecker = { host, port ->
            val start = clock.nowNanos()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val responseTime = clock.elapsedMillisSince(start)

                // Attempt banner grab for open port (short read)
                val banner: String? = try {
                    socket.soTimeout = 300
                    val inputStream = socket.getInputStream()
                    val bytes = ByteArray(256)
                    val read = inputStream.read(bytes)
                    if (read > 0) BannerSanitizer.sanitize(String(bytes, 0, read)) else null
                } catch (_: Exception) { null }

                PortConnectResult(PortStatus.OPEN, responseTime, banner)
            } catch (e: ConnectException) {
                PortConnectResult(PortStatus.CLOSED, clock.elapsedMillisSince(start), null)
            } catch (e: SocketTimeoutException) {
                PortConnectResult(PortStatus.FILTERED, clock.elapsedMillisSince(start), null)
            } catch (_: Exception) {
                PortConnectResult(PortStatus.FILTERED, clock.elapsedMillisSince(start), null)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    override fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int
    ): Flow<PortScanUpdate> = flow {
        val effectiveChecker = checker ?: defaultChecker(timeoutMs, clock)
        val startTime = clock.nowNanos()
        val results = mutableListOf<PortScanResult>()

        // Resolve host IP once for the summary
        val resolvedIp: String? = try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) { null }

        val effectiveConcurrency = concurrency.coerceIn(1, 500)

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
                    for (port in ports) pending.send(port)
                } finally {
                    pending.close()
                }
            }
            val workerCount = minOf(effectiveConcurrency, ports.size)
            val workers = List(workerCount) {
                launch(Dispatchers.IO) {
                    for (port in pending) {
                        val connectResult = effectiveChecker(host, port)
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
                results += portResult
                scannedCount++
                emit(
                    PortScanUpdate.PortResult(
                        result = portResult,
                        scannedCount = scannedCount,
                        totalCount = ports.size
                    )
                )
            }
        }

        // Build and emit summary
        val summary = PortScanSummary(
            host = host,
            resolvedIp = resolvedIp,
            scannedPorts = ports,
            openPorts = results.count { it.status == PortStatus.OPEN },
            closedPorts = results.count { it.status == PortStatus.CLOSED },
            filteredPorts = results.count { it.status == PortStatus.FILTERED },
            scanDurationMs = clock.elapsedMillisSince(startTime),
            results = results.sortedBy { it.port }
        )
        emit(PortScanUpdate.Complete(summary))
    }.flowOn(Dispatchers.IO)
}
