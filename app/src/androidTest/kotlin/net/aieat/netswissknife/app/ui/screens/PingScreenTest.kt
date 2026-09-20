package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.ping.PingUiState
import net.aieat.netswissknife.app.ui.screens.ping.PingViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Ping's Running -> Error state transition rendering.
 *
 * [PingScreen] requests `NEARBY_WIFI_DEVICES` on entry on API 36+ (Local
 * Network Protections, see `LocalNetworkPermission.kt`) — pre-granting it
 * avoids a system permission dialog interrupting the test.
 */
@RunWith(AndroidJUnit4::class)
class PingScreenTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        GrantPermissionRule.grant(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun runningState_showsHostBeingPinged() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Running(host = "example.com", packets = emptyList(), totalCount = 4)))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(PingScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(PingScreenTestTags.RESULTS_PANEL_INDEX)
        composeRule.onNodeWithText("example.com", substring = true).assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorTitleAndMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Error("Host unreachable")))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(PingScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(PingScreenTestTags.RESULTS_PANEL_INDEX)
        composeRule.onNodeWithText(context.getString(R.string.ping_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Host unreachable").assertIsDisplayed()
    }

    @Test
    fun errorState_withoutMessage_showsHelpfulDetailsPlaceholder() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Error("")))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(PingScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(PingScreenTestTags.RESULTS_PANEL_INDEX)
        composeRule.onNodeWithText(context.getString(R.string.error_no_details)).assertIsDisplayed()
    }

    @Test
    fun helpSheet_showsConceptHeading() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
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
            .onNodeWithText(context.getString(R.string.help_ping_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun hostWithSpace_showsValidationError() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Idle, host = "bad host"))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.error_invalid_host))
            .assertIsDisplayed()
    }

    @Test
    fun advancedOptions_toggleShowsPacketControls() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText(context.getString(R.string.action_expand)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("Payload size: 56 bytes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TTL: 64").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Interval: 1000 ms").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun advancedOptions_toggleHidesPacketControls() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText(context.getString(R.string.action_expand)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(context.getString(R.string.action_collapse)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("Payload size: 56 bytes").assertDoesNotExist()
    }

    @Test
    fun runningState_progressCounterUpdatesAsPacketsArrive() {
        val stateFlow = MutableStateFlow<PingUiState>(
            PingUiState.Running(host = "example.com", packets = listOf(fakePacket(1)), totalCount = 4)
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(flow = stateFlow))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(PingScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(PingScreenTestTags.RESULTS_PANEL_INDEX)
        composeRule.onNodeWithText("1 / 4").assertIsDisplayed()

        stateFlow.value = PingUiState.Running(
            host = "example.com",
            packets = listOf(fakePacket(1), fakePacket(2)),
            totalCount = 4
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("2 / 4").assertIsDisplayed()
    }

    @Test
    fun continuousMode_beyond50Packets_showsTrimCaption() {
        val packets = (1..60).map { fakePacket(it) }
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(
                    viewModel = fakePingViewModel(
                        PingUiState.Running(
                            host = "example.com",
                            packets = packets,
                            totalCount = 0,
                            isContinuous = true,
                            pingsSent = 60
                        )
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(PingScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(PingScreenTestTags.RESULTS_PANEL_INDEX)
        composeRule
            .onNodeWithText(context.getString(R.string.ping_showing_recent, 50, 60))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun fakePacket(sequence: Int) = PingPacketResult(
        sequence = sequence,
        host = "example.com",
        rtTimeMs = 20L,
        status = PingStatus.SUCCESS
    )

    private fun fakePingViewModel(
        state: PingUiState? = null,
        flow: MutableStateFlow<PingUiState>? = null,
        host: String = ""
    ): PingViewModel {
        val viewModel = mockk<PingViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: PingUiState.Idle))
        every { viewModel.host } returns MutableStateFlow(host)
        every { viewModel.count } returns MutableStateFlow(4)
        every { viewModel.timeoutMs } returns MutableStateFlow(1000)
        every { viewModel.continuousMode } returns MutableStateFlow(false)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
