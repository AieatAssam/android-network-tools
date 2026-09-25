package net.aieat.netswissknife.app.ui.screens.speedtest

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.cancel
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.Transport
import net.aieat.netswissknife.core.domain.SpeedTestUseCase
import net.aieat.netswissknife.core.network.speedtest.LatencySample
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestPhase
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import net.aieat.netswissknife.core.network.speedtest.ServerInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SpeedTestViewModel")
class SpeedTestViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: SpeedTestUseCase
    private lateinit var viewModel: SpeedTestViewModel
    private lateinit var dataStore: DataStore<Preferences>
    private val testScope = kotlinx.coroutines.test.TestScope(testDispatcher + Job())
    @TempDir lateinit var tempDir: File

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
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "speedtest.preferences_pb") }
        )
        viewModel = SpeedTestViewModel(useCase, dataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cancel()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts Idle`() {
            assertTrue(viewModel.uiState.value is SpeedTestUiState.Idle)
        }

        @Test
        fun `exposes live network status from provider`() {
            val statusFlow = MutableStateFlow(NetworkStatus(hasInternet = true, transport = Transport.WIFI))
            val provider = object : NetworkStatusProvider {
                override val status = statusFlow.asStateFlow()
            }
            val vm = SpeedTestViewModel(useCase, dataStore, provider)

            assertEquals(NetworkStatus(hasInternet = true, transport = Transport.WIFI), vm.networkStatus.value)
            statusFlow.value = NetworkStatus(hasInternet = false)
            assertEquals(NetworkStatus(hasInternet = false), vm.networkStatus.value)
        }
    }

    @Nested
    @DisplayName("startTest")
    inner class StartTest {

        @Test
        fun `an exception during the test flow surfaces as Error instead of crashing`() = runTest {
            every { useCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
                throw java.io.IOException("connection reset")
            }
            viewModel.startTest()
            val state = viewModel.uiState.value
            assertTrue(state is SpeedTestUiState.Error)
        }

        @Test
        fun `Running state tracks latency progress`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.LatencyProgress(latencySample, total = 5)
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Running
            assertEquals(SpeedTestPhase.LATENCY, state.phase)
            assertEquals(latencyStats, state.latencyStats)
        }

        @Test
        fun `advances through phases as events arrive`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
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
            every { useCase(any(), any()) } returns flowOf(
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
        fun `stream settings persist and are passed to the next run`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.LatencyFinished(latencyStats),
                SpeedTestEvent.DownloadFinished(downloadResult),
                SpeedTestEvent.LoadedLatencyFinished(SpeedTestPhase.DOWNLOAD, latencyStats),
                SpeedTestEvent.LoadedLatencyFinished(SpeedTestPhase.UPLOAD, latencyStats),
                SpeedTestEvent.UploadFinished(uploadResult)
            )
            viewModel.setDownloadStreams(6)
            viewModel.setUploadStreams(3)
            dataStore.data.first { it[AppPreferenceKeys.SPEEDTEST_DOWN_STREAMS] == 6 && it[AppPreferenceKeys.SPEEDTEST_UP_STREAMS] == 3 }

            viewModel.startTest()

            verify { useCase(any(), match { it.downloadStreams == 6 && it.uploadStreams == 3 }) }
            val finished = viewModel.uiState.value as SpeedTestUiState.Finished
            assertEquals(6, finished.result.config.downloadStreams)
            assertEquals(3, finished.result.config.uploadStreams)
            assertEquals(latencyStats, finished.result.loadedLatencyDown)
            assertEquals(latencyStats, finished.result.loadedLatencyUp)
        }

        @Test
        fun `loaded samples and server metadata update the result state`() = runTest {
            val info = ServerInfo(colo = "LHR", asn = "AS13335")
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.ServerInfoReceived(info),
                SpeedTestEvent.LatencyFinished(latencyStats),
                SpeedTestEvent.LoadedLatencySample(SpeedTestPhase.DOWNLOAD, 24),
                SpeedTestEvent.LoadedLatencyFinished(SpeedTestPhase.DOWNLOAD, LatencyStats.compute(listOf(LatencySample(1, 24)))),
                SpeedTestEvent.DownloadFinished(downloadResult),
                SpeedTestEvent.LoadedLatencyFinished(SpeedTestPhase.UPLOAD, LatencyStats.compute(listOf(LatencySample(1, 30)))),
                SpeedTestEvent.UploadFinished(uploadResult)
            )

            viewModel.startTest()

            val result = (viewModel.uiState.value as SpeedTestUiState.Finished).result
            assertEquals(info, result.serverInfo)
            assertEquals(24.0, result.loadedLatencyDown.avgMs)
            assertEquals(30.0, result.loadedLatencyUp.avgMs)
        }

        @Test
        fun `Error state on Failed event`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.Failed(SpeedTestPhase.DOWNLOAD, "connection reset")
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Error
            assertEquals(SpeedTestPhase.DOWNLOAD, state.phase)
            assertEquals("connection reset", state.message)
        }

        @Test
        fun `deadline failure ends the run in Error instead of leaving it Running`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.Failed(SpeedTestPhase.DOWNLOAD, "Speed test timed out")
            )

            viewModel.startTest()

            val state = viewModel.uiState.value as SpeedTestUiState.Error
            assertEquals(SpeedTestPhase.DOWNLOAD, state.phase)
            assertEquals("Speed test timed out", state.message)
        }

    }

    @Nested
    @DisplayName("onCancel / onRetry")
    inner class CancelAndRetry {

        @Test
        fun `onCancel resets to Idle`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.LatencyProgress(latencySample, total = 5)
            )
            viewModel.startTest()

            viewModel.onCancel()

            assertTrue(viewModel.uiState.value is SpeedTestUiState.Idle)
        }

        @Test
        fun `onRetry re-invokes the use case`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.Failed(SpeedTestPhase.LATENCY, "timeout")
            )
            viewModel.startTest()

            every { useCase(any(), any()) } returns flowOf(
                SpeedTestEvent.LatencyProgress(latencySample, total = 5)
            )
            viewModel.onRetry()

            assertTrue(viewModel.uiState.value is SpeedTestUiState.Running)
        }
    }

    @Test
    fun `share output includes server, connect and loaded latency`() {
        val result = net.aieat.netswissknife.core.network.speedtest.SpeedTestResult(
            latency = latencyStats.copy(connectRttMs = 8),
            download = downloadResult,
            upload = uploadResult,
            serverInfo = ServerInfo(colo = "LHR", asn = "AS13335"),
            loadedLatencyDown = LatencyStats.compute(listOf(LatencySample(1, 28))),
            loadedLatencyUp = LatencyStats.compute(listOf(LatencySample(1, 31)))
        )

        val share = buildSpeedTestShareText(result)
        assertTrue(share.contains("Connect RTT: 8 ms"))
        assertTrue(share.contains("Server: LHR · AS13335"))
        assertTrue(share.contains("Latency under download load: 28.0 ms avg / 1 samples"))
        assertTrue(share.contains("Latency under upload load: 31.0 ms avg / 1 samples"))
        assertFalse(share.contains("null"))
    }
}
