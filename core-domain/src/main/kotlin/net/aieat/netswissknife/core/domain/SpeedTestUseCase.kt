package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestRepository
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.speedtest.SpeedTestConfig

/** Orchestrates a full latency/download/upload speed test run. */
class SpeedTestUseCase(
    private val repository: SpeedTestRepository
) {
    operator fun invoke(): Flow<SpeedTestEvent> = repository.runSpeedTest()

    operator fun invoke(operationSession: OperationSession): Flow<SpeedTestEvent> =
        repository.runSpeedTest(operationSession)

    operator fun invoke(operationSession: OperationSession, config: SpeedTestConfig): Flow<SpeedTestEvent> =
        repository.runSpeedTest(operationSession, config)
}
