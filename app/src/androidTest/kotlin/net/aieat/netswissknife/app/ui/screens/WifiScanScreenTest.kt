package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.wifi.ApSortOrder
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Wi-Fi Scanner's help sheet, the permission/support/disabled status
 * screens, sort-order interaction, and Success-state rendering with a frozen
 * network order (the app's live-refresh selection-preservation mechanism).
 *
 * [WifiScanScreen] requests `ACCESS_FINE_LOCATION` always and additionally
 * `NEARBY_WIFI_DEVICES` on API 33+ — pre-granting both avoids a system
 * permission dialog interrupting the test.
 */
@RunWith(AndroidJUnit4::class)
class WifiScanScreenTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)
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
                WifiScanScreen(viewModel = fakeViewModel(successState(listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50)))))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_wifi_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun noPermissionState_showsMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.NoPermission))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_no_permission_title))
            .assertIsDisplayed()
    }

    @Test
    fun notSupportedState_showsMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.NotSupported))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_not_supported_title))
            .assertIsDisplayed()
    }

    @Test
    fun wifiDisabledState_showsMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.WifiDisabled))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_disabled_title))
            .assertIsDisplayed()
    }

    @Test
    fun sortChip_tapCallsSetSortOrder() {
        val viewModel = fakeViewModel(successState(listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50))))
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(ApSortOrder.SSID.label).performClick()

        verify(exactly = 1) { viewModel.setSortOrder(ApSortOrder.SSID) }
    }

    @Test
    fun successState_frozenOrder_rendersAllNetworksInFrozenOrder() {
        // Natural SIGNAL order (descending RSSI) would be Charlie, Alpha, Bravo;
        // frozenOrder pins a different sequence that must still fully render.
        val alpha = fakeAp("Alpha", "AA:AA:AA:AA:AA:01", -70)
        val bravo = fakeAp("Bravo", "BB:BB:BB:BB:BB:01", -80)
        val charlie = fakeAp("Charlie", "CC:CC:CC:CC:CC:01", -50)
        val frozenOrder = listOf(
            "Bravo|${WifiSecurity.WPA2.name}",
            "Alpha|${WifiSecurity.WPA2.name}",
            "Charlie|${WifiSecurity.WPA2.name}"
        )

        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(
                    viewModel = fakeViewModel(
                        successState(listOf(alpha, bravo, charlie), frozenOrder = frozenOrder)
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Bravo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Charlie").performScrollTo().assertIsDisplayed()
    }

    private fun fakeAp(
        ssid: String,
        bssid: String,
        rssi: Int,
        security: WifiSecurity = WifiSecurity.WPA2
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        frequency = 2437,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 6,
        band = net.aieat.netswissknife.core.network.wifi.WifiBand.BAND_2_4GHZ,
        standard = WifiStandard.WIFI_5,
        security = security,
        isConnected = false,
        vendor = "",
        centerFrequency0 = 0,
        centerFrequency1 = 0,
        timestampUs = 0L
    )

    private fun successState(
        accessPoints: List<WifiAccessPoint>,
        frozenOrder: List<String>? = null
    ) = WifiScanUiState.Success(
        result = WifiScanResult(
            accessPoints = accessPoints,
            channels = emptyList(),
            connectedNetwork = null,
            scanTimestampMs = 0L,
            isWifiEnabled = true
        ),
        frozenOrder = frozenOrder
    )

    private fun fakeViewModel(state: WifiScanUiState): WifiScanViewModel {
        val viewModel = mockk<WifiScanViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.autoRefresh } returns MutableStateFlow(false)
        every { viewModel.expandedNetworks } returns MutableStateFlow(emptySet())
        every { viewModel.apDisappearedMessage } returns MutableStateFlow(null)
        return viewModel
    }
}
