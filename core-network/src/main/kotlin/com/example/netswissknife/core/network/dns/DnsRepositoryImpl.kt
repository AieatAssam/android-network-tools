package com.example.netswissknife.core.network.dns

import com.example.netswissknife.core.network.NetworkResult
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
import java.time.Duration

/**
 * Production DNS repository using dnsjava for full record-type support and
 * custom DNS server selection (IPv4 & IPv6).
 */
class DnsRepositoryImpl : DnsRepository {

    companion object {
        private val TIMEOUT = Duration.ofSeconds(8)
    }

    override suspend fun lookup(
        domain: String,
        recordType: DnsRecordType,
        server: DnsServer
    ): NetworkResult<DnsResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()

        try {
            // Normalize domain – append root dot if absent
            val normalizedDomain = if (domain.endsWith(".")) domain else "$domain."
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
        } catch (e: Exception) {
            NetworkResult.Error(
                message = "DNS lookup failed: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    // ── Resolver factory ─────────────────────────────────────────────────────

    private fun buildResolver(server: DnsServer): Resolver {
        return when (server) {
            is DnsServer.System     -> buildSystemResolver()
            is DnsServer.Google     -> simpleResolver(DnsServer.Google.PRIMARY)
            is DnsServer.Cloudflare -> simpleResolver(DnsServer.Cloudflare.PRIMARY)
            is DnsServer.OpenDns    -> simpleResolver(DnsServer.OpenDns.PRIMARY)
            is DnsServer.Quad9      -> simpleResolver(DnsServer.Quad9.PRIMARY)
            is DnsServer.Custom     -> simpleResolver(server.address)
        }
    }

    private fun simpleResolver(address: String): Resolver =
        SimpleResolver(address).also { it.setTimeout(TIMEOUT) }

    private fun buildSystemResolver(): Resolver {
        return try {
            // ExtendedResolver auto-detects system DNS servers from OS configuration.
            // Falls back to localhost / platform defaults if none found.
            ExtendedResolver().also { it.setTimeout(TIMEOUT) }
        } catch (e: Exception) {
            // Absolute fallback: use Cloudflare when system DNS can't be detected
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
