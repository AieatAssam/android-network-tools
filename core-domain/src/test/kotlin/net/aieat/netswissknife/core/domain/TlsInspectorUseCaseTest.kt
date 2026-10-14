package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.tls.TlsInspectorRepository
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import net.aieat.netswissknife.core.network.tls.TlsInspectorOperation
import net.aieat.netswissknife.core.network.tls.TlsInspectorOptions
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.tls.TlsCertificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TlsInspectorUseCase")
class TlsInspectorUseCaseTest {

    private lateinit var repository: TlsInspectorRepository
    private lateinit var useCase: TlsInspectorUseCase

    private val successResult = TlsInspectorResult(
        host            = "example.com",
        port            = 443,
        tlsVersion      = "TLSv1.3",
        cipherSuite     = "TLS_AES_128_GCM_SHA256",
        chain           = listOf(
            TlsCertificate(
                subjectCN          = "example.com",
                subjectOrg         = "Example Inc",
                issuerCN           = "DigiCert CA",
                issuerOrg          = "DigiCert",
                notBefore          = 0L,
                notAfter           = Long.MAX_VALUE,
                isExpired          = false,
                isSelfSigned       = false,
                sans               = listOf("DNS:example.com"),
                serialNumber       = "DEADBEEF",
                signatureAlgorithm = "SHA256withRSA",
                publicKeyAlgorithm = "RSA",
                publicKeyBits      = 2048,
                sha256Fingerprint  = "AB:CD:EF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC"
            )
        ),
        isChainTrusted  = true,
        handshakeTimeMs = 120L
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase    = TlsInspectorUseCase(repository)
    }

