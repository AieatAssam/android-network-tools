package net.aieat.netswissknife.core.network.dns

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import org.xbill.DNS.DClass
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.io.IoClientFactory
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Rcode
import org.xbill.DNS.Resolver
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import java.net.Inet6Address
import java.net.InetAddress
import java.net.IDN
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

internal interface DnsResolverMetadata {
    val lastServerAddress: String?
}

internal data class DnsResolverEndpoint(val address: String, val resolver: Resolver)

class DnsRepositoryImpl(
    private val resolverFactory: ResolverFactory = ResolverFactory { server -> defaultResolver(server) }
) : DnsRepository {

    internal var ioClientFactoryFactory: (OperationSession) -> IoClientFactory = { SessionIoClientFactory(it) }

    fun interface ResolverFactory {
        fun create(server: DnsServer): Resolver
    }

    companion object {
        private val TIMEOUT = Duration.ofSeconds(8)
        private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
        private val IPV4_SHAPE_REGEX = Regex("""^\d+\.\d+\.\d+\.\d+$""")
        private val ASCII_HOST_LABEL = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
        private val ASCII_OWNER_LABEL = Regex("""^_[a-z0-9](?:[a-z0-9-]{0,60}[a-z0-9])?$""")

        internal fun normalizeDomain(domain: String, recordType: DnsRecordType): String {
            val separatorNormalized = domain.trim()
                .replace('\u3002', '.')
                .replace('\uFF0E', '.')
                .replace('\uFF61', '.')
            val stripped = when {
                separatorNormalized.endsWith('.') -> separatorNormalized.dropLast(1)
                else -> separatorNormalized
            }
            require(stripped.isNotEmpty()) { "Domain name must not be empty" }
            require(!stripped.startsWith('.') && !stripped.endsWith('.') && !stripped.contains("..")) {
                "Domain name contains an empty label or repeated root dot"
            }

            if (recordType == DnsRecordType.PTR) {
                val ipv4Match = IPV4_REGEX.matchEntire(stripped)
                if (ipv4Match != null) {
                    require(HostValidator.isValidIpv4(stripped)) { "Invalid IPv4 address" }
                    val (a, b, c, d) = ipv4Match.destructured
                    return "$d.$c.$b.$a.in-addr.arpa."
                }
                require(!IPV4_SHAPE_REGEX.matches(stripped)) { "Invalid IPv4 address" }
                if (stripped.contains(':')) {
                    return reverseIPv6(stripped)
                        ?: throw IllegalArgumentException("Invalid IPv6 address")
                }
                val lowerCase = stripped.lowercase(Locale.ROOT)
                if (lowerCase.endsWith(".in-addr.arpa") || lowerCase.endsWith(".ip6.arpa")) {
                    return "$lowerCase."
                }
            } else {
                require(!stripped.contains(':')) { "IPv6 literals are only valid for PTR lookups" }
            }

            return toAsciiDnsName(stripped)
        }

        /**
         * Encodes U-labels with Java's IDNA profile, matching HostValidator's
         * existing IDN.toASCII compatibility policy. Leading-underscore DNS
         * owner labels (for SRV/TXT queries) are preserved after narrow ASCII
         * validation because they are not host labels.
         */
        private fun toAsciiDnsName(domain: String): String {
            val labels = domain.split('.')
            val asciiLabels = labels.map { label ->
                if (label.startsWith('_')) {
                    require(ASCII_OWNER_LABEL.matches(label.lowercase(Locale.ROOT))) {
                        "Invalid underscore DNS owner label"
                    }
                    label.lowercase(Locale.ROOT)
                } else {
                    val ascii = try {
                        IDN.toASCII(label, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("Invalid internationalized domain label", e)
                    }
                    require(ASCII_HOST_LABEL.matches(ascii)) {
                        "Invalid internationalized domain label"
                    }
                    if (ascii.startsWith("xn--")) {
                        val unicode = IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED)
                        require(unicode != ascii &&
                            IDN.toASCII(unicode, IDN.ALLOW_UNASSIGNED).equals(ascii, ignoreCase = true)
                        ) {
                            "Invalid punycode DNS label"
                        }
                    }
                    ascii
                }
            }
            val asciiName = asciiLabels.joinToString(".")
            val wireLength = asciiLabels.sumOf { it.length + 1 } + 1 // label lengths plus root octet
            require(asciiName.length <= 253 && wireLength <= 255) {
                "Domain name is too long after IDNA encoding"
            }
            return "$asciiName."
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
    ): NetworkResult<DnsResult> = lookup(domain, recordType, server, DnsLookupOperation.newSession(clock))

    internal var clock: MonotonicClock = SystemMonotonicClock

    /**
     * Custom DNS server addresses are trimmed and must be IPv4 or IPv6 literals.
     * Hostnames and host:port input are rejected before resolver construction.
     */
    override suspend fun lookup(
        domain: String,
        recordType: DnsRecordType,
        server: DnsServer,
        operationSession: OperationSession,
    ): NetworkResult<DnsResult> = withContext(Dispatchers.IO) {
        val normalizedServer = when (server) {
            is DnsServer.Custom -> {
                val address = server.address.trim()
                if (!HostValidator.isValidIpv4(address) && !HostValidator.isValidIpv6(address)) {
                    return@withContext NetworkResult.Error(
                        "Invalid custom DNS server: expected an IPv4 or IPv6 address"
                    )
                }
                DnsServer.Custom(address)
            }
            else -> server
        }

        if (normalizedServer is DnsServer.System && normalizedServer.serverAddresses.isEmpty()) {
            return@withContext NetworkResult.Error(
                "No system DNS server reported by Android (Private DNS or no network). Choose a resolver."
            )
        }

        val normalizedDomain = try {
            normalizeDomain(domain, recordType)
        } catch (e: IllegalArgumentException) {
            return@withContext NetworkResult.Error("Invalid DNS domain name: ${e.message}", e)
        }

        try {
            OperationRunner.run(operationSession) {
                val startNs = clock.nowNanos()
                val queryName = Name.fromString(normalizedDomain)
                val queryRecord = Record.newRecord(queryName, recordType.dnsTypeInt, DClass.IN)
                val queryMessage = Message.newQuery(queryRecord)
                val resolver = resolverFactory.create(normalizedServer)
                bindSessionTransport(resolver, operationSession)
                ensureCurrentOperationActive()
                val sendThread = Thread.currentThread()
                // Dispatchers.IO reuses workers. Clear any prior interrupt before this blocking
                // call and again afterward so dnsjava's restored interrupt cannot poison the pool.
                Thread.interrupted()
                val resolverCall = try {
                    operationSession.resources.register(ResolverCallLease(sendThread))
                } catch (failure: Throwable) {
                    Thread.interrupted()
                    throw failure
                }
                val response = try {
                    resolver.send(queryMessage)
                } finally {
                    // dnsjava restores the interrupt flag when its blocking future wait is
                    // interrupted. Clear it before Dispatchers.IO reuses this worker.
                    resolverCall.markCompleted()
                    Thread.interrupted()
                    operationSession.resources.release(resolverCall)
                }
                ensureCurrentOperationActive()
                val queryTimeMs = clock.elapsedMillisSince(startNs)
                val serverUsed = (resolver as? DnsResolverMetadata)?.lastServerAddress
                    ?.let(::formatServerAddress)
                    ?: serverAddress(normalizedServer)
                val result = DnsMessageMapper.toResult(
                    domain = domain.trimEnd('.'),
                    requestedType = recordType,
                    server = normalizedServer,
                    response = response,
                    serverUsed = serverUsed,
                    queryTimeMs = queryTimeMs
                )
                NetworkResult.Success(result)
            }
        } catch (e: CancellationException) {
            if (e is OperationCancellationException && e.reason == CancellationReason.DEADLINE_EXCEEDED) {
                return@withContext NetworkResult.Error("DNS lookup failed: Operation deadline exceeded", e)
            }
            throw e
        } catch (e: Exception) {
            when (operationSession.cancellationReason) {
                CancellationReason.DEADLINE_EXCEEDED -> return@withContext NetworkResult.Error(
                    "DNS lookup failed: Operation deadline exceeded",
                    e,
                )
                null -> Unit
                else -> throw OperationCancellationException(
                    checkNotNull(operationSession.cancellationReason),
                    e,
                )
            }
            NetworkResult.Error(
                message = "DNS lookup failed: ${e.message ?: e.javaClass.simpleName}",
                cause = e
            )
        }
    }

    private fun bindSessionTransport(resolver: Resolver, session: OperationSession) {
        val timeout = Duration.ofMillis(
            session.budget.remainingTimeoutMillis().coerceAtLeast(1).coerceAtMost(TIMEOUT.toMillis())
        )
        when (resolver) {
            is SimpleResolver -> {
                resolver.setTimeout(timeout)
                resolver.ioClientFactory = ioClientFactoryFactory(session)
            }
            is TrackingExtendedResolver -> resolver.bindSession(session, timeout, ioClientFactoryFactory)
            else -> resolver.setTimeout(timeout)
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

        fun bindSession(
            session: OperationSession,
            timeout: Duration,
            ioFactory: (OperationSession) -> IoClientFactory,
        ) {
            setTimeout(timeout)
            getResolvers().forEach { resolver ->
                when (resolver) {
                    is TrackingResolver -> resolver.bindSession(session, timeout, ioFactory)
                    is SimpleResolver -> {
                        resolver.setTimeout(timeout)
                        resolver.ioClientFactory = ioFactory(session)
                    }
                    else -> resolver.setTimeout(timeout)
                }
            }
        }
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
        fun bindSession(
            session: OperationSession,
            timeout: Duration,
            ioFactory: (OperationSession) -> IoClientFactory,
        ) {
            delegate.setTimeout(timeout)
            if (delegate is SimpleResolver) delegate.ioClientFactory = ioFactory(session)
        }
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

/** Interrupts dnsjava's blocking send wait if cancellation lands between UDP and TCP work. */
private class ResolverCallLease(private val thread: Thread) : AutoCloseable {
    private enum class State { ACTIVE, COMPLETED, CANCELLED }
    private val lock = Any()
    private var state = State.ACTIVE

    fun markCompleted() {
        synchronized(lock) {
            if (state == State.ACTIVE) state = State.COMPLETED
        }
    }

    override fun close() {
        synchronized(lock) {
            if (state == State.ACTIVE) {
                state = State.CANCELLED
                thread.interrupt()
            }
        }
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
            Type.TXT -> (record as TXTRecord).semanticTextValue()
            Type.CNAME, Type.NS, Type.PTR, Type.SRV -> record.rdataToString().trimEnd('.')
            Type.SOA -> record.rdataToString().split(" ").joinToString(" ") { it.trimEnd('.') }
            else -> record.rdataToString()
        }
    } catch (_: Exception) {
        record.rdataToString()
    }

    /** TXT character-strings form one semantic octet string; dnsjava's rdata text is presentation syntax. */
    private fun TXTRecord.semanticTextValue(): String {
        @Suppress("UNCHECKED_CAST")
        val segments = getStringsAsByteArrays() as List<ByteArray>
        val bytes = ByteArray(segments.sumOf { it.size })
        var offset = 0
        for (segment in segments) {
            segment.copyInto(bytes, destinationOffset = offset)
            offset += segment.size
        }
        // String's UTF-8 decoder replaces malformed sequences with U+FFFD.
        return String(bytes, Charsets.UTF_8)
    }
}
