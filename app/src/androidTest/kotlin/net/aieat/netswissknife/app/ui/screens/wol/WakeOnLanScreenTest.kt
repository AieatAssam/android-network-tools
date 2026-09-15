package net.aieat.netswissknife.app.ui.screens.wol

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.wol.WolSendReport
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Wake-on-LAN's help sheet, MAC validation, advanced-options
 * (broadcast/port) enable-gating, and Success/Error state rendering.
 *
 * [WakeOnLanScreen] requests `NEARBY_WIFI_DEVICES` on entry on API 36+ (Local
 * Network Protections, see `LocalNetworkPermission.kt`) — pre-granting it
 * avoids a system permission dialog interrupting the test.
 */
@RunWith(AndroidJUnit4::class)
class WakeOnLanScreenTest {
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
    fun helpSheet_showsConceptHeading() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WakeOnLanScreen(viewModel = fakeViewModel())
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithText(context.getString(R.string.wol_screen_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.help_wol_concept_heading))
            .assertIsDisplayed()
    }

    @Test
    fun invalidMac_showsValidationErrorAndDisablesSend() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WakeOnLanScreen(viewModel = fakeViewModel(macAddress = "not-a-mac"))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wol_mac_invalid))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.wol_send_button))
            .assertIsNotEnabled()
    }

    @Test
    fun invalidPort_disablesSendButton() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WakeOnLanScreen(
                    viewModel = fakeViewModel(
                        macAddress = "AA:BB:CC:DD:EE:FF",
                        port = "70000"
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        // Broadcast/port are behind the "Advanced options" disclosure.
        composeRule
            .onNodeWithText(context.getString(R.string.wol_advanced_options))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.wol_send_button))
            .assertIsNotEnabled()
    }

    @Test
    fun validInputs_enableSendButton() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WakeOnLanScreen(
                    viewModel = fakeViewModel(
                        macAddress = "AA:BB:CC:DD:EE:FF",
                        broadcastAddress = "192.168.1.255",
                        port = "9"
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wol_send_button))
            .assertIsEnabled()
    }

    @Test
    fun successState_showsSentDetails() {
        val report = WolSendReport(
            macAddress = "AA:BB:CC:DD:EE:FF",
            broadcastAddress = "255.255.255.255",
            port = 9,
            packetsSent = 3
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                WakeOnLanScreen(viewModel = fakeViewModel(state = WolUiState.Success(report)))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wol_success_title))
            .assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WakeOnLanScreen(viewModel = fakeViewModel(state = WolUiState.Error("Network unreachable")))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(context.getString(R.string.wol_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Network unreachable").assertIsDisplayed()
    }

    private fun fakeViewModel(
        state: WolUiState = WolUiState.Idle,
        macAddress: String = "",
        broadcastAddress: String = "255.255.255.255",
        port: String = "9"
    ): WakeOnLanViewModel {
        val viewModel = mockk<WakeOnLanViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.macAddress } returns MutableStateFlow(macAddress)
        every { viewModel.broadcastAddress } returns MutableStateFlow(broadcastAddress)
        every { viewModel.port } returns MutableStateFlow(port)
        return viewModel
    }
}
