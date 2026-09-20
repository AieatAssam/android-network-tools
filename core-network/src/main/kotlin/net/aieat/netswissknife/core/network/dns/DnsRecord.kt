package net.aieat.netswissknife.core.network.dns

/**
 * A single DNS resource record returned from a lookup.
 *
 * @param type      The record type (A, MX, TXT, …)
 * @param name      The owner name of this record (usually the queried domain)
 * @param value     Human-readable record data (IP address, hostname, text, etc.)
 * @param ttl       Time-to-live in seconds — how long resolvers may cache this record
 * @param rawLine   Full zone-file representation (e.g. "example.com. 300 IN A 93.184.216.34")
 */
data class DnsRecord(
    val type: DnsRecordType?,
    val name: String,
    val value: String,
    val ttl: Long,
    val rawLine: String,
    /** Actual RR type returned by the server, even when it differs from the query type. */
    val rrTypeName: String = type?.displayName ?: "UNKNOWN",
    val section: DnsSection = DnsSection.ANSWER
)
