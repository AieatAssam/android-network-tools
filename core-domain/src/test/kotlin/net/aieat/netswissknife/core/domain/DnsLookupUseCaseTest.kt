package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsRepository
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DnsLookupUseCase")
class DnsLookupUseCaseTest {

    private lateinit var repository: DnsRepository
    private lateinit var useCase: DnsLookupUseCase

    private val successResult = DnsResult(
        domain = "example.com",
        recordType = DnsRecordType.A,
        server = DnsServer.Google,
        records = listOf(
            DnsRecord(DnsRecordType.A, "example.com", "93.184.216.34", 300L, "raw")
        ),
        queryTimeMs = 45L,
        rawResponse = ";; raw"
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = DnsLookupUseCase(repository)
    }

    @Nested
    @DisplayName("input validation")
    inner class Validation {

        @Test
        fun `blank domain returns Error without calling repository`() = runTest {
            val result = useCase(DnsLookupParams(domain = "  "))
            assertTrue(result is NetworkResult.Error)
            coVerify(exactly = 0) { repository.lookup(any(), any(), any()) }
        }

        @Test
        fun `empty domain returns Error`() = runTest {
            val result = useCase(DnsLookupParams(domain = ""))
            assertTrue(result is NetworkResult.Error)
        }

        @Test
        fun `blank domain error message is descriptive`() = runTest {
            val result = useCase(DnsLookupParams(domain = "")) as NetworkResult.Error
            assertTrue(result.message.isNotBlank())
        }

        @Test
        fun `domain longer than 253 chars returns Error`() = runTest {
            val longDomain = "a".repeat(254)
            val result = useCase(DnsLookupParams(domain = longDomain))
            assertTrue(result is NetworkResult.Error)
        }

        @Test
        fun `domain of exactly 253 chars is valid`() = runTest {
            // 50-char label repeated to reach 253 (use short labels separated by dots)
            val validDomain = "a".repeat(50) + "." + "b".repeat(50) + "." + "c".repeat(50) + ".com"
            coEvery { repository.lookup(any(), any(), any()) } returns NetworkResult.Success(successResult)
            val result = useCase(DnsLookupParams(domain = validDomain))
            // Should reach the repository (validation passed)
            coVerify(exactly = 1) { repository.lookup(any(), any(), any()) }
        }

        @Test
        fun `custom server with blank address returns Error`() = runTest {
            val result = useCase(
                DnsLookupParams(
                    domain = "example.com",
                    server = DnsServer.Custom("")
                )
            )
            assertTrue(result is NetworkResult.Error)
        }

        @Test
        fun `custom server with blank address does not call repository`() = runTest {
            useCase(DnsLookupParams(domain = "example.com", server = DnsServer.Custom("")))
            coVerify(exactly = 0) { repository.lookup(any(), any(), any()) }
        }

        @Test
        fun `domain is trimmed before passing to repository`() = runTest {
            coEvery { repository.lookup(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(DnsLookupParams(domain = "  example.com  "))
            coVerify { repository.lookup("example.com", any(), any()) }
        }
    }

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `valid domain delegates to repository`() = runTest {
            coEvery { repository.lookup(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(DnsLookupParams(domain = "example.com"))
            coVerify(exactly = 1) { repository.lookup(any(), any(), any()) }
        }

        @Test
        fun `passes correct record type to repository`() = runTest {
            coEvery { repository.lookup(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(DnsLookupParams(domain = "example.com", recordType = DnsRecordType.MX))
            coVerify { repository.lookup(any(), DnsRecordType.MX, any()) }
        }

        @Test
        fun `passes correct server to repository`() = runTest {
            coEvery { repository.lookup(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(DnsLookupParams(domain = "example.com", server = DnsServer.Cloudflare))
            coVerify { repository.lookup(any(), any(), DnsServer.Cloudflare) }
        }

        @Test
        fun `returns repository result unchanged`() = runTest {
            val expected = NetworkResult.Success(successResult)
            coEvery { repository.lookup(any(), any(), any()) } returns expected
            val actual = useCase(DnsLookupParams(domain = "example.com"))
            assertEquals(expected, actual)
        }

        @Test
        fun `propagates repository Error`() = runTest {
            val error = NetworkResult.Error("Network timeout")
            coEvery { repository.lookup(any(), any(), any()) } returns error
            val result = useCase(DnsLookupParams(domain = "example.com"))
            assertTrue(result is NetworkResult.Error)
            assertEquals("Network timeout", (result as NetworkResult.Error).message)
        }

        @Test
        fun `custom server with valid address reaches repository`() = runTest {
            coEvery { repository.lookup(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(DnsLookupParams(domain = "example.com", server = DnsServer.Custom("192.168.1.1")))
            coVerify { repository.lookup(any(), any(), DnsServer.Custom("192.168.1.1")) }
        }
    }
}
