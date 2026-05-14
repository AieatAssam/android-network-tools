package net.aieat.netswissknife.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingRepository
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ContinuousPingUseCase")
class ContinuousPingUseCaseTest {

    private lateinit var repository: PingRepository
    private lateinit var useCase: ContinuousPingUseCase

    private val samplePacket = PingPacketResult(1, "8.8.8.8", 12L, PingStatus.SUCCESS)

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = ContinuousPingUseCase(repository)
    }

    @Nested
    @DisplayName("validation")
    inner class Validation {

        @Test
        fun `blank host emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "  ")).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `empty host emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "")).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `invalid host emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "not valid!!")).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `timeout below 100ms emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "8.8.8.8", timeoutMs = 99)).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `timeout above 30000ms emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "8.8.8.8", timeoutMs = 30_001)).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `packetSize below 1 emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "8.8.8.8", packetSize = 0)).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `packetSize above 65507 emits validation error`() = runTest {
            val result = useCase(ContinuousPingParams(host = "8.8.8.8", packetSize = 65_508)).first()
            assertTrue(result is PingFlowResult.ValidationError)
        }

        @Test
        fun `validation error does not call repository`() = runTest {
            useCase(ContinuousPingParams(host = "")).toList()
            verify(exactly = 0) { repository.continuousPing(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `valid host delegates to repository continuousPing`() = runTest {
            every { repository.continuousPing(any(), any(), any()) } returns flowOf(samplePacket)
            useCase(ContinuousPingParams(host = "8.8.8.8")).toList()
            verify(exactly = 1) { repository.continuousPing(any(), any(), any()) }
        }

        @Test
        fun `host is trimmed before passing to repository`() = runTest {
            every { repository.continuousPing(any(), any(), any()) } returns flowOf(samplePacket)
            useCase(ContinuousPingParams(host = "  8.8.8.8  ")).toList()
            verify { repository.continuousPing("8.8.8.8", any(), any()) }
        }

        @Test
        fun `timeoutMs is forwarded to repository`() = runTest {
            every { repository.continuousPing(any(), any(), any()) } returns flowOf(samplePacket)
            useCase(ContinuousPingParams(host = "8.8.8.8", timeoutMs = 5_000)).toList()
            verify { repository.continuousPing(any(), 5_000, any()) }
        }

        @Test
        fun `emitted items are wrapped in Packet`() = runTest {
            every { repository.continuousPing(any(), any(), any()) } returns flowOf(samplePacket)
            val results = useCase(ContinuousPingParams(host = "8.8.8.8")).toList()
            assertTrue(results.all { it is PingFlowResult.Packet })
        }

        @Test
        fun `flow cancellation stops collection`() = runTest {
            every { repository.continuousPing(any(), any(), any()) } returns flow {
                var i = 1
                while (true) emit(samplePacket.copy(sequence = i++))
            }
            val results = useCase(ContinuousPingParams(host = "8.8.8.8")).take(3).toList()
            assertEquals(3, results.size)
        }
    }
}
