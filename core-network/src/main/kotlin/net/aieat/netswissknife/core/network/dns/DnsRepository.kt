package net.aieat.netswissknife.core.network.dns

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement

/**
 * Contract for DNS lookup operations.
 * Implementations query the given DNS server for records of the specified type.
 */
interface DnsRepository {
    /**
     * Performs a DNS lookup for [domain] requesting records of [recordType] from [server].
     *
     * Returns [NetworkResult.Success] with a [DnsResult] (which may have an empty [DnsResult.records]
     * list if the domain has no records of that type), or [NetworkResult.Error] on network/timeout failure.
     */
    suspend fun lookup(
        domain: String,
        recordType: DnsRecordType,
        server: DnsServer
    ): NetworkResult<DnsResult>

    /** Caller-owned operation variant; legacy implementations remain source compatible. */
    suspend fun lookup(
        domain: String,
        recordType: DnsRecordType,
        server: DnsServer,
        operationSession: OperationSession,
    ): NetworkResult<DnsResult> = lookup(domain, recordType, server)
}

/** Shared single-query deadline used by the DNS UI and legacy repository entry point. */
object DnsLookupOperation {
    const val TIMEOUT_MILLIS = 8_000L

    fun newSession(clock: MonotonicClock = SystemMonotonicClock): OperationSession =
        OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = TIMEOUT_MILLIS,
                maxConcurrentProbes = 1,
                clock = clock,
            )
        )
}
