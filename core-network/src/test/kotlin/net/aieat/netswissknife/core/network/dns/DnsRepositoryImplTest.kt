package net.aieat.netswissknife.core.network.dns

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import org.xbill.DNS.EDNSOption
import org.xbill.DNS.Message
import org.xbill.DNS.Resolver
import org.xbill.DNS.TSIG
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("DnsRepositoryImpl.normalizeDomain")
class DnsRepositoryImplTest {

    @Test
    fun `empty system DNS list returns typed resolver error`() = runTest {
        val result = DnsRepositoryImpl().lookup(
            domain = "example.com",
            recordType = DnsRecordType.A,
            server = DnsServer.System(emptyList()),
        ) as NetworkResult.Error

        assertEquals(ErrorCode.DNS_NO_SYSTEM_RESOLVER, result.info?.code)
        assertTrue(result.message.contains("No system DNS server"))
    }

    @Test
    fun `invalid custom DNS hostname and host port are rejected before resolver construction or IO`() = runTest {
        var resolverFactoryCalls = 0
        var resolverCalls = 0
        var ioFactoryCalls = 0
        val repository = DnsRepositoryImpl(
            resolverFactory = DnsRepositoryImpl.ResolverFactory {
                resolverFactoryCalls++
                ReportingResolver(onQuery = { resolverCalls++ })
            }
        ).also {
            it.ioClientFactoryFactory = {
                ioFactoryCalls++
                throw AssertionError("Invalid custom address must be rejected before transport setup")
            }
        }

        listOf("resolver.example", "192.0.2.53:5353").forEach { address ->
            val result = repository.lookup(
                domain = "example.com",
                recordType = DnsRecordType.A,
                server = DnsServer.Custom(address)
            )
            assertTrue(result is NetworkResult.Error, address)
        }

        assertEquals(0, resolverFactoryCalls)
        assertEquals(0, ioFactoryCalls)
        assertEquals(0, resolverCalls)
    }

    @Test
    fun `custom DNS literal is trimmed for resolver construction and result`() = runTest {
        val resolverServer = AtomicReference<DnsServer?>()
        val repository = DnsRepositoryImpl(
            resolverFactory = DnsRepositoryImpl.ResolverFactory { server ->
                resolverServer.set(server)
                ReportingResolver()
            }
        )

        val result = repository.lookup(
            domain = "example.com",
            recordType = DnsRecordType.A,
            server = DnsServer.Custom("  192.0.2.53  ")
        ) as NetworkResult.Success

        assertEquals(DnsServer.Custom("192.0.2.53"), resolverServer.get())
        assertEquals(DnsServer.Custom("192.0.2.53"), result.data.server)
        assertEquals("192.0.2.53:53", result.data.serverUsed)
    }

    @Test
    fun `system DNS result reports the resolver that actually answered`() = runTest {
        val resolver = ReportingResolver("9.9.9.9")
        val repository = DnsRepositoryImpl(
            resolverFactory = DnsRepositoryImpl.ResolverFactory { resolver }
        )

        val result = repository.lookup(
            domain = "example.com",
            recordType = DnsRecordType.A,
            server = DnsServer.System(listOf("1.1.1.1", "9.9.9.9"))
        )

        assertEquals(
            "9.9.9.9:53",
            (result as net.aieat.netswissknife.core.network.NetworkResult.Success).data.serverUsed
        )
    }

    @Test
    fun `tracking resolver records the endpoint that returned after fallback`() {
        val query = Message.newQuery(
            org.xbill.DNS.Record.newRecord(
                org.xbill.DNS.Name.fromString("example.com."),
                org.xbill.DNS.Type.A,
                org.xbill.DNS.DClass.IN
            )
        )
        val resolver = DnsRepositoryImpl.TrackingExtendedResolver(
            arrayOf(
                DnsResolverEndpoint("1.1.1.1", ReportingResolver(failure = IOException("offline"))),
                DnsResolverEndpoint("9.9.9.9", ReportingResolver())
            )
        )

        resolver.send(query)

        assertEquals("9.9.9.9", (resolver as DnsResolverMetadata).lastServerAddress)
    }

