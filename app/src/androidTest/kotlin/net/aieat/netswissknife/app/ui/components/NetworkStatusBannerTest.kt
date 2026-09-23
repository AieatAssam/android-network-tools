package net.aieat.netswissknife.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkStatusBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun internetScope_showsOfflineWarningOnlyWhenInternetIsUnavailable() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetworkStatusBanner(
                    status = NetworkStatus(hasInternet = false, hasLocalNetwork = true),
                    scope = NetworkStatusScope.INTERNET,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.network_banner_no_internet)).assertIsDisplayed()
        composeRule.onNodeWithTag(PERMISSION_CARD_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun localScope_showsNoLocalWarningBeforeVpnInformation() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetworkStatusBanner(
                    status = NetworkStatus(hasInternet = true, hasLocalNetwork = false, vpnActive = true),
                    scope = NetworkStatusScope.LOCAL_NETWORK,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.network_banner_no_local)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.network_banner_vpn_info)).assertDoesNotExist()
    }

    @Test
    fun localScope_showsVpnInformationWhenLocalNetworkIsAvailable() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetworkStatusBanner(
                    status = NetworkStatus(hasInternet = true, hasLocalNetwork = true, vpnActive = true),
                    scope = NetworkStatusScope.LOCAL_NETWORK,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.network_banner_vpn_info)).assertIsDisplayed()
    }

    @Test
    fun healthyInternetScope_hasNoBanner() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetworkStatusBanner(
                    status = NetworkStatus(hasInternet = true, hasLocalNetwork = true),
                    scope = NetworkStatusScope.INTERNET,
                )
            }
        }

        composeRule.onNodeWithTag(STATUS_BANNER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun anyNetworkScope_doesNotWarnWhenVpnIsActive() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetworkStatusBanner(
                    status = NetworkStatus(hasInternet = false, hasLocalNetwork = false, vpnActive = true),
                    scope = NetworkStatusScope.ANY_NETWORK,
                )
            }
        }

        composeRule.onNodeWithTag(STATUS_BANNER_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.network_banner_no_network)).assertDoesNotExist()
    }

    @Test
    fun permissionError_showsLocalizedGrantActionAndInvokesIt() {
        var grants = 0
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetworkStatusBanner(
                    status = NetworkStatus(hasInternet = true, hasLocalNetwork = true),
                    scope = NetworkStatusScope.LOCAL_NETWORK,
                    permissionDenied = true,
                    onGrantPermission = { grants++ },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.network_error_local_permission)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.network_action_grant_permission)).performClick()
        composeRule.runOnIdle { assertEquals(1, grants) }
    }
}
