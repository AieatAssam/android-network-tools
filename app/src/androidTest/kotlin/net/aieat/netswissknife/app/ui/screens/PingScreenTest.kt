package net.aieat.netswissknife.app.ui.screens

import android.os.Build
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.LocalNetworkPermissionPolicy
import net.aieat.netswissknife.app.ui.screens.ping.PingUiState
import net.aieat.netswissknife.app.ui.screens.ping.PingViewModel
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import net.aieat.netswissknife.core.network.HostValidator
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Ping's Running -> Error state transition rendering.
 *
 * Pre-grant the OS-version-specific local-network permission so any test that
 * starts a local target avoids a system permission dialog.
 */
@RunWith(AndroidJUnit4::class)
class PingScreenTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = LocalNetworkPermissionPolicy
        .permissionToRequest(Build.VERSION.SDK_INT)
        ?.let { permission -> GrantPermissionRule.grant(permission) }
        ?: GrantPermissionRule.grant()

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
        val viewModel = fakePingViewModel(PingUiState.Idle, host = "bad host")
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.error_invalid_host))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.ping_start_button))
            .assertIsNotEnabled()

        composeRule.onNodeWithText("bad host").performClick().performImeAction()
        verify(exactly = 0) { viewModel.startPing() }
    }

    @Test
    fun hostWithOuterWhitespace_remainsStartable() {
        val viewModel = fakePingViewModel(PingUiState.Idle, host = "  example.com  ")
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule
            .onNodeWithText(context.getString(R.string.ping_start_button))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.mainClock.autoAdvance = false

        verify(exactly = 1) { viewModel.startPing() }
    }

    @Test
    fun lanHandoff_showsProvenanceAndDoesNotStartPing() {
        val viewModel = fakePingViewModel(PingUiState.Idle, host = "192.0.2.8", sourceContext = ToolSource.LAN)
        composeRule.setContent {
            NetSwissKnifeTheme { PingScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(PingScreenTestTags.SOURCE_CONTEXT).assertIsDisplayed()
        composeRule.onNodeWithText("192.0.2.8").assertIsDisplayed()
        verify(exactly = 0) { viewModel.startPing() }
    }

    @Test
    fun clearPrefillAction_clearsHostAndSourceWithoutStartingPing() {
        val host = MutableStateFlow("192.0.2.8")
        val viewModel = fakePingViewModel(
            PingUiState.Idle,
            hostState = host,
            sourceContext = ToolSource.LAN,
        )
        composeRule.setContent {
            NetSwissKnifeTheme { PingScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(PingScreenTestTags.CLEAR_PREFILL_ACTION)
            .assertIsDisplayed()
            .performClick()
        composeRule.mainClock.advanceTimeBy(100L)

        composeRule.onAllNodesWithTag(PingScreenTestTags.SOURCE_CONTEXT).assertCountEquals(0)
        composeRule.onNodeWithTag(PingScreenTestTags.HOST_FIELD).assertTextEquals("")
        verify(exactly = 1) { viewModel.clearPrefill() }
        verify(exactly = 0) { viewModel.startPing() }
    }

    @Test
    fun mdnsHandoff_showsProvenanceAndDoesNotStartPing() {
        val viewModel = fakePingViewModel(
            PingUiState.Idle,
            host = "printer.local",
            sourceContext = ToolSource.MDNS,
        )
        composeRule.setContent {
            NetSwissKnifeTheme { PingScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(PingScreenTestTags.SOURCE_CONTEXT)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.ping_source_mdns))
        composeRule.onNodeWithText("printer.local").assertIsDisplayed()
        verify(exactly = 0) { viewModel.startPing() }
    }

    @Test
    fun invalidTypedHandoff_showsRecoveryMessageAndKeepsPingFormAvailable() {
        val host = MutableStateFlow("")
        val hasInvalidHandoff = MutableStateFlow(true)
        val viewModel = fakePingViewModel(
            PingUiState.Idle,
            hostState = host,
            invalidHandoffState = hasInvalidHandoff,
        )
        composeRule.setContent {
            NetSwissKnifeTheme { PingScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(PingScreenTestTags.INVALID_HANDOFF).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ping_host_label)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ping_start_button)).assertIsNotEnabled()

        val hostField = composeRule.onNodeWithTag(PingScreenTestTags.HOST_FIELD)
        hostField.performTextInput("bad host")

        composeRule.onNodeWithTag(PingScreenTestTags.INVALID_HANDOFF).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ping_start_button)).assertIsNotEnabled()

        hostField.performTextClearance()
        hostField.performTextInput("replacement.example")

        composeRule.onAllNodesWithTag(PingScreenTestTags.INVALID_HANDOFF).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.ping_start_button)).assertIsEnabled()
        verify(exactly = 0) { viewModel.startPing() }
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
    fun advancedOptions_showViewModelValuesAfterCompositionReentry() {
        val payloadBytes = MutableStateFlow(56)
        val ttl = MutableStateFlow(64)
        val intervalMs = MutableStateFlow(1_000)
        val viewModel = fakePingViewModel(
            PingUiState.Idle,
            payloadBytes = payloadBytes,
            ttl = ttl,
            intervalMs = intervalMs
        )
        val screenGeneration = mutableStateOf(0)
        composeRule.setContent {
            key(screenGeneration.value) {
                NetSwissKnifeTheme { PingScreen(viewModel = viewModel) }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000L)

        // Dispose and re-enter the screen composition with the same ViewModel.
        payloadBytes.value = 512
        ttl.value = 128
        intervalMs.value = 2_500
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.runOnIdle {
            screenGeneration.value++
        }
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText(context.getString(R.string.action_expand)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText("Payload size: 512 bytes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TTL: 128").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Interval: 2500 ms").performScrollTo().assertIsDisplayed()
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
    fun countSlider_supportsSettingsMaximumOf100() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PingScreen(viewModel = fakePingViewModel(PingUiState.Idle, count = 100))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText("Count: 100").performScrollTo().assertIsDisplayed()

        val slider = composeRule.onNodeWithTag(PingScreenTestTags.COUNT_SLIDER)
            .fetchSemanticsNode()
        val range = slider.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(100f, range.current)
        assertEquals(1f..100f, range.range)
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
        host: String = "",
        count: Int = 4,
        payloadBytes: MutableStateFlow<Int> = MutableStateFlow(56),
        ttl: MutableStateFlow<Int> = MutableStateFlow(64),
        intervalMs: MutableStateFlow<Int> = MutableStateFlow(1_000),
        sourceContext: ToolSource? = null,
        hasInvalidHandoff: Boolean = false,
        hostState: MutableStateFlow<String>? = null,
        invalidHandoffState: MutableStateFlow<Boolean>? = null,
    ): PingViewModel {
        val viewModel = mockk<PingViewModel>(relaxed = true)
        val sourceContextFlow = MutableStateFlow(sourceContext)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: PingUiState.Idle))
        every { viewModel.host } returns (hostState ?: MutableStateFlow(host))
        every { viewModel.count } returns MutableStateFlow(count)
        every { viewModel.timeoutMs } returns MutableStateFlow(1000)
        every { viewModel.payloadBytes } returns payloadBytes
        every { viewModel.ttl } returns ttl
        every { viewModel.intervalMs } returns intervalMs
        every { viewModel.continuousMode } returns MutableStateFlow(false)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        every { viewModel.sourceContext } returns sourceContext
        every { viewModel.sourceContextState } returns sourceContextFlow
        every { viewModel.clearPrefill() } answers {
            hostState?.value = ""
            sourceContextFlow.value = null
        }
        if (hostState != null && invalidHandoffState != null) {
            every { viewModel.hasInvalidHandoff } returns invalidHandoffState
            every { viewModel.onHostChange(any()) } answers {
                val replacement = firstArg<String>()
                hostState.value = replacement
                if (HostValidator.normalize(replacement) != null) invalidHandoffState.value = false
            }
        } else {
            every { viewModel.hasInvalidHandoff } returns MutableStateFlow(hasInvalidHandoff)
        }
        return viewModel
    }
}
