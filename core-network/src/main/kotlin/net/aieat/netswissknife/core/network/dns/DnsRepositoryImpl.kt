package net.aieat.netswissknife.core.network.dns

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import org.xbill.DNS.DClass
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Rcode
import org.xbill.DNS.Resolver
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.Inet6Address
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

internal interface DnsResolverMetadata {
    val lastServerAddress: String?
}

internal data class DnsResolverEndpoint(val address: String, val resolver: Resolver)

class DnsRepositoryImpl(
    private val resolverFactory: ResolverFactory = ResolverFactory { server -> defaultResolver(server) }
) : DnsRepository {

    fun interface ResolverFactory {
        fun create(server: DnsServer): Resolver
    }

    companion object {
        private val TIMEOUT = Duration.ofSeconds(8)
        private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

        internal fun normalizeDomain(domain: String, recordType: DnsRecordType): String {
            val stripped = domain.trimEnd('.')
            if (recordType == DnsRecordType.PTR) {
                val ipv4Match = IPV4_REGEX.matchEntire(stripped)
                if (ipv4Match != null) {
                    val (a, b, c, d) = ipv4Match.destructured
                    return "$d.$c.$b.$a.in-addr.arpa."
                }
                if (stripped.contains(':')) {
                    val reversed = reverseIPv6(stripped)
                    if (reversed != null) return reversed
                }
                if (stripped.endsWith(".in-addr.arpa") || stripped.endsWith(".ip6.arpa")) {
                    return "$stripped."
                }
            }
            return "$stripped."
        }

        private fun reverseIPv6(ip: String): String? = try {
            val addr = InetAddress.getByName(ip)
            if (addr !is Inet6Address) return null
            val hex = addr.address.joinToString("") { "%02x".format(it) }
            "${hex.reversed().toList().joinToString(".")}.ip6.arpa."
        } catch (_: Exception) {
            null
        }

        private fun defaultResolver(server: DnsServer): Resolver = when (server) {
            is DnsServer.System -> TrackingExtendedResolver(server.serverAddresses).also { it.setTimeout(TIMEOUT) }
            is DnsServer.Google -> simpleResolver(DnsServer.Google.PRIMARY)
            is DnsServer.Cloudflare -> simpleResolver(DnsServer.Cloudflare.PRIMARY)
            is DnsServer.OpenDns -> simpleResolver(DnsServer.OpenDns.PRIMARY)
            is DnsServer.Quad9 -> simpleResolver(DnsServer.Quad9.PRIMARY)
            is DnsServer.Custom -> simpleResolver(server.address)
        }

        private fun simpleResolver(address: String): Resolver =
            SimpleResolver(address).also { it.setTimeout(TIMEOUT) }
    }

    override suspend fun lookup(
        domain: String,
        recordType: DnsRecordType,
        server: DnsServer
    ): NetworkResult<DnsResult> = withContext(Dispatchers.IO) {
        if (server is DnsServer.System && server.serverAddresses.isEmpty()) {
            return@withContext NetworkResult.Error(
                "No system DNS server reported by Android (Private DNS or no network). Choose a resolver."
            )
        }

        val startNs = System.nanoTime()
        try {
            val normalizedDomain = normalizeDomain(domain, recordType)
            val queryName = Name.fromString(normalizedDomain)
            val queryRecord = Record.newRecord(queryName, recordType.dnsTypeInt, DClass.IN)
            val queryMessage = Message.newQuery(queryRecord)
            val resolver = resolverFactory.create(server)
            val response = resolver.send(queryMessage)
            val queryTimeMs = (System.nanoTime() - startNs) / 1_000_000L
            val serverUsed = (resolver as? DnsResolverMetadata)?.lastServerAddress
                ?.let(::formatServerAddress)
                ?: serverAddress(server)
            val result = DnsMessageMapper.toResult(
                domain = domain.trimEnd('.'),
                requestedType = recordType,
                server = server,
                response = response,
                serverUsed = serverUsed,
                queryTimeMs = queryTimeMs
            )
            NetworkResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(
                message = "DNS lookup failed: ${e.message ?: e.javaClass.simpleName}",
                cause = e
            )
        }
    }

    private fun serverAddress(server: DnsServer): String = when (server) {
        is DnsServer.System -> formatServerAddress(server.serverAddresses.first())
        is DnsServer.Google -> formatServerAddress(DnsServer.Google.PRIMARY)
        is DnsServer.Cloudflare -> formatServerAddress(DnsServer.Cloudflare.PRIMARY)
        is DnsServer.OpenDns -> formatServerAddress(DnsServer.OpenDns.PRIMARY)
        is DnsServer.Quad9 -> formatServerAddress(DnsServer.Quad9.PRIMARY)
        is DnsServer.Custom -> formatServerAddress(server.address)
    }

    private fun formatServerAddress(address: String): String =
        if (address.contains(':') && !address.startsWith('[')) "[$address]:53" else "$address:53"

    private class ResolverSelection {
        @Volatile
        var lastServerAddress: String? = null
    }

    internal class TrackingExtendedResolver private constructor(
        private val selection: ResolverSelection,
        endpoints: Array<DnsResolverEndpoint>
    ) : ExtendedResolver(
        endpoints.map { endpoint ->
            TrackingResolver(endpoint.resolver, endpoint.address) { selected ->
                selection.lastServerAddress = selected
            }
        }.toTypedArray()
    ), DnsResolverMetadata {
        constructor(addresses: List<String>) : this(
            ResolverSelection(),
            addresses.map { address ->
                DnsResolverEndpoint(
                    address,
                    SimpleResolver(address).also { it.setTimeout(TIMEOUT) }
                )
            }.toTypedArray()
        )

        internal constructor(endpoints: Array<DnsResolverEndpoint>) : this(ResolverSelection(), endpoints)

        override val lastServerAddress: String?
            get() = selection.lastServerAddress
    }

    private class TrackingResolver(
        private val delegate: Resolver,
        private val address: String,
        private val onSuccess: (String) -> Unit
    ) : Resolver {
        override fun setPort(port: Int) = delegate.setPort(port)
        override fun setTCP(flag: Boolean) = delegate.setTCP(flag)
        override fun setIgnoreTruncation(flag: Boolean) = delegate.setIgnoreTruncation(flag)
        override fun setEDNS(level: Int, payloadSize: Int, flags: Int, options: List<org.xbill.DNS.EDNSOption>) =
            delegate.setEDNS(level, payloadSize, flags, options)
        override fun setTSIGKey(key: org.xbill.DNS.TSIG?) = delegate.setTSIGKey(key)
        override fun setTimeout(timeout: Duration) = delegate.setTimeout(timeout)
        override fun send(query: Message): Message = delegate.send(query).also { onSuccess(address) }
        override fun sendAsync(query: Message): CompletionStage<Message> =
            delegate.sendAsync(query).whenComplete { _, error -> if (error == null) onSuccess(address) }
        override fun sendAsync(query: Message, executor: Executor): CompletionStage<Message> =
            delegate.sendAsync(query, executor).whenComplete { _, error -> if (error == null) onSuccess(address) }
    }
}

