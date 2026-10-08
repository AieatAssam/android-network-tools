package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationSession
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicInteger

class WhoisRdapIntegrationTest {
    private val bootstrapUrl = "https://iana.example.test/dns.json".toHttpUrl()
    private val redirectorUrl = "https://rdap.example.test/".toHttpUrl()

    @Test
    fun `mapped domain IP and ASN results emit one RDAP hop with operation id`() = runTest {
        val examples = listOf(
            Triple("example.com", WhoisQueryType.DOMAIN, """{"objectClassName":"domain","ldhName":"example.com"}"""),
            Triple("8.8.8.8", WhoisQueryType.IPV4, """{"objectClassName":"ip network","ipVersion":"v4","name":"PUBLIC"}"""),
            Triple("AS15169", WhoisQueryType.ASN, """{"objectClassName":"autnum","name":"GOOGLE","startAutnum":15169,"endAutnum":15169}"""),
        )
        for ((query, type, body) in examples) {
            val rdap = client { url ->
                if (url == bootstrapUrl) bootstrapResponse()
                else RdapHttpResponse(200, body, url.toString())
            }
            val repo = repository(rdap)
            val session = OperationSession(OperationBudget.start())
            val progress = async(start = CoroutineStart.UNDISPATCHED) { repo.hopProgress.take(1).toList() }

            val result = repo.lookup(query, 2_000, session)

            assertInstanceOf(NetworkResult.Success::class.java, result)
            val mapped = (result as NetworkResult.Success).data
            assertEquals(type, mapped.queryType)
            assertEquals(1, mapped.hops.size)
            assertEquals(WhoisServerRole.RDAP, mapped.hops.single().server.role)
            assertEquals(session.budget.operationId, mapped.hops.single().operationId)
            assertEquals(session.budget.operationId, progress.await().single().operationId)
        }
    }

    @Test
    fun `domain RDAP requests use registrable domain and preserve normalized input`() = runTest {
        val requested = mutableListOf<String>()
        val rdap = client { url ->
            if (url == bootstrapUrl) bootstrapResponse()
            else {
                requested += url.encodedPath
                RdapHttpResponse(200, """{"objectClassName":"domain","ldhName":"example.com"}""", url.toString())
            }
        }
        val result = repository(rdap).lookup("WWW.Example.COM.", 2_000)

        assertInstanceOf(NetworkResult.Success::class.java, result)
        val mapped = (result as NetworkResult.Success).data
        assertEquals("www.example.com", mapped.query)
        assertEquals(listOf("/domain/example.com"), requested)
    }

    @Test
    fun `not found network and malformed RDAP responses fall back to WHOIS without RDAP progress`() = runTest {
        val failures: List<(HttpUrl) -> RdapHttpResponse> = listOf(
            { url -> if (url == bootstrapUrl) bootstrapResponse() else RdapHttpResponse(404, "", url.toString()) },
            { throw IOException("offline") },
            { url -> if (url == bootstrapUrl) bootstrapResponse() else RdapHttpResponse(200, "{bad", url.toString()) },
        )

        for (failure in failures) {
            val sockets = AtomicInteger()
            val repo = repository(client(failure), sockets, listOf("NetName: TEST-NET\r\n"))
            val session = OperationSession(OperationBudget.start())

            val result = repo.lookup("8.8.8.8", 2_000, session)

            assertInstanceOf(NetworkResult.Success::class.java, result)
            val data = (result as NetworkResult.Success).data
            assertEquals(WhoisServerRole.RIR, data.hops.first().server.role)
            assertEquals("TEST-NET", data.netName)
            assertEquals(1, sockets.get())
            assertFalse(data.hops.any { it.server.role == WhoisServerRole.RDAP })
        }
    }

