package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestRepository

/** Orchestrates a full latency/download/upload speed test run. */
class SpeedTestUseCase(
    private val repository: SpeedTestRepository
) {
    operator fun invoke(): Flow<SpeedTestEvent> = repository.runSpeedTest()
}