    private class ReportingResolver(
        override val lastServerAddress: String? = null,
        private val failure: IOException? = null,
        private val onQuery: (Message) -> Unit = {}
    ) : Resolver, DnsResolverMetadata {
        override fun setPort(port: Int) = Unit
        override fun setTCP(flag: Boolean) = Unit
        override fun setIgnoreTruncation(flag: Boolean) = Unit
        override fun setEDNS(
            level: Int,
            payloadSize: Int,
            flags: Int,
            options: List<EDNSOption>
        ) = Unit
        override fun setTSIGKey(key: TSIG?) = Unit
        override fun setTimeout(timeout: Duration) = Unit
        override fun send(query: Message): Message {
            failure?.let { throw it }
            onQuery(query)
            return query
        }
        override fun sendAsync(query: Message): CompletionStage<Message> =
            failure?.let { CompletableFuture.failedFuture<Message>(it) }
                ?: CompletableFuture.completedFuture(query)
        override fun sendAsync(query: Message, executor: Executor): CompletionStage<Message> =
            sendAsync(query)
    }

    // ── IPv4 PTR ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IPv4 PTR reversal")
    inner class IPv4Ptr {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "172.217.17.78,  78.17.217.172.in-addr.arpa.",
            "8.8.8.8,        8.8.8.8.in-addr.arpa.",
            "1.2.3.4,        4.3.2.1.in-addr.arpa.",
            "192.168.1.100,  100.1.168.192.in-addr.arpa.",
            "10.0.0.1,       1.0.0.10.in-addr.arpa."
        )
        fun `plain IPv4 is reversed and suffixed`(ip: String, expected: String) {
            assertEquals(
                expected.trim(),
                DnsRepositoryImpl.normalizeDomain(ip.trim(), DnsRecordType.PTR)
            )
        }

        @Test
        fun `trailing dot on IPv4 input is stripped before reversal`() {
            assertEquals(
                "4.3.2.1.in-addr.arpa.",
                DnsRepositoryImpl.normalizeDomain("1.2.3.4.", DnsRecordType.PTR)
            )
        }

        @Test
        fun `already-reversed in-addr arpa input passes through with trailing dot`() {
            assertEquals(
                "78.17.217.172.in-addr.arpa.",
                DnsRepositoryImpl.normalizeDomain("78.17.217.172.in-addr.arpa", DnsRecordType.PTR)
            )
        }

