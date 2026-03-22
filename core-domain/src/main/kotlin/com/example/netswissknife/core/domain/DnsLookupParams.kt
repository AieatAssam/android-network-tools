package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.dns.DnsRecordType
import com.example.netswissknife.core.network.dns.DnsServer

/**
 * Input parameters for a DNS lookup use case.
 *
 * @param domain     The domain name or IP address to query (e.g. "example.com", "8.8.8.8")
 * @param recordType The DNS record type to request
 * @param server     The DNS resolver to use
 */
data class DnsLookupParams(
    val domain: String,
    val recordType: DnsRecordType = DnsRecordType.A,
    val server: DnsServer = DnsServer.System()
)
