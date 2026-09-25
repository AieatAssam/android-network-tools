package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.testkit.FakeClock
import net.aieat.netswissknife.core.network.whois.WhoisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("WhoisRepositoryImpl – validation")
class WhoisRepositoryImplTest {

    private val repo = WhoisRepositoryImpl()

    @Test
    @DisplayName("lookup returns Error for blank query")
    fun `lookup returns Error for blank query`() = runTest {
        val result = repo.lookup("   ", 5_000)
        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertTrue(error.message.contains("blank", ignoreCase = true))
    }

    @Test
    @DisplayName("lookup returns Error for timeout below 500 ms")
    fun `lookup returns Error for timeout below 500 ms`() = runTest {
        val result = repo.lookup("example.com", 499)
        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(ErrorCode.TIMEOUT_OUT_OF_RANGE, error.info?.code)
        assertEquals(listOf(500, 30_000), error.info?.args)
        assertTrue(error.message.contains("500") && error.message.contains("30 000"))
    }

    @Test
    @DisplayName("lookup returns Error for timeout above 30000 ms")
    fun `lookup returns Error for timeout above 30000 ms`() = runTest {
        val result = repo.lookup("example.com", 30_001)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("invalid inputs are rejected before resolver or socket creation")
    fun `invalid inputs are rejected before resolver or socket creation`() = runTest {
        val resolutions = AtomicInteger()
        val sockets = AtomicInteger()
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver {
                resolutions.incrementAndGet()
                InetAddress.getByName("8.8.8.8")
            },
            socketFactory = WhoisSocketFactory {
                sockets.incrementAndGet()
                Socket()
            },
        )
        val session = OperationSession(OperationBudget.start())

        listOf(
            "foo bar",
            "a\r\nb",
            "999.1.1.1",
            "1.2.3",
            "1.2.3.4.5",
        ).forEach { input ->
            val result = repository.lookup(input, 1_000, session)
            assertTrue(result is NetworkResult.Error, "expected '$input' to be rejected")
        }

        assertEquals(0, resolutions.get())
        assertEquals(0, sockets.get())
    }
}

@DisplayName("WhoisRepositoryImpl – wire-safe input")
class WhoisRepositoryWireInputTest {

    @Test
    @DisplayName("direct IDN lookup sends lowercase A-labels to every WHOIS hop")
    fun `direct IDN lookup sends lowercase A-labels to every WHOIS hop`() = runTest {
        val queries = mutableListOf<ByteArrayOutputStream>()
        val responses = listOf(
            "refer: whois.verisign-grs.com\r\n".toByteArray(),
            "Domain Name: XN--BCHER-KVA.DE\r\n".toByteArray(),
        )
        var socketIndex = 0
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)) },
            socketFactory = WhoisSocketFactory {
                val index = socketIndex++
                val query = ByteArrayOutputStream().also(queries::add)
                object : Socket() {
                    override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
                    override fun getOutputStream() = query
                    override fun getInputStream(): InputStream = responses[index].inputStream()
                    override fun close() = Unit
                }
            },
        )

        val result = withContext(Dispatchers.IO) { repository.lookup(" BÜCHER.DE. ", 1_000) }

        assertTrue(result is NetworkResult.Success, "$result")
        assertEquals(2, queries.size)
        assertEquals(
            listOf("xn--bcher-kva.de\r\n", "xn--bcher-kva.de\r\n"),
            queries.map { it.toString(Charsets.UTF_8) },
        )
    }
}

@DisplayName("WhoisRepositoryImpl – referred RIR failures")
class WhoisReferredRirFailureTest {

    @Test
    @DisplayName("connect failure is retained as a failed referred RIR hop")
    fun `connect failure is retained as a failed referred RIR hop`() = runTest {
        val publicAddress = InetAddress.getByName("8.8.8.8")
        val socketCreates = AtomicInteger()
        val firstHopSocket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getInputStream(): InputStream =
                "NetName: ARIN-NET\r\nReferralServer: whois://whois.ripe.net\r\n".byteInputStream()
            override fun close() = Unit
        }
        val referralSocket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                throw IOException("referral connect failed")
            }
            override fun close() = Unit
        }
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { publicAddress },
            socketFactory = WhoisSocketFactory {
                if (socketCreates.incrementAndGet() == 1) firstHopSocket
                else referralSocket
            },
        )

        val result = withContext(Dispatchers.IO) { repository.lookup("8.8.8.8", 1_000) }

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        val hops = data.hops
        assertEquals(2, hops.size)
        val failedReferral = hops[1]
        assertEquals("whois.ripe.net", failedReferral.server.host)
        assertEquals(WhoisServerRole.RIR, failedReferral.server.role)
        assertTrue(failedReferral.error.orEmpty().contains("referral connect failed"))
        assertNotNull(failedReferral.operationId)
        assertEquals("", failedReferral.rawResponse)
        assertEquals("ARIN-NET", data.netName, "keep fields parsed from the last successful hop")
    }
}