    @Test
    fun `forced WHOIS bypasses RDAP and forced RDAP failure does not fall back`() = runTest {
        val whoisSockets = AtomicInteger()
        val rdapRequests = AtomicInteger()
        val rdapSuccess = client { url ->
            rdapRequests.incrementAndGet()
            RdapHttpResponse(200, """{"objectClassName":"ip network","ipVersion":"v4","name":"RDAP"}""", url.toString())
        }
        val whoisRepository = repository(rdapSuccess, whoisSockets, listOf("NetName: WHOIS\r\n"))

        val whoisResult = whoisRepository.lookup("8.8.8.8", 2_000, WhoisProtocol.WHOIS)

        assertInstanceOf(NetworkResult.Success::class.java, whoisResult)
        assertEquals("WHOIS", (whoisResult as NetworkResult.Success).data.netName)
        assertEquals(0, rdapRequests.get())
        assertEquals(1, whoisSockets.get())

        val forcedRdapSockets = AtomicInteger()
        val failingRdap = client { throw IOException("RDAP offline") }
        val forcedRdapResult = repository(failingRdap, forcedRdapSockets).lookup(
            "8.8.8.8",
            2_000,
            WhoisProtocol.RDAP,
        )

        assertInstanceOf(NetworkResult.Error::class.java, forcedRdapResult)
        assertEquals(0, forcedRdapSockets.get())
    }

    @Test
    fun `progress is not emitted for malformed RDAP before WHOIS fallback`() = runTest {
        val sockets = AtomicInteger()
        val repo = repository(
            client { url -> if (url == bootstrapUrl) bootstrapResponse() else RdapHttpResponse(200, "{}", url.toString()) },
            sockets,
            listOf("NetName: TEST\r\n"),
        )
        val progress = async(start = CoroutineStart.UNDISPATCHED) { repo.hopProgress.take(1).toList() }

        val result = repo.lookup("8.8.8.8", 2_000)

        assertInstanceOf(NetworkResult.Success::class.java, result)
        assertEquals(WhoisServerRole.RIR, progress.await().single().server.role)
    }

    @Test
    fun `RDAP deadline remains terminal and does not run WHOIS fallback`() = runTest {
        val sockets = AtomicInteger()
        val rdap = client { throw OperationDeadlineExceededException() }
        val repo = repository(rdap, sockets)

        val result = repo.lookup("8.8.8.8", 2_000)

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertTrue((result as NetworkResult.Error).message.contains("deadline", ignoreCase = true))
        assertEquals(0, sockets.get())
    }

    @Test
    fun `parent cancellation cancels RDAP and is not converted to fallback`() = runTest {
        val sockets = AtomicInteger()
        val repo = repository(client { awaitCancellation() }, sockets)
        val lookup = async { repo.lookup("8.8.8.8", 2_000) }
        yield()

        lookup.cancel(CancellationException("test cancellation"))

        assertTrue(runCatching { lookup.await() }.exceptionOrNull() is CancellationException)
        assertEquals(0, sockets.get())
    }

    @Test
    fun `RDAP bytes count against aggregate response budget before fallback`() = runTest {
        val sockets = AtomicInteger()
        val raw = """{"objectClassName":"ip network","ipVersion":"v4","name":"${"x".repeat(100)}"}"""
        val repo = repository(client { url -> RdapHttpResponse(200, raw, url.toString()) }, sockets)
        val session = OperationSession(OperationBudget.start(maxResponseBytes = 32))

        val result = repo.lookup("8.8.8.8", 2_000, session)

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertTrue((result as NetworkResult.Error).message.contains("exceeded", ignoreCase = true))
        assertEquals(0, sockets.get())
    }

    @Test
    fun `bytes from a truncated RDAP body count before WHOIS fallback`() = runTest {
        for ((partialBytes, expectFallback) in listOf(40 to false, 8 to true)) {
            val sockets = AtomicInteger()
            val rdap = client { throw RdapResponseReadException(partialBytes, IOException("truncated body")) }
            val repo = repository(rdap, sockets, listOf("NetName: TEST\r\n"))
            val session = OperationSession(OperationBudget.start(maxResponseBytes = 32))

            val result = repo.lookup("8.8.8.8", 2_000, session)

            if (expectFallback) {
                assertInstanceOf(NetworkResult.Success::class.java, result)
                assertEquals(1, sockets.get())
            } else {
                assertInstanceOf(NetworkResult.Error::class.java, result)
                assertTrue((result as NetworkResult.Error).message.contains("exceeded", ignoreCase = true))
                assertEquals(0, sockets.get())
            }
        }
    }

