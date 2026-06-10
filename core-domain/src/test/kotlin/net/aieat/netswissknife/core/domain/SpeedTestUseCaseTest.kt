package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestRepository
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
}