@DisplayName("WhoisRepositoryImpl – registry failures")
class WhoisRegistryFailureTest {

    private val publicAddress = InetAddress.getByName("8.8.8.8")

    @Test
    @DisplayName("registry connect failure is retained and emitted once as a failed hop")
    fun `registry connect failure is retained and emitted once as a failed hop`() = runTest {
        val clock = FakeClock()
        val socketCreates = AtomicInteger()
        val ianaSocket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getInputStream(): InputStream = timedInputStream(
                "refer: whois.verisign-grs.com\nDomain Name: example.com\n",
                clock,
                11,
            )
            override fun close() = Unit
        }
        val registrySocket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                clock.advanceBy(37_000_000L)
                throw IOException("registry connect failed")
            }
            override fun close() = Unit
        }
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { publicAddress },
            socketFactory = WhoisSocketFactory {
                if (socketCreates.incrementAndGet() == 1) ianaSocket else registrySocket
            },
            clock = clock,
        )
        val session = OperationSession(OperationBudget.start(clock = clock))
        val liveProgress = async(start = CoroutineStart.UNDISPATCHED) {
            repository.hopProgress.take(2).toList()
        }

        val result = withContext(Dispatchers.IO) {
            repository.lookup("example.com", 1_000, session)
        }

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertTrue(data.hops.first().rawResponse.contains("Domain Name: example.com"))
        assertEquals(2, data.hops.size)
        assertEquals(11L, data.hops.first().queryTimeMs)
        val failedRegistry = data.hops.last()
        assertEquals("whois.verisign-grs.com", failedRegistry.server.host)
        assertEquals(WhoisServerRole.REGISTRY, failedRegistry.server.role)
        assertEquals("", failedRegistry.rawResponse)
        assertEquals(0L, failedRegistry.queryTimeMs)
        assertEquals(48L, data.totalQueryTimeMs)
        assertTrue(failedRegistry.error.orEmpty().contains("registry connect failed"))
        assertEquals(session.budget.operationId, failedRegistry.operationId)

        val progress = liveProgress.await()
        assertEquals(data.hops, progress)
        assertEquals(1, progress.count { it.server.role == WhoisServerRole.REGISTRY })
    }

    private fun timedInputStream(content: String, clock: FakeClock, millis: Long): InputStream {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return object : InputStream() {
            private var offset = 0
            private var advanced = false

            override fun read(): Int {
                if (!advanced) {
                    advanced = true
                    clock.advanceBy(millis * 1_000_000L)
                }
                return if (offset == bytes.size) -1 else bytes[offset++].toInt() and 0xff
            }

            override fun read(buffer: ByteArray, byteOffset: Int, length: Int): Int {
                if (!advanced) {
                    advanced = true
                    clock.advanceBy(millis * 1_000_000L)
                }
                if (offset == bytes.size) return -1
                val count = minOf(length, bytes.size - offset)
                bytes.copyInto(buffer, byteOffset, offset, offset + count)
                offset += count
                return count
            }
        }
    }

    @Test
    @DisplayName("registry cancellation and deadline exceptions do not become failure hops")
    fun `registry cancellation and deadline exceptions do not become failure hops`() = runTest {
        suspend fun runWithRegistryFailure(failure: Exception): Pair<NetworkResult<WhoisResult>?, List<WhoisHop>> {
            val repository = WhoisRepositoryImpl(
                resolver = WhoisHostResolver { host ->
                    if (host == "whois.verisign-grs.com") throw failure
                    publicAddress
                },
                socketFactory = WhoisSocketFactory {
                    object : Socket() {
                        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
                        override fun getOutputStream() = ByteArrayOutputStream()
                        override fun getInputStream(): InputStream =
                            "refer: whois.verisign-grs.com\n".byteInputStream()
                        override fun close() = Unit
                    }
                },
            )
            val liveProgress = async(start = CoroutineStart.UNDISPATCHED) {
                repository.hopProgress.take(1).toList()
            }
            val result = try {
                withContext(Dispatchers.IO) { repository.lookup("example.com", 1_000) }
            } catch (caught: CancellationException) {
                assertTrue(failure is CancellationException)
                assertEquals(failure.message, caught.message)
                null
            }
            return result to liveProgress.await()
        }

        val (cancelledResult, cancellationProgress) = runWithRegistryFailure(
            CancellationException("registry cancelled"),
        )
        assertEquals(null, cancelledResult)
        assertEquals(listOf(WhoisServerRole.IANA), cancellationProgress.map { it.server.role })

        val (deadlineResult, deadlineProgress) = runWithRegistryFailure(OperationDeadlineExceededException())
        assertTrue(deadlineResult is NetworkResult.Error)
        assertTrue((deadlineResult as NetworkResult.Error).message.contains("deadline", ignoreCase = true))
        assertEquals(listOf(WhoisServerRole.IANA), deadlineProgress.map { it.server.role })
    }
}

