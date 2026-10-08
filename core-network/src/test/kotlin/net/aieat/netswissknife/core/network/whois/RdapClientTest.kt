package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import okio.BufferedSink
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RdapClientTest {
    private val servers = mutableListOf<MockWebServer>()

    @AfterEach
    fun tearDown() = servers.forEach(MockWebServer::close)

    @Test
    fun `bootstrap parser normalizes suffixes and preserves normalized base paths`() {
        val fixture = javaClass.getResource("/rdap/dns-bootstrap-synthetic.json")!!.readText()
        val bootstrap = DnsBootstrap.parse(fixture)

        assertEquals(listOf("com", "net"), bootstrap.services[0].suffixes)
        assertEquals("https://rdap.example.invalid/shared/", bootstrap.services[0].baseUrls[1].toString())
        assertEquals("xn--bcher-kva", bootstrap.services[2].suffixes.single())
        assertEquals("", bootstrap.services[3].suffixes.single())
    }

    @Test
    fun `domain lookup uses longest label suffix HTTPS base and caches bootstrap`() = runTest {
        val server = server()
        val bootstrap = """
            {"services":[
              [["com"],["http://unused.invalid/", "https://rdap.example.invalid/shared/"]],
              [["example.com"],["${server.url("/specific").toString()}"]]
            ]}
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(bootstrap).addHeader("Cache-Control", "max-age=3600").build())
        server.enqueue(MockResponse.Builder().body("{\"objectClassName\":\"domain\"}").build())
        server.enqueue(MockResponse.Builder().body("{\"objectClassName\":\"domain\"}").build())
        val client = rdapClient(bootstrapUrl = server.url("/dns.json"))

        val first = client.lookup("www.Example.com.", WhoisQueryType.DOMAIN)
        val second = client.lookup("example.com", WhoisQueryType.DOMAIN)

        assertInstanceOf(RdapLookupResult.Found::class.java, first)
        assertEquals("localhost", (first as RdapLookupResult.Found).finalResponseHost)
        assertInstanceOf(RdapLookupResult.Found::class.java, second)
        assertEquals(3, server.requestCount) // exact example.com beats the broader com suffix
        assertEquals("/dns.json", server.takeRequest().url.encodedPath)
        assertEquals("/specific/domain/www.example.com", server.takeRequest().url.encodedPath)
        assertEquals("/specific/domain/example.com", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `domain service matching requires label boundary and IDNA query is A-label`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().body("""
            {"services":[[["com"],["${server.url("/com")}"]],[["xn--bcher-kva"],["${server.url("/idn")}"]]]}
        """.trimIndent()).build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        val client = rdapClient(bootstrapUrl = server.url("/bootstrap"))

        val noMatch = runCatching { client.lookup("notcom", WhoisQueryType.DOMAIN) }
        assertTrue(noMatch.exceptionOrNull() is IOException)
        client.lookup("bücher", WhoisQueryType.DOMAIN).also { assertInstanceOf(RdapLookupResult.Found::class.java, it) }

        assertEquals("/bootstrap", server.takeRequest().url.encodedPath)
        assertEquals("/idn/domain/xn--bcher-kva", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `domain service prefers HTTPS base within longest suffix service`() = runTest {
        val requested = mutableListOf<String>()
        val bootstrapUrl = "https://iana.example.test/dns.json".toHttpUrl()
        val bootstrap = """{"services":[[["com"],["http://insecure.example.test/rdap", "https://secure.example.test/rdap/"]]]}"""
        val transport = RdapTransport { url ->
            if (url == bootstrapUrl) RdapHttpResponse(200, bootstrap, url.toString())
            else {
                requested += url.toString()
                RdapHttpResponse(200, "{}", url.toString())
            }
        }
        val client = RdapClient(transport, bootstrapUrl = bootstrapUrl)

        client.lookup("a.example.com", WhoisQueryType.DOMAIN)

        assertEquals("https://secure.example.test/rdap/domain/a.example.com", requested.single())
    }

    @Test
    fun `IP ASN redirectors and 404 result are typed`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().code(404).body("not found").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        val client = RdapClient(
            OkHttpClient(),
            ipRedirectorUrl = server.url("/"),
            asnRedirectorUrl = server.url("/"),
            bootstrapUrl = "https://data.iana.org/rdap/dns.json".toHttpUrl(),
            nowNanos = System::nanoTime,
            dns = Dns.SYSTEM,
            allowPrivateHostnamesForTests = setOf("localhost", "127.0.0.1", "::1"),
        )

        val missing = client.lookup("8.8.8.8", WhoisQueryType.IPV4)
        val asn = client.lookup("as15169", WhoisQueryType.ASN)

        assertInstanceOf(RdapLookupResult.Unsupported::class.java, missing)
        assertEquals(404, (missing as RdapLookupResult.Unsupported).statusCode)
        assertInstanceOf(RdapLookupResult.Found::class.java, asn)
        assertEquals("/ip/8.8.8.8", server.takeRequest().url.encodedPath)
        assertEquals("/autnum/15169", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `cache max age is capped at one day and zero freshness refetches`() = runTest {
        val server = server()
        var now = 1_000L
        val bootstrap = """{"services":[[["com"],["${server.url("/rdap")}"]]]}"""
        server.enqueue(MockResponse.Builder().body(bootstrap).addHeader("Cache-Control", "max-age=172800").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        val client = rdapClient(bootstrapUrl = server.url("/bootstrap"), nowNanos = { now })
        client.lookup("example.com", WhoisQueryType.DOMAIN)
        now += TimeUnit.HOURS.toNanos(23)
        client.lookup("example.com", WhoisQueryType.DOMAIN)
        assertEquals(3, server.requestCount) // one bootstrap, two registry lookups
        now += TimeUnit.HOURS.toNanos(2)
        server.enqueue(MockResponse.Builder().body(bootstrap).addHeader("Cache-Control", "max-age=0").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        client.lookup("example.com", WhoisQueryType.DOMAIN)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `no-cache field directive and stale expires header force bootstrap refetch`() = runTest {
        val server = server()
        val bootstrap = """{"services":[[["com"],["${server.url("/rdap")}"]]]}"""
        server.enqueue(MockResponse.Builder().body(bootstrap).addHeader("Cache-Control", "no-cache=\"etag\"").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        server.enqueue(MockResponse.Builder().body(bootstrap).addHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        val client = rdapClient(bootstrapUrl = server.url("/bootstrap"))

        client.lookup("example.com", WhoisQueryType.DOMAIN)
        client.lookup("example.com", WhoisQueryType.DOMAIN)

        assertEquals(4, server.requestCount) // bootstrap is not retained after either stale directive
    }

    @Test
    fun `concurrent domain lookups share one bootstrap fetch`() = runTest {
        val bootstrapUrl = "https://iana.example.test/dns.json".toHttpUrl()
        val bootstrapFetches = AtomicInteger()
        val bootstrap = """{"services":[[["com"],["https://rdap.example.test/"]]]}"""
        val transport = RdapTransport { url ->
            if (url == bootstrapUrl) {
                bootstrapFetches.incrementAndGet()
                delay(20)
                RdapHttpResponse(200, bootstrap, url.toString())
            } else {
                RdapHttpResponse(200, "{}", url.toString())
            }
        }
        val client = RdapClient(transport, bootstrapUrl = bootstrapUrl)

        listOf(
            async { client.lookup("one.example.com", WhoisQueryType.DOMAIN) },
            async { client.lookup("two.example.com", WhoisQueryType.DOMAIN) },
            async { client.lookup("three.example.com", WhoisQueryType.DOMAIN) },
        ).awaitAll()

        assertEquals(1, bootstrapFetches.get())
    }

    @Test
    fun `concurrent failed bootstrap lookups share failure but later lookup retries`() = runTest {
        val bootstrapUrl = "https://iana.example.test/dns.json".toHttpUrl()
        val bootstrapAttempts = AtomicInteger()
        val transport = RdapTransport { url ->
            if (url == bootstrapUrl) {
                bootstrapAttempts.incrementAndGet()
                delay(20)
                throw IOException("bootstrap unavailable")
            }
            RdapHttpResponse(200, "{}", url.toString())
        }
        val client = RdapClient(transport, bootstrapUrl = bootstrapUrl)

        val concurrentResults = listOf(
            async { runCatching { client.lookup("one.example.com", WhoisQueryType.DOMAIN) } },
            async { runCatching { client.lookup("two.example.com", WhoisQueryType.DOMAIN) } },
            async { runCatching { client.lookup("three.example.com", WhoisQueryType.DOMAIN) } },
        ).awaitAll()

        assertTrue(concurrentResults.all { it.exceptionOrNull() is IOException })
        assertEquals(1, bootstrapAttempts.get())
        runCatching { client.lookup("later.example.com", WhoisQueryType.DOMAIN) }
        assertEquals(2, bootstrapAttempts.get())
    }

    @Test
    fun `oversized responses fail and cancellation cancels pending request`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().body("x".repeat(1024 * 1024 + 1)).build())
        val client = rdapClient(ipRedirectorUrl = server.url("/"))
        val error = runCatching { client.lookup("8.8.8.8", WhoisQueryType.IPV4) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("size limit"))

        server.enqueue(MockResponse.Builder().body("later").bodyDelay(5, TimeUnit.SECONDS).build())
        val request = async { client.lookup("1.1.1.1", WhoisQueryType.IPV4) }
        server.takeRequest()
        request.cancel()
        delay(50)
        assertTrue(request.isCancelled)
    }

    @Test
    fun `partial response read failure reports bytes already received`() = runTest {
        val server = server()
        server.enqueue(MockResponse.Builder().body(object : MockResponseBody {
            override val contentLength: Long = 100

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("partial")
                sink.flush()
                throw IOException("simulated truncated response")
            }
        }).build())
        val client = rdapClient(ipRedirectorUrl = server.url("/"))
        var observedBytes = 0

        val failure = runCatching {
            client.lookup("8.8.8.8", WhoisQueryType.IPV4) { observedBytes += it }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("partial".toByteArray(Charsets.UTF_8).size, observedBytes)
    }

    @Test
    fun `HTTPS redirect cannot downgrade to HTTP`() = runTest {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val secure = MockWebServer().also {
            it.protocols = listOf(Protocol.HTTP_1_1)
            it.useHttps(serverTls.sslSocketFactory())
            it.start()
            servers += it
        }
        val insecure = server()
        secure.enqueue(MockResponse.Builder().code(302).addHeader("Location", insecure.url("/should-not-hit").toString()).build())
        val client = RdapClient(
            OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build(),
            ipRedirectorUrl = secure.url("/"),
            bootstrapUrl = "https://data.iana.org/rdap/dns.json".toHttpUrl(),
            asnRedirectorUrl = "https://rdap.org/".toHttpUrl(),
            nowNanos = System::nanoTime,
            dns = Dns.SYSTEM,
            allowPrivateHostnamesForTests = setOf("localhost"),
        )

        val failure = runCatching { client.lookup("8.8.8.8", WhoisQueryType.IPV4) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(1, secure.requestCount)
        assertEquals(0, insecure.requestCount)
    }

    @Test
    fun `default DNS policy rejects loopback private and unique local targets`() {
        val localDns = PublicInternetDns(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
        val privateDns = PublicInternetDns(Dns { listOf(InetAddress.getByName("10.0.0.1")) })
        val uniqueLocalDns = PublicInternetDns(Dns { listOf(InetAddress.getByName("fc00::1")) })
        val documentationDns = PublicInternetDns(Dns { listOf(InetAddress.getByName("192.0.2.1")) })
        val documentationV6Dns = PublicInternetDns(Dns { listOf(InetAddress.getByName("2001:db8::1")) })
        val sixToFourDns = PublicInternetDns(Dns { listOf(InetAddress.getByName("2002:7f00:1::1")) })

        assertThrows(UnknownHostException::class.java) { localDns.lookup("loopback.example") }
        assertThrows(UnknownHostException::class.java) { privateDns.lookup("private.example") }
        assertThrows(UnknownHostException::class.java) { uniqueLocalDns.lookup("private-v6.example") }
        assertThrows(UnknownHostException::class.java) { documentationDns.lookup("docs.example") }
        assertThrows(UnknownHostException::class.java) { documentationV6Dns.lookup("docs-v6.example") }
        assertThrows(UnknownHostException::class.java) { sixToFourDns.lookup("6to4.example") }
    }

    @Test
    fun `redirect to local resolving host is blocked before destination request`() = runTest {
        val server = MockWebServer().also {
            it.start()
            it.enqueue(MockResponse.Builder().code(302)
                .addHeader("Location", "http://private.example.test:${it.port}/should-not-hit").build())
            servers += it
        }
        val dns = Dns { hostname ->
            if (hostname == "private.example.test") listOf(InetAddress.getByName("127.0.0.1"))
            else Dns.SYSTEM.lookup(hostname)
        }
        val client = OkHttpClient.Builder().dns(dns).build()
        val securePolicyClient = RdapClient(
            client,
            bootstrapUrl = "https://data.iana.org/rdap/dns.json".toHttpUrl(),
            ipRedirectorUrl = server.url("/"),
            asnRedirectorUrl = "https://rdap.org/".toHttpUrl(),
            nowNanos = System::nanoTime,
            dns = dns,
            allowPrivateHostnamesForTests = setOf("localhost"),
        )

        val failure = runCatching { securePolicyClient.lookup("8.8.8.8", WhoisQueryType.IPV4) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `configured proxy cannot resolve a private RDAP redirect on behalf of the client`() = runTest {
        val origin = server()
        val proxy = server()
        origin.enqueue(MockResponse.Builder().code(302)
            .addHeader("Location", "http://private.example.test:${origin.port}/should-not-hit").build())
        proxy.enqueue(MockResponse.Builder().body("{}").build())
        val dns = Dns { hostname ->
            if (hostname == "private.example.test") listOf(InetAddress.getByName("127.0.0.1"))
            else Dns.SYSTEM.lookup(hostname)
        }
        val okHttp = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", proxy.port)))
            .dns(dns)
            .build()
        val client = RdapClient(
            okHttp,
            bootstrapUrl = "https://data.iana.org/rdap/dns.json".toHttpUrl(),
            ipRedirectorUrl = origin.url("/"),
            asnRedirectorUrl = "https://rdap.org/".toHttpUrl(),
            nowNanos = System::nanoTime,
            dns = dns,
            allowPrivateHostnamesForTests = setOf("localhost"),
        )

        val failure = runCatching { client.lookup("8.8.8.8", WhoisQueryType.IPV4) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1, origin.requestCount)
        assertEquals(0, proxy.requestCount)
    }

    private fun server(): MockWebServer = MockWebServer().also { it.start(); servers += it }

    private fun rdapClient(
        bootstrapUrl: okhttp3.HttpUrl = "https://data.iana.org/rdap/dns.json".toHttpUrl(),
        ipRedirectorUrl: okhttp3.HttpUrl = "https://rdap.org/".toHttpUrl(),
        nowNanos: () -> Long = System::nanoTime,
    ): RdapClient = RdapClient(
        OkHttpClient(),
        bootstrapUrl,
        ipRedirectorUrl,
        "https://rdap.org/".toHttpUrl(),
        nowNanos,
        Dns.SYSTEM,
        setOf("localhost", "127.0.0.1", "::1"),
    )
}
