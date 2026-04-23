package net.aieat.netswissknife.core.network.dns

import net.aieat.netswissknife.core.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.DClass
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Resolver
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.Inet6Address
import java.net.InetAddress
import java.time.Duration

/**
 * Production DNS repository using dnsjava for full record-type support and
 * custom DNS server selection (IPv4 & IPv6).
 */
class DnsRepositoryImpl : DnsRepository {

    companion object {
        private val TIMEOUT = Duration.ofSeconds(8)

        private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

        /** Transforms a query name into the canonical fully-qualified form for the given record type.
         *  PTR queries auto-reverse IPv4 (in-addr.arpa) and IPv6 (ip6.arpa) addresses. */
        internal fun normalizeDomain(domain: String, recordType: DnsRecordType): String {
            val stripped = domain.trimEnd('.')
            if (recordType == DnsRecordType.PTR) {
                // IPv4 – reverse octets and append .in-addr.arpa.
                val ipv4Match = IPV4_REGEX.matchEntire(stripped)
                if (ipv4Match != null) {
                    val (a, b, c, d) = ipv4Match.destructured
                    return "$d.$c.$b.$a.in-addr.arpa."
                }
                // IPv6 – expand to 32 nibbles, reverse, and append .ip6.arpa.
                if (stripped.contains(':')) {
                    val reversed = reverseIPv6(stripped)
                    if (reversed != null) return reversed
                }
                // Already in reverse-lookup form – just ensure trailing dot
                if (stripped.endsWith(".in-addr.arpa") || stripped.endsWith(".ip6.arpa")) {
                    return "$stripped."
                }
            }
            return if (domain.endsWith(".")) domain else "$domain."
        }

        /** Parses an IPv6 address string and returns its .ip6.arpa. PTR form, or null on failure. */
        private fun reverseIPv6(ip: String): String? {
            return try {
                val addr = InetAddress.getByName(ip)
                if (addr !is Inet6Address) return null
                val hex = addr.address.joinToString("") { "%02x".format(it) }
                val nibbles = hex.reversed().toList().joinToString(".")
                "$nibbles.ip6.arpa."
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun lookup(
        domain: String,
        recordType: DnsRecordType,
        server: DnsServer
    ): NetworkResult<DnsResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()

        try {
            val normalizedDomain = normalizeDomain(domain, recordType)
            val queryName = Name.fromString(normalizedDomain)
            val dnsType = recordType.dnsTypeInt

            // Build resolver for the chosen server
            val resolver = buildResolver(server)

            // Build the DNS query message
            val queryRecord = Record.newRecord(queryName, dnsType, DClass.IN)
            val queryMessage = Message.newQuery(queryRecord)

            // Send query and receive response
            val response = resolver.send(queryMessage)
            val queryTimeMs = System.currentTimeMillis() - startMs

            // Extract answer records
            val answerRecords = response.getSection(Section.ANSWER)
            val rawResponse = buildRawResponse(response, queryTimeMs)

            val dnsRecords = answerRecords.map { rec ->
                DnsRecord(
                    type = recordType,
                    name = rec.name.toString().trimEnd('.'),
                    value = formatRecordValue(rec),
                    ttl = rec.ttl,
                    rawLine = rec.toString()
                )
            }

            NetworkResult.Success(
                DnsResult(
                    domain = domain.trimEnd('.'),
                    recordType = recordType,
                    server = server,
                    records = dnsRecords,
                    queryTimeMs = queryTimeMs,
                    rawResponse = rawResponse
                )
            )
        } catch (e: Throwable) {
            NetworkResult.Error(
                message = "DNS lookup failed: ${e.message ?: e.javaClass.simpleName}",
                cause = e
            )
        }
    }

    // ── Resolver factory ─────────────────────────────────────────────────────

    private fun buildResolver(server: DnsServer): Resolver {
        return when (server) {
            is DnsServer.System     -> buildSystemResolver(server)
            is DnsServer.Google     -> simpleResolver(DnsServer.Google.PRIMARY)
            is DnsServer.Cloudflare -> simpleResolver(DnsServer.Cloudflare.PRIMARY)
            is DnsServer.OpenDns    -> simpleResolver(DnsServer.OpenDns.PRIMARY)
            is DnsServer.Quad9      -> simpleResolver(DnsServer.Quad9.PRIMARY)
            is DnsServer.Custom     -> simpleResolver(server.address)
        }
    }

    private fun simpleResolver(address: String): Resolver =
        SimpleResolver(address).also { it.setTimeout(TIMEOUT) }

    private fun buildSystemResolver(server: DnsServer.System): Resolver {
        // serverAddresses are populated by the app layer (ConnectivityManager / LinkProperties).
        // Never use ExtendedResolver() with no args – on Android that falls back to localhost:53
        // which has no DNS listener and throws "Port unreachable".
        return if (server.serverAddresses.isNotEmpty()) {
            try {
                ExtendedResolver(server.serverAddresses.toTypedArray()).also { it.setTimeout(TIMEOUT) }
            } catch (e: Throwable) {
                simpleResolver(DnsServer.Cloudflare.PRIMARY)
            }
        } else {
            simpleResolver(DnsServer.Cloudflare.PRIMARY)
        }
    }

    // ── Record formatting ────────────────────────────────────────────────────

    /**
     * Extracts a human-readable value string from a DNS record.
     * Falls back to rdataToString() for any type not explicitly handled.
     */
    private fun formatRecordValue(record: Record): String {
        return try {
            when (record.getType()) {
                Type.A, Type.AAAA -> {
                    record.rdataToString()
                }
                Type.MX -> {
                    record.rdataToString()
                }
                Type.TXT -> {
                    record.rdataToString()
                        .removePrefix("\"")
                        .removeSuffix("\"")
                        .replace("\" \"", " ")
                }
                Type.CNAME, Type.NS, Type.PTR -> {
                    record.rdataToString().trimEnd('.')
                }
                Type.SOA -> {
                    record.rdataToString()
                        .split(" ")
                        .joinToString(" ") { part -> part.trimEnd('.') }
                }
                Type.SRV -> {
                    record.rdataToString().trimEnd('.')
                }
                else -> record.rdataToString()
            }
        } catch (e: Exception) {
            record.rdataToString()
        }
    }

    // ── Raw response ─────────────────────────────────────────────────────────

    private fun buildRawResponse(response: Message, queryTimeMs: Long): String {
        return buildString {
            appendLine(";; Query time: ${queryTimeMs} ms")
            appendLine(";; SERVER: (see selected server above)")
            appendLine()
            append(response.toString())
        }
    }
}
