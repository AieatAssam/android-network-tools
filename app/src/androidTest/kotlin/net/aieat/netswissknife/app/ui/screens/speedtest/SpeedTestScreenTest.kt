package net.aieat.netswissknife.app.ui.screens.speedtest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestPhase
import net.aieat.netswissknife.core.network.speedtest.SpeedTestResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Speed Test's help sheet, incremental phase progress, Start/Cancel
 * interactions, Finished result display, and Error+retry.
 */
@RunWith(AndroidJUnit4::class)
class SpeedTestScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun helpSheet_showsConceptHeading() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                SpeedTestScreen(viewModel = fakeViewModel(SpeedTestUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(500L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        // ModalBottomSheet renders in its own semantics root (a separate popup
        // window) that only gets created/attached once real frames are pumped --
        // a paused clock's advanceTimeBy never triggers that. waitUntil polls a
        // bounded condition instead of requiring full idle, so it stays safe even
        // if the underlying screen has its own infinite (e.g. refresh-spin) animation.
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.isRoot())
                .fetchSemanticsNodes().size > 1
        }
        composeRule.mainClock.autoAdvance = false

        composeRule
            .onNodeWithText(context.getString(R.string.help_speedtest_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun runningState_phaseTitleAdvancesAsPhaseChanges() {
        val stateFlow = MutableStateFlow<SpeedTestUiState>(
            SpeedTestUiState.Running(
                phase = SpeedTestPhase.DOWNLOAD,
                downloadSamples = listOf(fakeThroughputSample())
            )
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                SpeedTestScreen(viewModel = fakeViewModel(flow = stateFlow))
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule
            .onNodeWithText(context.getString(R.string.speedtest_running_download))
            .assertIsDisplayed()

        stateFlow.value = SpeedTestUiState.Running(
            phase = SpeedTestPhase.UPLOAD,
            downloadResult = ThroughputResult.EMPTY,
            uploadSamples = listOf(fakeThroughputSample())
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule
            .onNodeWithText(context.getString(R.string.speedtest_running_upload))
            .assertIsDisplayed()
    }

    @Test
    fun startButton_fromIdle_callsStartTest() {
        val viewModel = fakeViewModel(SpeedTestUiState.Idle)
        composeRule.setContent {
            NetSwissKnifeTheme {
                SpeedTestScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.speedtest_start_button))
            .performClick()

        verify(exactly = 1) { viewModel.startTest() }
    }

    @Test
    fun cancelButton_fromRunning_callsOnCancel() {
        val viewModel = fakeViewModel(
            SpeedTestUiState.Running(phase = SpeedTestPhase.LATENCY)
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                SpeedTestScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.speedtest_cancel_button))
            .performClick()

        verify(exactly = 1) { viewModel.onCancel() }
    }

    @Test
    fun finishedState_displaysDownloadAndUploadSpeeds() {
        val result = SpeedTestResult(
            latency = LatencyStats.EMPTY,
            download = ThroughputResult(avgMbps = 123.4, peakMbps = 150.0, bytesTransferred = 1_000_000L, durationMs = 5_000L, samples = emptyList()),
            upload = ThroughputResult(avgMbps = 45.6, peakMbps = 60.0, bytesTransferred = 500_000L, durationMs = 5_000L, samples = emptyList())
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                SpeedTestScreen(viewModel = fakeViewModel(SpeedTestUiState.Finished(result)))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText("123.4 Mbps")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("45.6 Mbps")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun errorState_showsMessageAndRetryCallsOnRetry() {
        val viewModel = fakeViewModel(SpeedTestUiState.Error(SpeedTestPhase.DOWNLOAD, "Connection lost"))
        composeRule.setContent {
            NetSwissKnifeTheme {
                SpeedTestScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithText("Connection lost").assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.speedtest_retry))
            .performClick()

        verify(exactly = 1) { viewModel.onRetry() }
    }

    private fun fakeThroughputSample() = ThroughputSample(elapsedMs = 100L, bytesTransferred = 12_500L, instantMbps = 10.0)

    private fun fakeViewModel(
        state: SpeedTestUiState? = null,
        flow: MutableStateFlow<SpeedTestUiState>? = null
    ): SpeedTestViewModel {
        val viewModel = mockk<SpeedTestViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: SpeedTestUiState.Idle))
        return viewModel
    }
}