/** Pure dnsjava-to-domain mapping, kept separate so malformed responses are testable in-memory. */
object DnsMessageMapper {
    fun toResult(
        domain: String,
        requestedType: DnsRecordType,
        server: DnsServer,
        response: Message,
        serverUsed: String,
        queryTimeMs: Long
    ): DnsResult {
        val records = mapSection(response, Section.ANSWER, DnsSection.ANSWER)
        val authority = mapSection(response, Section.AUTHORITY, DnsSection.AUTHORITY)
        val additional = mapSection(response, Section.ADDITIONAL, DnsSection.ADDITIONAL)
        val flags = buildSet {
            val header = response.header
            if (header.getFlag(org.xbill.DNS.Flags.AA.toInt())) add("AA")
            if (header.getFlag(org.xbill.DNS.Flags.AD.toInt())) add("AD")
            if (header.getFlag(org.xbill.DNS.Flags.TC.toInt())) add("TC")
            if (header.getFlag(org.xbill.DNS.Flags.RA.toInt())) add("RA")
            if (header.getFlag(org.xbill.DNS.Flags.RD.toInt())) add("RD")
        }
        val rcode = Rcode.string(response.header.rcode)
        val rawResponse = buildString {
            appendLine(";; Query time: $queryTimeMs ms")
            appendLine(";; SERVER: $serverUsed")
            appendLine(";; RCODE: $rcode")
            appendLine(";; FLAGS: ${flags.joinToString(" ")}")
            appendLine()
            append(response.toString())
        }
        return DnsResult(
            domain = domain,
            recordType = requestedType,
            server = server,
            records = records,
            authority = authority,
            additional = additional,
            rcode = rcode,
            flags = flags,
            serverUsed = serverUsed,
            queryTimeMs = queryTimeMs,
            rawResponse = rawResponse
        )
    }

    private fun mapSection(response: Message, section: Int, dnsSection: DnsSection): List<DnsRecord> =
        response.getSection(section).map { record ->
            val typeName = runCatching { Type.string(record.type) }.getOrDefault("TYPE${record.type}")
            DnsRecord(
                type = DnsRecordType.fromDnsTypeInt(record.type),
                rrTypeName = typeName,
                name = record.name.toString().trimEnd('.'),
                value = formatRecordValue(record),
                ttl = record.ttl,
                rawLine = record.toString(),
                section = dnsSection
            )
        }

    private fun formatRecordValue(record: Record): String = try {
        when (record.type) {
            Type.TXT -> record.rdataToString()
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\" \"", " ")
            Type.CNAME, Type.NS, Type.PTR, Type.SRV -> record.rdataToString().trimEnd('.')
            Type.SOA -> record.rdataToString().split(" ").joinToString(" ") { it.trimEnd('.') }
            else -> record.rdataToString()
        }
    } catch (_: Exception) {
        record.rdataToString()
    }
}
