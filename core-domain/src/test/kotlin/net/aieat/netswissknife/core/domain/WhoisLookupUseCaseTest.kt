package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisRepository
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WhoisLookupUseCase")
class WhoisLookupUseCaseTest {

    private lateinit var repository: WhoisRepository
    private lateinit var useCase: WhoisLookupUseCase

    private val successResult = WhoisResult(
        query = "example.com",
        queryType = WhoisQueryType.DOMAIN,
        hops = emptyList(),
        domainName = "EXAMPLE.COM",
        registrar = "Test Registrar",
        registrarUrl = null,
        registeredOn = null,
        expiresOn = null,
        updatedOn = null,
        nameServers = emptyList(),
        statusCodes = emptyList(),
        registrantOrg = null,
        registrantCountry = null,
        dnssec = null,
        netName = null,
        netRange = null,
        orgName = null,
        country = null,
        totalQueryTimeMs = 42L
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        every { repository.hopProgress } returns MutableSharedFlow()
        useCase = WhoisLookupUseCase(repository)
    }

    @Test
    @DisplayName("blank query returns Error without calling repository")
    fun `blank query returns Error without calling repository`() = runTest {
        val result = useCase(WhoisParams(query = "  "))
        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { repository.lookup(any(), any()) }
    }

    @Test
    @DisplayName("timeout 499 returns Error without calling repository")
    fun `timeout 499 returns Error without calling repository`() = runTest {
        val result = useCase(WhoisParams(query = "example.com", timeoutMs = 499))
        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { repository.lookup(any(), any()) }
    }

    @Test
    @DisplayName("valid params delegates to repository and returns result unchanged")
    fun `valid params delegates to repository and returns result unchanged`() = runTest {
        val expected = NetworkResult.Success(successResult)
        coEvery { repository.lookup(any(), any()) } returns expected
        val actual = useCase(WhoisParams(query = "example.com"))
        assertEquals(expected, actual)
        coVerify(exactly = 1) { repository.lookup("example.com", 10_000) }
    }

    @Test
    @DisplayName("caller-owned operation session is forwarded to repository")
    fun `caller-owned operation session is forwarded to repository`() = runTest {
        val session = OperationSession(OperationBudget.start(requirement = OperationRequirement.INTERNET))
        val expected = NetworkResult.Success(successResult)
        coEvery { repository.lookup(any(), any(), any()) } returns expected

        val actual = useCase(WhoisParams(query = " example.com "), session)

        assertEquals(expected, actual)
        coVerify(exactly = 1) { repository.lookup("example.com", 10_000, session) }
    }

    @Test
    @DisplayName("IDN domain is normalized to lowercase ASCII without a root dot")
    fun `IDN domain is normalized to lowercase ASCII without a root dot`() = runTest {
        val expected = NetworkResult.Success(successResult)
        coEvery { repository.lookup(any(), any()) } returns expected

        val actual = useCase(WhoisParams(query = " BÜCHER.DE. "))

        assertEquals(expected, actual)
        coVerify(exactly = 1) { repository.lookup("xn--bcher-kva.de", 10_000) }
    }

    @Test
    @DisplayName("spaces, control characters, and malformed IPv4 are rejected before repository access")
    fun `spaces controls and malformed IPv4 are rejected before repository access`() = runTest {
        listOf(
            "foo bar",
            "a\r\nb",
            "999.1.1.1",
            "1.2.3",
            "1.2.3.4.5",
        ).forEach { input ->
            val result = useCase(WhoisParams(query = input))
            assertTrue(result is NetworkResult.Error, "expected '$input' to be rejected")
        }
        coVerify(exactly = 0) { repository.lookup(any(), any()) }
    }

    @Test
    @DisplayName("valid IPv4, IPv6, and ASN queries remain supported")
    fun `valid IP and ASN queries remain supported`() = runTest {
        coEvery { repository.lookup(any(), any()) } returns NetworkResult.Success(successResult)

        useCase(WhoisParams(query = "8.8.8.8"))
        useCase(WhoisParams(query = "2001:4860:4860::8888"))
        useCase(WhoisParams(query = "as15169"))

        coVerify(exactly = 1) { repository.lookup("8.8.8.8", 10_000) }
        coVerify(exactly = 1) { repository.lookup("2001:4860:4860::8888", 10_000) }
        coVerify(exactly = 1) { repository.lookup("AS15169", 10_000) }
    }
}
