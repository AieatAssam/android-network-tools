package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.portscan.PortScanUiState
import net.aieat.netswissknife.app.ui.screens.portscan.PortScanViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.domain.PortScanPreset
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the concurrency safety warning added to [PortsScreen] — it must
 * appear above 100 concurrent connections and stay hidden at or below it.
 *
 * [PortsScreen] requests `NEARBY_WIFI_DEVICES` on entry on API 36+ (Local
 * Network Protections, see `LocalNetworkPermission.kt`) — pre-granting it
 * avoids a system permission dialog interrupting the test.
 */
@RunWith(AndroidJUnit4::class)
class PortsScreenTest {
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
    fun highConcurrency_showsSafetyWarning() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PortsScreen(viewModel = fakePortScanViewModel(concurrency = 150))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.ports_concurrency_high_warning))
            .assertIsDisplayed()
    }

    @Test
    fun lowConcurrency_hidesSafetyWarning() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PortsScreen(viewModel = fakePortScanViewModel(concurrency = 50))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.ports_concurrency_high_warning))
            .assertDoesNotExist()
    }

    @Test
    fun helpSheet_showsConceptHeading() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PortsScreen(viewModel = fakePortScanViewModel())
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
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
            .onNodeWithText(context.getString(R.string.help_portscan_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun scanningState_showsProgressCard() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PortsScreen(
                    viewModel = fakePortScanViewModel(
                        state = PortScanUiState.Scanning(liveResults = emptyList(), scannedCount = 5, totalCount = 20)
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.ports_scanning_title))
            .assertIsDisplayed()
    }

    @Test
    fun customPresetInvalidPortRange_showsRangeError() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                PortsScreen(
                    viewModel = fakePortScanViewModel(
                        selectedPreset = PortScanPreset.CUSTOM,
                        startPort = "99999",
                        endPort = "1024"
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onAllNodesWithText(context.getString(R.string.ports_range_error))
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun commonServicesPicker_closesAndLeavesScanCtaReachable() {
        val viewModel = fakePortScanViewModel()
        every { viewModel.host } returns MutableStateFlow("example.com")

        composeRule.setContent {
            NetSwissKnifeTheme {
                PortsScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(PortsScreenTestTags.PRESET_FIELD).performClick()
        composeRule
            .onAllNodesWithText(PortScanPreset.COMMON.label)
            .onLast()
            .performClick()

        composeRule
            .onNodeWithTag(PortsScreenTestTags.SCAN_BUTTON)
            .assertIsDisplayed()
            .performClick()
        io.mockk.verify(exactly = 1) { viewModel.startScan() }
    }

    @Test
    fun hostWithOuterWhitespace_remainsStartable() {
        val viewModel = fakePortScanViewModel(host = "  example.com  ")
        composeRule.setContent {
            NetSwissKnifeTheme { PortsScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(PortsScreenTestTags.SCAN_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        io.mockk.verify(exactly = 1) { viewModel.startScan() }
    }

    @Test
    fun hostWithInternalSpace_showsValidationAndBlocksButtonAndIme() {
        val viewModel = fakePortScanViewModel()
        composeRule.setContent {
            NetSwissKnifeTheme { PortsScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(context.getString(R.string.ports_host_label))
            .performTextInput("bad host")
        composeRule.mainClock.advanceTimeBy(200L)

        composeRule.onNodeWithText(context.getString(R.string.error_invalid_host))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PortsScreenTestTags.SCAN_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("bad host").performImeAction()

        io.mockk.verify(exactly = 0) { viewModel.startScan() }
    }

    private fun fakePortScanViewModel(
        host: String = "",
        concurrency: Int = 50,
        state: PortScanUiState = PortScanUiState.Idle,
        selectedPreset: PortScanPreset = PortScanPreset.COMMON,
        startPort: String = "1",
        endPort: String = "1024"
    ): PortScanViewModel {
        val viewModel = mockk<PortScanViewModel>(relaxed = true)
        val hostFlow = MutableStateFlow(host)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.host } returns hostFlow
        every { viewModel.onHostChange(any()) } answers { hostFlow.value = firstArg() }
        every { viewModel.selectedPreset } returns MutableStateFlow(selectedPreset)
        every { viewModel.startPort } returns MutableStateFlow(startPort)
        every { viewModel.endPort } returns MutableStateFlow(endPort)
        every { viewModel.timeoutMs } returns MutableStateFlow(1000)
        every { viewModel.concurrency } returns MutableStateFlow(concurrency)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
