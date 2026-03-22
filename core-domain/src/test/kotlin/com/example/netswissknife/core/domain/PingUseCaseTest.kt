package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.ping.PingPacketResult
import com.example.netswissknife.core.network.ping.PingRepository
import com.example.netswissknife.core.network.ping.PingStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PingUseCase")
class PingUseCaseTest {

    private lateinit var repository: PingRepository
    private lateinit var useCase: PingUseCase

    private val samplePacket = PingPacketResult(1, "8.8.8.8", 12L, PingStatus.SUCCESS)

    private fun successFlow(count: Int = 4): Flow<PingPacketResult> = flowOf(
        *Array(count) { i ->
            PingPacketResult(i + 1, "8.8.8.8", (10L + i), PingStatus.SUCCESS)
        }
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = PingUseCase(repository)
    }

    @Nested
    @DisplayName("input validation")
    inner class Validation {

        @Test
        fun `blank host emits error and does not call repository`() = runTest {
            val results = useCase(PingParams(host = "  ")).toList()
            assertTrue(results.first().isError)
            verify(exactly = 0) { repository.ping(any(), any(), any(), any()) }
        }

        @Test
        fun `empty host emits error`() = runTest {
            val results = useCase(PingParams(host = "")).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `invalid host emits error`() = runTest {
            val results = useCase(PingParams(host = "not a valid host!!")).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `count zero emits error`() = runTest {
            val results = useCase(PingParams(host = "8.8.8.8", count = 0)).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `count greater than 100 emits error`() = runTest {
            val results = useCase(PingParams(host = "8.8.8.8", count = 101)).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `timeout less than 100ms emits error`() = runTest {
            val results = useCase(PingParams(host = "8.8.8.8", timeoutMs = 99)).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `timeout greater than 30000ms emits error`() = runTest {
            val results = useCase(PingParams(host = "8.8.8.8", timeoutMs = 30001)).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `packetSize less than 1 emits error`() = runTest {
            val results = useCase(PingParams(host = "8.8.8.8", packetSize = 0)).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `packetSize greater than 65507 emits error`() = runTest {
            val results = useCase(PingParams(host = "8.8.8.8", packetSize = 65508)).toList()
            assertTrue(results.first().isError)
        }

        @Test
        fun `validation error message is descriptive`() = runTest {
            val result = useCase(PingParams(host = "")).toList().first()
            assertTrue(result.isError)
            assertTrue((result as PingFlowResult.ValidationError).message.isNotBlank())
        }
    }

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `valid host delegates to repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            useCase(PingParams(host = "8.8.8.8")).toList()
            verify(exactly = 1) { repository.ping(any(), any(), any(), any()) }
        }

        @Test
        fun `passes host to repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            useCase(PingParams(host = "example.com")).toList()
            verify { repository.ping("example.com", any(), any(), any()) }
        }

        @Test
        fun `passes count to repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow(3)
            useCase(PingParams(host = "8.8.8.8", count = 3)).toList()
            verify { repository.ping(any(), 3, any(), any()) }
        }

        @Test
        fun `passes timeoutMs to repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            useCase(PingParams(host = "8.8.8.8", timeoutMs = 5000)).toList()
            verify { repository.ping(any(), any(), 5000, any()) }
        }

        @Test
        fun `passes packetSize to repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            useCase(PingParams(host = "8.8.8.8", packetSize = 64)).toList()
            verify { repository.ping(any(), any(), any(), 64) }
        }

        @Test
        fun `emits packet results from repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns flowOf(samplePacket)
            val results = useCase(PingParams(host = "8.8.8.8")).toList()
            assertTrue(results.all { it is PingFlowResult.Packet })
        }

        @Test
        fun `packet result carries the packet data`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns flowOf(samplePacket)
            val result = useCase(PingParams(host = "8.8.8.8")).toList().first()
            assertEquals(samplePacket, (result as PingFlowResult.Packet).packet)
        }

        @Test
        fun `host is trimmed before passing to repository`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            useCase(PingParams(host = "  8.8.8.8  ")).toList()
            verify { repository.ping("8.8.8.8", any(), any(), any()) }
        }

        @Test
        fun `valid IPv4 address is accepted`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            val results = useCase(PingParams(host = "192.168.1.1")).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `valid hostname is accepted`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow()
            val results = useCase(PingParams(host = "google.com")).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `count of 1 is valid`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns flowOf(samplePacket)
            val results = useCase(PingParams(host = "8.8.8.8", count = 1)).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `count of 100 is valid`() = runTest {
            every { repository.ping(any(), any(), any(), any()) } returns successFlow(100)
            val results = useCase(PingParams(host = "8.8.8.8", count = 100)).toList()
            assertTrue(results.none { it.isError })
        }
    }
}

/** Extension for readability in tests. */
private val PingFlowResult.isError: Boolean
    get() = this is PingFlowResult.ValidationError
