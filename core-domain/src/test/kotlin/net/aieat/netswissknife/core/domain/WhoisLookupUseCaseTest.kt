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
}
