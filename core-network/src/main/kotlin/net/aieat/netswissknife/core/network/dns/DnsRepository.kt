package net.aieat.netswissknife.core.network.dns

import net.aieat.netswissknife.core.network.NetworkResult

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
}
