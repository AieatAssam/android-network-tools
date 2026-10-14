package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

/**
 * Contract for a port scanner.
 *
 * Implementations emit one [PortScanUpdate] per scanned port, then a final
 * a [PortScanUpdate.Started] event once the target is resolved, one
 * [PortScanUpdate.PortResult] per scanned port, then a [PortScanUpdate.Complete]
 * when all ports have been tested.
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

    /** Optional protocol-aware scan. Implementations may keep legacy passive behavior. */
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        aggressiveProbes: Boolean,
        operationSession: OperationSession,
    ): Flow<PortScanUpdate> = scan(host, ports, timeoutMs, concurrency, operationSession)

    /** Creates a caller-owned session for one scan. */
    fun newSession(
        concurrency: Int,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            maxConcurrentProbes = concurrency.coerceIn(1, 500),
            clock = clock,
        )
    )

    /** Request-sized session for callers that know the scan work before starting it. */
    fun newSession(
        portCount: Int,
        timeoutMs: Int,
        concurrency: Int,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = PortScanOperationBudget.sessionTimeoutMillis(portCount, timeoutMs, concurrency),
            maxConcurrentProbes = concurrency.coerceIn(1, PortScanOperationBudget.MAX_CONCURRENCY),
            clock = clock,
        )
    )

    /**
     * Caller-owned variant. The source-compatible default delegates to the legacy method and
     * cannot enforce the session; production repositories that own resources must override it.
     */
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        operationSession: OperationSession,
    ): Flow<PortScanUpdate> = scan(host, ports, timeoutMs, concurrency)
}

/** Progress events emitted during a scan. */
sealed interface PortScanUpdate {
    /** Emitted after target resolution and before any port probes begin. */
    data class Started(
        val resolvedIp: String,
        val totalCount: Int
    ) : PortScanUpdate

    /** Emitted after each port is probed. */
    data class PortResult(
        val result: PortScanResult,
        val scannedCount: Int,
        val totalCount: Int
    ) : PortScanUpdate

    /** Emitted once when all ports have been scanned. */
    data class Complete(val summary: PortScanSummary) : PortScanUpdate
}
