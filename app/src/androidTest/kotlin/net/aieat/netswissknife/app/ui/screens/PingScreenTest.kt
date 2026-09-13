package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    private fun fakePingViewModel(state: PingUiState): PingViewModel {
        val viewModel = mockk<PingViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.host } returns MutableStateFlow("")
        every { viewModel.count } returns MutableStateFlow(4)
        every { viewModel.timeoutMs } returns MutableStateFlow(1000)
        every { viewModel.continuousMode } returns MutableStateFlow(false)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