@DisplayName("WhoisRepositoryImpl – referral SSRF guard")
class WhoisRepositoryReferralGuardTest {

    private val repo = WhoisRepositoryImpl()

    @Test
    @DisplayName("loopback referral address is rejected")
    fun `loopback referral address is rejected`() {
        assertTrue(repo.isDisallowedReferralAddress(java.net.InetAddress.getByName("127.0.0.1")))
    }

    @Test
    @DisplayName("private RFC1918 referral address is rejected")
    fun `private RFC1918 referral address is rejected`() {
        assertTrue(repo.isDisallowedReferralAddress(java.net.InetAddress.getByName("192.168.1.1")))
        assertTrue(repo.isDisallowedReferralAddress(java.net.InetAddress.getByName("10.0.0.1")))
        assertTrue(repo.isDisallowedReferralAddress(java.net.InetAddress.getByName("172.16.0.1")))
    }

    @Test
    @DisplayName("link-local referral address is rejected")
    fun `link-local referral address is rejected`() {
        assertTrue(repo.isDisallowedReferralAddress(java.net.InetAddress.getByName("169.254.169.254")))
    }

    @Test
    @DisplayName("public referral address is allowed")
    fun `public referral address is allowed`() {
        assertTrue(!repo.isDisallowedReferralAddress(java.net.InetAddress.getByName("8.8.8.8")))
    }
}

@DisplayName("WhoisRepositoryImpl – subdomain normalisation")
class WhoisRegistrableDomainTest {

    private val repo = WhoisRepositoryImpl()

    @Test
    @DisplayName("simple subdomain strips to eTLD+1")
    fun `simple subdomain strips to eTLD+1`() {
        assertEquals("example.com", repo.extractRegistrableDomain("sub.example.com"))
    }

    @Test
    @DisplayName("deep subdomain strips to eTLD+1")
    fun `deep subdomain strips to eTLD+1`() {
        assertEquals("example.net", repo.extractRegistrableDomain("a.b.example.net"))
    }

    @Test
    @DisplayName("compound public suffix keeps the registrable labels")
    fun `compound public suffix keeps the registrable labels`() {
        assertEquals("example.co.uk", repo.extractRegistrableDomain("sub.example.co.uk"))
    }

    @Test
    @DisplayName("private hosting suffix does not replace the WHOIS registry domain")
    fun `private hosting suffix does not replace the WHOIS registry domain`() {
        assertEquals("github.io", repo.extractRegistrableDomain("a.foo.github.io"))
    }

    @Test
    @DisplayName("bare domain is returned unchanged")
    fun `bare domain is returned unchanged`() {
        assertEquals("example.com", repo.extractRegistrableDomain("example.com"))
    }

    @Test
    @DisplayName("two-label compound TLD domain is returned unchanged")
    fun `two-label compound TLD domain is returned unchanged`() {
        assertEquals("example.co.uk", repo.extractRegistrableDomain("example.co.uk"))
    }

    @Test
    @DisplayName("unknown suffix falls back to the last two labels")
    fun `unknown suffix falls back to last two labels`() {
        assertEquals("bar.unknown-tld", repo.extractRegistrableDomain("foo.bar.unknown-tld"))
    }
}
