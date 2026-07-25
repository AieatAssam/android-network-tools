package net.aieat.netswissknife.app.ui.screens.speedtest

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.SpeedTestUseCase
import net.aieat.netswissknife.core.network.speedtest.LatencySample
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestPhase
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SpeedTestViewModel")
class SpeedTestViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: SpeedTestUseCase
    private lateinit var viewModel: SpeedTestViewModel

    private val latencySample = LatencySample(sequence = 1, rtTimeMs = 20L)
    private val latencyStats = LatencyStats.compute(listOf(latencySample))
    private val downloadSample = ThroughputSample(elapsedMs = 100, bytesTransferred = 1000, instantMbps = 8.0)
    private val downloadResult = ThroughputResult.from(1000, 100, listOf(downloadSample))
    private val uploadSample = ThroughputSample(elapsedMs = 100, bytesTransferred = 500, instantMbps = 4.0)
    private val uploadResult = ThroughputResult.from(500, 100, listOf(uploadSample))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        viewModel = SpeedTestViewModel(useCase)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts Idle`() {
            assertTrue(viewModel.uiState.value is SpeedTestUiState.Idle)
        }
    }

    @Nested
    @DisplayName("startTest")
    inner class StartTest {

        @Test
        fun `Running state tracks latency progress`() = runTest {
            every { useCase() } returns flowOf(
                SpeedTestEvent.LatencyProgress(latencySample, total = 5)
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Running
            assertEquals(SpeedTestPhase.LATENCY, state.phase)
            assertEquals(latencyStats, state.latencyStats)
        }

        @Test
        fun `advances through phases as events arrive`() = runTest {
            every { useCase() } returns flowOf(
                SpeedTestEvent.LatencyFinished(latencyStats),
                SpeedTestEvent.DownloadProgress(downloadSample),
                SpeedTestEvent.DownloadFinished(downloadResult),
                SpeedTestEvent.UploadProgress(uploadSample)
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Running
            assertEquals(SpeedTestPhase.UPLOAD, state.phase)
            assertEquals(downloadResult, state.downloadResult)
            assertEquals(listOf(uploadSample), state.uploadSamples)
        }

        @Test
        fun `Finished on UploadFinished combines all phase results`() = runTest {
            every { useCase() } returns flowOf(
                SpeedTestEvent.LatencyFinished(latencyStats),
                SpeedTestEvent.DownloadFinished(downloadResult),
                SpeedTestEvent.UploadFinished(uploadResult)
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Finished
            assertEquals(latencyStats, state.result.latency)
            assertEquals(downloadResult, state.result.download)
            assertEquals(uploadResult, state.result.upload)
        }

        @Test
        fun `Error state on Failed event`() = runTest {
            every { useCase() } returns flowOf(
                SpeedTestEvent.Failed(SpeedTestPhase.DOWNLOAD, "connection reset")
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Error
            assertEquals(SpeedTestPhase.DOWNLOAD, state.phase)
            assertEquals("connection reset", state.message)
        }

    }

    @Nested
    @DisplayName("onCancel / onRetry")
    inner class CancelAndRetry {

        @Test
        fun `onCancel resets to Idle`() = runTest {
            every { useCase() } returns flowOf(
                SpeedTestEvent.LatencyProgress(latencySample, total = 5)
            )
            viewModel.startTest()

            viewModel.onCancel()

            assertTrue(viewModel.uiState.value is SpeedTestUiState.Idle)
        }

        @Test
        fun `onRetry re-invokes the use case`() = runTest {
            every { useCase() } returns flowOf(
                SpeedTestEvent.Failed(SpeedTestPhase.LATENCY, "timeout")
            )
            viewModel.startTest()

            every { useCase() } returns flowOf(
                SpeedTestEvent.LatencyProgress(latencySample, total = 5)
            )
            viewModel.onRetry()

            assertTrue(viewModel.uiState.value is SpeedTestUiState.Running)
        }
    }
}
