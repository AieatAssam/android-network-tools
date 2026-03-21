package com.example.netswissknife.core.network.dns

/**
 * The result of a DNS lookup operation.
 *
 * @param domain        The domain that was queried
 * @param recordType    The DNS record type that was requested
 * @param server        The DNS server that was used
 * @param records       The list of returned records (may be empty if NXDOMAIN / no records)
 * @param queryTimeMs   Round-trip query time in milliseconds
 * @param rawResponse   Full raw DNS response message for the "raw view"
 */
data class DnsResult(
    val domain: String,
    val recordType: DnsRecordType,
    val server: DnsServer,
    val records: List<DnsRecord>,
    val queryTimeMs: Long,
    val rawResponse: String
)