    @Nested
    @DisplayName("input validation")
    inner class Validation {

        @Test
        fun `blank host returns Error without calling repository`() = runTest {
            val result = useCase(TlsInspectorParams(host = "  "))
            assertTrue(result is NetworkResult.Error)
            assertEquals(ErrorCode.HOST_BLANK, (result as NetworkResult.Error).info?.code)
            coVerify(exactly = 0) { repository.inspect(any(), any(), any()) }
        }

        @Test
        fun `empty host returns Error`() = runTest {
            val result = useCase(TlsInspectorParams(host = ""))
            assertTrue(result is NetworkResult.Error)
        }

        @Test
        fun `invalid host returns Error`() = runTest {
            val result = useCase(TlsInspectorParams(host = "not a valid host!!"))
            assertTrue(result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(ErrorCode.HOST_INVALID, error.info?.code)
            assertEquals(listOf("not a valid host!!"), error.info?.args)
        }

        @Test
        fun `port 0 returns Error`() = runTest {
            val result = useCase(TlsInspectorParams(host = "example.com", port = 0))
            assertTrue(result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(ErrorCode.PORT_OUT_OF_RANGE, error.info?.code)
            assertEquals(listOf(0, 1, 65_535), error.info?.args)
            coVerify(exactly = 0) { repository.inspect(any(), any(), any()) }
        }

        @Test
        fun `port 65536 returns Error`() = runTest {
            val result = useCase(TlsInspectorParams(host = "example.com", port = 65_536))
            assertTrue(result is NetworkResult.Error)
            coVerify(exactly = 0) { repository.inspect(any(), any(), any()) }
        }

        @Test
        fun `timeout 499 ms returns Error`() = runTest {
            val result = useCase(TlsInspectorParams(host = "example.com", port = 443, timeoutMs = 499))
            assertTrue(result is NetworkResult.Error)
            coVerify(exactly = 0) { repository.inspect(any(), any(), any()) }
        }

        @Test
        fun `timeout 30001 ms returns Error`() = runTest {
            val result = useCase(TlsInspectorParams(host = "example.com", port = 443, timeoutMs = 30_001))
            assertTrue(result is NetworkResult.Error)
        }

        @Test
        fun `invalid pin returns typed error without calling repository`() = runTest {
            val result = useCase(TlsInspectorParams(host = "example.com", expectedPinSha256 = "not-a-pin"))

            assertTrue(result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals("TLS_PIN_INVALID", error.code)
            assertEquals("tls_pin_invalid", error.descriptionKey)
            assertEquals(ErrorCode.TLS_PIN_INVALID, error.info?.code)
            coVerify(exactly = 0) { repository.inspect(any(), any(), any(), any<TlsInspectorOptions>()) }
        }

        @Test
        fun `pin is trimmed colon-stripped and uppercased before repository call`() = runTest {
            val normalizedPin = "AB".repeat(32)
            coEvery { repository.inspect(any(), any(), any(), any<TlsInspectorOptions>()) } returns
                NetworkResult.Success(successResult)

            useCase(TlsInspectorParams(
                host = "example.com",
                expectedPinSha256 = "  ${"ab:".repeat(31)}ab  ",
            ))

            coVerify(exactly = 1) {
                repository.inspect(
                    "example.com",
                    443,
                    10_000,
                    match<TlsInspectorOptions> { it == TlsInspectorOptions(expectedPinSha256 = normalizedPin) },
                )
            }
        }

        @Test
        fun `protocol probe option is forwarded`() = runTest {
            coEvery { repository.inspect(any(), any(), any(), any<TlsInspectorOptions>()) } returns
                NetworkResult.Success(successResult)

            useCase(TlsInspectorParams(host = "example.com", probeProtocols = true))

            coVerify(exactly = 1) {
                repository.inspect("example.com", 443, 10_000, TlsInspectorOptions(probeProtocols = true))
            }
        }

        @Test
        fun `host is trimmed before validation`() = runTest {
            coEvery { repository.inspect(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(TlsInspectorParams(host = "  example.com  "))
            coVerify { repository.inspect("example.com", any(), any()) }
        }
    }

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `valid params delegates to repository`() = runTest {
            coEvery { repository.inspect(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(TlsInspectorParams(host = "example.com", port = 443, timeoutMs = 10_000))
            coVerify(exactly = 1) { repository.inspect("example.com", 443, 10_000) }
        }

        @Test
        fun `returns repository result unchanged`() = runTest {
            val expected = NetworkResult.Success(successResult)
            coEvery { repository.inspect(any(), any(), any()) } returns expected
            val actual = useCase(TlsInspectorParams(host = "example.com"))
            assertEquals(expected, actual)
        }

        @Test
        fun `caller operation session is forwarded to repository`() = runTest {
            val expected = NetworkResult.Success(successResult)
            val session = TlsInspectorOperation.newSession(10_000)
            coEvery { repository.inspect(any(), any(), any(), any<OperationSession>()) } returns expected

            val actual = useCase(TlsInspectorParams(host = "example.com"), session)

            assertEquals(expected, actual)
            coVerify(exactly = 1) { repository.inspect("example.com", 443, 10_000, session) }
        }

        @Test
        fun `propagates repository Error`() = runTest {
            val error = NetworkResult.Error("Connection refused")
            coEvery { repository.inspect(any(), any(), any()) } returns error
            val result = useCase(TlsInspectorParams(host = "example.com"))
            assertTrue(result is NetworkResult.Error)
            assertEquals("Connection refused", (result as NetworkResult.Error).message)
        }

        @Test
        fun `port boundary 1 is valid`() = runTest {
            coEvery { repository.inspect(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(TlsInspectorParams(host = "example.com", port = 1))
            coVerify(exactly = 1) { repository.inspect(any(), 1, any()) }
        }

        @Test
        fun `port boundary 65535 is valid`() = runTest {
            coEvery { repository.inspect(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(TlsInspectorParams(host = "example.com", port = 65_535))
            coVerify(exactly = 1) { repository.inspect(any(), 65_535, any()) }
        }

        @Test
        fun `timeout boundary 500 ms is valid`() = runTest {
            coEvery { repository.inspect(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(TlsInspectorParams(host = "example.com", port = 443, timeoutMs = 500))
            coVerify(exactly = 1) { repository.inspect(any(), any(), 500) }
        }

        @Test
        fun `timeout boundary 30000 ms is valid`() = runTest {
            coEvery { repository.inspect(any(), any(), any()) } returns NetworkResult.Success(successResult)
            useCase(TlsInspectorParams(host = "example.com", port = 443, timeoutMs = 30_000))
            coVerify(exactly = 1) { repository.inspect(any(), any(), 30_000) }
        }
    }
}
