package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

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
 * Ports are tested in batches of [concurrency] simultaneously using coroutines.
 * For each open port, a brief banner read is attempted on well-known service ports.
 *
 * @param checker  Functional hook for the TCP probe. Pass null to use the real socket
 *                 implementation, which honours the [scan] `timeoutMs` parameter.
 */
class PortScanRepositoryImpl(
    private val checker: PortConnectChecker? = null,
) : PortScanRepository {

    companion object {
        /**
         * Returns a TCP checker that uses [timeoutMs] for the connection timeout.
         */
        fun defaultChecker(timeoutMs: Int): PortConnectChecker = { host, port ->
            val start = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val responseTime = System.currentTimeMillis() - start

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
                PortConnectResult(PortStatus.CLOSED, System.currentTimeMillis() - start, null)
            } catch (e: SocketTimeoutException) {
                PortConnectResult(PortStatus.FILTERED, System.currentTimeMillis() - start, null)
            } catch (_: Exception) {
                PortConnectResult(PortStatus.FILTERED, System.currentTimeMillis() - start, null)
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
        val effectiveChecker = checker ?: defaultChecker(timeoutMs)
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<PortScanResult>()
        var scannedCount = 0
        val mutex = Mutex()

        // Resolve host IP once for the summary
        val resolvedIp: String? = try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) { null }

        val effectiveConcurrency = concurrency.coerceAtLeast(1).coerceAtMost(500)
        val portChunks = ports.chunked(effectiveConcurrency)

        for (chunk in portChunks) {
            coroutineScope {
                val deferreds = chunk.map { port ->
                    async(Dispatchers.IO) {
                        val connectResult = effectiveChecker(host, port)
                        val portInfo = WellKnownPorts.getInfo(port)
                        PortScanResult(
                            port = port,
                            status = connectResult.status,
                            serviceName = portInfo?.serviceName ?: WellKnownPorts.getServiceName(port),
                            serviceDescription = portInfo?.description,
                            banner = connectResult.banner,
                            responseTimeMs = connectResult.responseTimeMs
                        )
                    }
                }

                // Collect results as they complete and emit updates
                for (deferred in deferreds) {
                    val portResult = deferred.await()
                    val currentCount: Int
                    mutex.withLock {
                        results.add(portResult)
                        scannedCount++
                        currentCount = scannedCount
                    }
                    emit(
                        PortScanUpdate.PortResult(
                            result = portResult,
                            scannedCount = currentCount,
                            totalCount = ports.size
                        )
                    )
                }
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
            scanDurationMs = System.currentTimeMillis() - startTime,
            results = results.sortedBy { it.port }
        )
        emit(PortScanUpdate.Complete(summary))
    }.flowOn(Dispatchers.IO)
}