    @Test
    fun `404 and other HTTP bodies count against aggregate response budget`() = runTest {
        for (status in listOf(404, 503)) {
            val sockets = AtomicInteger()
            val repo = repository(
                client { url -> RdapHttpResponse(status, "x".repeat(40), url.toString()) },
                sockets,
            )
            val session = OperationSession(OperationBudget.start(maxResponseBytes = 32))

            val result = repo.lookup("8.8.8.8", 2_000, session)

            assertInstanceOf(NetworkResult.Error::class.java, result)
            assertTrue((result as NetworkResult.Error).message.contains("exceeded", ignoreCase = true))
            assertEquals(0, sockets.get())
        }
    }

    @Test
    fun `bootstrap response bytes count against aggregate operation budget`() = runTest {
        val sockets = AtomicInteger()
        val rdap = client { url ->
            if (url == bootstrapUrl) bootstrapResponse()
            else RdapHttpResponse(200, """{"objectClassName":"domain"}""", url.toString())
        }
        val repo = repository(rdap, sockets)
        val session = OperationSession(OperationBudget.start(maxResponseBytes = 32))

        val result = repo.lookup("example.com", 2_000, session)

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertTrue((result as NetworkResult.Error).message.contains("exceeded", ignoreCase = true))
        assertEquals(0, sockets.get())
    }

    @Test
    fun `oversized RDAP response is terminal and cannot start WHOIS fallback`() = runTest {
        val sockets = AtomicInteger()
        val rdap = client { throw RdapResponseTooLargeException(1_048_577, 1_048_576) }
        val repo = repository(rdap, sockets)
        val session = OperationSession(OperationBudget.start(maxResponseBytes = 2_097_152))

        val result = repo.lookup("8.8.8.8", 2_000, session)

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertTrue((result as NetworkResult.Error).message.contains("size limit", ignoreCase = true))
        assertEquals(0, sockets.get())
    }

    @Test
    fun `oversized bootstrap response is terminal and cannot start WHOIS fallback`() = runTest {
        val sockets = AtomicInteger()
        val rdap = client { throw RdapResponseTooLargeException(1_048_577, 1_048_576) }
        val repo = repository(rdap, sockets)
        val session = OperationSession(OperationBudget.start(maxResponseBytes = 2_097_152))

        val result = repo.lookup("example.com", 2_000, session)

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertTrue((result as NetworkResult.Error).message.contains("size limit", ignoreCase = true))
        assertEquals(0, sockets.get())
    }

    private fun client(handler: suspend (HttpUrl) -> RdapHttpResponse): RdapClient = RdapClient(
        transport = RdapTransport(handler),
        bootstrapUrl = bootstrapUrl,
        ipRedirectorUrl = redirectorUrl,
        asnRedirectorUrl = redirectorUrl,
    )

    private fun bootstrapResponse(): RdapHttpResponse = RdapHttpResponse(
        statusCode = 200,
        body = """{"services":[[["com"],["https://rdap.example.test/"]]]}""",
        finalUrl = bootstrapUrl.toString(),
    )

    private fun repository(
        rdap: RdapClient,
        sockets: AtomicInteger = AtomicInteger(),
        responses: List<String> = emptyList(),
    ): WhoisRepositoryImpl = WhoisRepositoryImpl(
        resolver = WhoisHostResolver { InetAddress.getByName("8.8.8.8") },
        socketFactory = WhoisSocketFactory {
            val index = sockets.incrementAndGet() - 1
            MemorySocket(responses.getOrElse(index) { "" })
        },
        rdapClient = rdap,
    )

    private class MemorySocket(private val response: String) : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = response.byteInputStream()
        override fun close() = Unit
    }
}
