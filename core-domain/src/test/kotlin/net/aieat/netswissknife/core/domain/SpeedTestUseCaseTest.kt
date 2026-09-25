package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestRepository
import net.aieat.netswissknife.core.network.speedtest.SpeedTestConfig
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SpeedTestUseCase")
class SpeedTestUseCaseTest {

    private class FakeRepository(private val events: List<SpeedTestEvent>) : SpeedTestRepository {
        override fun runSpeedTest(): Flow<SpeedTestEvent> = flowOf(*events.toTypedArray())
    }

    @Test
    fun `delegates to the repository and streams its events unchanged`() = runTest {
        val expected = listOf(
            SpeedTestEvent.LatencyFinished(LatencyStats.EMPTY)
        )
        val useCase = SpeedTestUseCase(FakeRepository(expected))

        assertEquals(expected, useCase().toList())
    }

    @Test
    fun `forwards caller-owned operation session`() = runTest {
        val expected = listOf(SpeedTestEvent.LatencyFinished(LatencyStats.EMPTY))
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000))
        var received: OperationSession? = null
        val repository = object : SpeedTestRepository {
            override fun runSpeedTest(): Flow<SpeedTestEvent> = flowOf(*expected.toTypedArray())
            override fun runSpeedTest(operationSession: OperationSession): Flow<SpeedTestEvent> {
                received = operationSession
                return flowOf(*expected.toTypedArray())
            }
        }

        assertEquals(expected, SpeedTestUseCase(repository)(session).toList())
        assertEquals(session, received)
    }

    @Test
    fun `forwards per-run stream configuration`() = runTest {
        val expected = listOf(SpeedTestEvent.LatencyFinished(LatencyStats.EMPTY))
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000))
        val config = SpeedTestConfig(downloadStreams = 6, uploadStreams = 3)
        var received: SpeedTestConfig? = null
        val repository = object : SpeedTestRepository {
            override fun runSpeedTest(): Flow<SpeedTestEvent> = flowOf(*expected.toTypedArray())
            override fun runSpeedTest(operationSession: OperationSession, config: SpeedTestConfig): Flow<SpeedTestEvent> {
                received = config
                return flowOf(*expected.toTypedArray())
            }
        }

        assertEquals(expected, SpeedTestUseCase(repository)(session, config).toList())
        assertEquals(config, received)
    }
}
