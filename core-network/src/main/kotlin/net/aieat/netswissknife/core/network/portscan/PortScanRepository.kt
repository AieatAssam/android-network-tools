package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.flow.Flow

/**
 * Contract for a port scanner.
 *
 * Implementations emit one [PortScanUpdate] per scanned port, then a final
 * [PortScanUpdate.Complete] when all ports have been tested.
 */
interface PortScanRepository {

    /**
     * Scan the given [ports] on [host] and emit progress updates as a cold [Flow].
     *
     * @param host        Hostname or IP address to scan.
     * @param ports       Ordered list of port numbers to scan.
     * @param timeoutMs   Per-port TCP connection timeout in milliseconds.
     * @param concurrency Maximum number of ports probed simultaneously.
     */
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int
    ): Flow<PortScanUpdate>
}

/** Progress events emitted during a scan. */
sealed interface PortScanUpdate {
    /** Emitted after each port is probed. */
    data class PortResult(
        val result: PortScanResult,
        val scannedCount: Int,
        val totalCount: Int
    ) : PortScanUpdate

    /** Emitted once when all ports have been scanned. */
    data class Complete(val summary: PortScanSummary) : PortScanUpdate
}