        @Test
        fun `already-reversed in-addr arpa with trailing dot is unchanged`() {
            assertEquals(
                "78.17.217.172.in-addr.arpa.",
                DnsRepositoryImpl.normalizeDomain("78.17.217.172.in-addr.arpa.", DnsRecordType.PTR)
            )
        }
    }

    // ── IPv6 PTR ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IPv6 PTR reversal")
    inner class IPv6Ptr {

        @Test
        fun `Google DNS IPv6 compressed address is fully expanded and reversed`() {
            // 2001:4860:4860::8888 -> 2001:4860:4860:0000:0000:0000:0000:8888
            // hex: 20014860486000000000000000008888
            // reversed nibbles: 8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2
            assertEquals(
                "8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("2001:4860:4860::8888", DnsRecordType.PTR)
            )
        }

        @Test
        fun `loopback IPv6 is reversed`() {
            // ::1 -> 0000:0000:0000:0000:0000:0000:0000:0001
            // hex: 00000000000000000000000000000001
            // reversed: 1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0
            assertEquals(
                "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("::1", DnsRecordType.PTR)
            )
        }

        @Test
        fun `Cloudflare IPv6 DNS address is reversed`() {
            // 2606:4700:4700::1111
            // expanded: 2606:4700:4700:0000:0000:0000:0000:1111
            // hex: 26064700470000000000000000001111
            // reversed nibbles: 1.1.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.7.4.0.0.7.4.6.0.6.2
            assertEquals(
                "1.1.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.7.4.0.0.7.4.6.0.6.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("2606:4700:4700::1111", DnsRecordType.PTR)
            )
        }

        @Test
        fun `full uncompressed IPv6 address is reversed`() {
            // 2001:0db8:0000:0000:0000:0000:0000:0001
            // hex: 20010db8000000000000000000000001
            // reversed: 1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2
            assertEquals(
                "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("2001:0db8:0000:0000:0000:0000:0000:0001", DnsRecordType.PTR)
            )
        }

        @Test
        fun `leading double-colon IPv6 is reversed`() {
            // ::ffff:192.0.2.1 (IPv4-mapped) should still be treated as IPv6
            // But a simple :: all-zeros address:
            // :: -> 0000:0000:0000:0000:0000:0000:0000:0000
            assertEquals(
                "0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("::", DnsRecordType.PTR)
            )
        }

        @Test
        fun `already-reversed ip6 arpa input passes through with trailing dot`() {
            assertEquals(
                "8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain(
                    "8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2.ip6.arpa",
                    DnsRecordType.PTR
                )
            )
        }
    }

    // ── Non-PTR types ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Non-PTR record types")
    inner class NonPtr {

        @ParameterizedTest(name = "{1} query for {0}")
        @CsvSource(
            "example.com,   A",
            "example.com,   AAAA",
            "example.com,   MX",
            "example.com,   TXT",
            "example.com,   CNAME",
            "example.com,   NS",
            "example.com,   SOA",
            "example.com,   SRV",
            "example.com,   CAA"
        )
        fun `plain domain has trailing dot appended`(domain: String, type: String) {
            val recordType = DnsRecordType.valueOf(type.trim())
            assertEquals(
                "example.com.",
                DnsRepositoryImpl.normalizeDomain(domain.trim(), recordType)
            )
        }

        @Test
        fun `domain with existing trailing dot is unchanged`() {
            assertEquals(
                "example.com.",
                DnsRepositoryImpl.normalizeDomain("example.com.", DnsRecordType.A)
            )
        }

        @ParameterizedTest(name = "{0} query encodes an internationalized hostname")
        @CsvSource("A", "AAAA")
        fun `Unicode hostname is IDNA encoded before the resolver call`(typeName: String) = runTest {
            val recordType = DnsRecordType.valueOf(typeName)
            val question = AtomicReference<Pair<String, Int>?>(null)
            val resolver = ReportingResolver(onQuery = { query ->
                query.question?.let { question.set(it.name.toString() to it.type) }
            })
            val repository = DnsRepositoryImpl(
                resolverFactory = DnsRepositoryImpl.ResolverFactory { resolver }
            )

            val result = repository.lookup("bücher.de", recordType, DnsServer.Google)

            assertEquals(true, result is NetworkResult.Success)
            assertEquals("xn--bcher-kva.de." to recordType.dnsTypeInt, question.get())
        }

        @Test
        fun `SRV query preserves service labels and encodes its Unicode suffix`() = runTest {
            val question = AtomicReference<Pair<String, Int>?>(null)
            val resolver = ReportingResolver(onQuery = { query ->
                query.question?.let { question.set(it.name.toString() to it.type) }
            })
            val repository = DnsRepositoryImpl(
                resolverFactory = DnsRepositoryImpl.ResolverFactory { resolver }
            )

            val result = repository.lookup("_http._tcp.bücher.de", DnsRecordType.SRV, DnsServer.Google)

            assertEquals(true, result is NetworkResult.Success)
            assertEquals("_http._tcp.xn--bcher-kva.de." to DnsRecordType.SRV.dnsTypeInt, question.get())
        }

        @Test
        fun `malformed IDNA names return validation errors without creating a resolver`() = runTest {
            var resolverFactoryCalls = 0
            val repository = DnsRepositoryImpl(
                resolverFactory = DnsRepositoryImpl.ResolverFactory {
                    resolverFactoryCalls++
                    ReportingResolver()
                }
            )

            val malformedNames = listOf(
                "bücher..de",
                "bad\u0000label.de",
                "ü".repeat(64) + ".de",
                "example.com..",
                "xn--",
                "xn--invalid-punycode.de"
            )
            malformedNames.forEach { domain ->
                val result = repository.lookup(domain, DnsRecordType.A, DnsServer.Google)
                assertEquals(true, result is NetworkResult.Error, domain)
                assertEquals(true, (result as NetworkResult.Error).message.startsWith("Invalid DNS domain name"))
            }
            assertEquals(0, resolverFactoryCalls)
        }

        @Test
        fun `IDNA dot separators normalize before splitting labels`() {
            assertEquals(
                "xn--bcher-kva.de.",
                DnsRepositoryImpl.normalizeDomain("bücher。de", DnsRecordType.A)
            )
        }

        @Test
        fun `IPv4-looking string is NOT reversed for non-PTR types`() {
            assertEquals(
                "8.8.8.8.",
                DnsRepositoryImpl.normalizeDomain("8.8.8.8", DnsRecordType.A)
            )
        }

        @Test
        fun `SRV service label is left as-is`() {
            assertEquals(
                "_http._tcp.example.com.",
                DnsRepositoryImpl.normalizeDomain("_http._tcp.example.com", DnsRecordType.SRV)
            )
        }
    }

    // ── PTR with non-IP hostname ──────────────────────────────────────────────

    @Nested
    @DisplayName("PTR with non-IP input")
    inner class PtrNonIp {

        @Test
        fun `hostname passed as PTR query appends trailing dot without modification`() {
            assertEquals(
                "some.custom.ptr.host.",
                DnsRepositoryImpl.normalizeDomain("some.custom.ptr.host", DnsRecordType.PTR)
            )
        }
    }
}
