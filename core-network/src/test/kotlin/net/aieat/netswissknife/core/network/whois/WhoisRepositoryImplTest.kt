package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
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
    @DisplayName("compound TLD keeps three labels")
    fun `compound TLD keeps three labels`() {
        assertEquals("example.co.uk", repo.extractRegistrableDomain("sub.example.co.uk"))
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
}
