package net.aieat.netswissknife.core.network.mdns

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

data class DiscoveredService(
    val serviceType: String,
    val instanceName: String,
    val displayName: String,
    val hostname: String,
    val port: Int,
    val ipAddresses: List<String> = emptyList(),
    val txtRecords: Map<String, String> = emptyMap()
)

sealed class MdnsUpdate {
    data class ServiceFound(val service: DiscoveredService) : MdnsUpdate()
    data class DiscoveryComplete(
        val totalFound: Int,
        val truncationReasons: Set<MdnsTruncationReason> = emptySet(),
    ) : MdnsUpdate()
}

interface MdnsRepository {
    fun discover(timeoutMs: Long = 5_000L): Flow<MdnsUpdate>

    /** Collects one scan under a caller-owned operation session. */
    fun discover(timeoutMs: Long, operationSession: OperationSession): Flow<MdnsUpdate> =
        discover(timeoutMs)
}

/** Shared mDNS operation limits and per-scan session factory. */
object MdnsOperation {
    const val MAX_SCAN_DURATION_MILLIS = OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS
    const val RECEIVE_BUFFER_SIZE_BYTES = 65_536

    fun newSession(clock: MonotonicClock = SystemMonotonicClock): OperationSession =
        newSession(MAX_SCAN_DURATION_MILLIS, clock)

    /** The caller timeout is clamped to the same 120s safety ceiling as the operation budget. */
    fun newSession(timeoutMs: Long, clock: MonotonicClock = SystemMonotonicClock): OperationSession =
        OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                timeoutMillis = clampScanDuration(timeoutMs),
                maxConcurrentProbes = 1,
                maxResponseBytes = RECEIVE_BUFFER_SIZE_BYTES.toLong(),
                clock = clock,
            )
        )

    fun clampScanDuration(timeoutMs: Long): Long =
        timeoutMs.coerceIn(1L, MAX_SCAN_DURATION_MILLIS)

    /** Rejects caller timeouts outside the published operation window. */
    fun requireValidScanDuration(timeoutMs: Long): Long {
        require(timeoutMs in 1L..MAX_SCAN_DURATION_MILLIS) {
            "mDNS scan duration must be between 1 and $MAX_SCAN_DURATION_MILLIS ms"
        }
        return timeoutMs
    }
}
