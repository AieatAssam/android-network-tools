package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.netinfo.IpAddressScope
import net.aieat.netswissknife.core.network.netinfo.IpFamily
import net.aieat.netswissknife.core.network.netinfo.LocalAddress
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.NetworkTransport
import net.aieat.netswissknife.core.network.netinfo.PublicAddressInfo
import net.aieat.netswissknife.core.network.netinfo.PublicIpReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Network Info renders each state and never contacts the public IP services on its own:
 * the Check action is the only trigger, and the view model is mocked so no network I/O runs.
 */
@RunWith(AndroidJUnit4::class)
class NetInfoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun connected(): NetInfoSnapshot =
        NetInfoSnapshot(
            hasActiveNetwork = true,
            transports = listOf(NetworkTransport.ETHERNET),
            metered = false,
            roaming = false,
            wifi = null,
            interfaceName = "eth0",
            addresses = listOf(LocalAddress("192.168.1.20", IpFamily.IPV4, 24, IpAddressScope.PRIVATE)),
            gateways = listOf("192.168.1.1"),
            dnsServers = listOf("192.168.1.1"),
            mtuBytes = 1500,
            mac = null,
            searchDomains = emptyList(),
            proxy = null,
        )

    private fun viewModel(
        local: NetInfoSnapshot? = connected(),
        publicIp: PublicIpUiState = PublicIpUiState.Idle,
    ): NetInfoViewModel {
        val viewModel = mockk<NetInfoViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(NetInfoUiState(local = local, publicIp = publicIp))
        return viewModel
    }

    @Test
    fun checkPublicIp_isNotStartedOnEntry() {
        val viewModel = viewModel()

        composeRule.setContent {
            NetSwissKnifeTheme {
                NetInfoScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        verify(exactly = 0) { viewModel.checkPublicIp() }
    }

    @Test
    fun checkPublicIp_button_requestsLookup() {
        val viewModel = viewModel()

        composeRule.setContent {
            NetSwissKnifeTheme {
                NetInfoScreen(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithTag(NetInfoPublicIpTestTags.CHECK).performClick()

        verify(exactly = 1) { viewModel.checkPublicIp() }
    }

    @Test
    fun noActiveNetwork_showsExplanation() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                NetInfoScreen(viewModel = viewModel(local = NetInfoSnapshot.noNetwork()))
            }
        }

        composeRule.onNodeWithTag(NetInfoScreenTestTags.NO_NETWORK).assertExists()
    }

    @Test
    fun loadedPublicIp_showsTheAddress() {
        val report =
            PublicIpReport(
                ipv4 = NetworkResult.Success(PublicAddressInfo("203.0.113.7", IpFamily.IPV4, null, null)),
                ipv6 = NetworkResult.error(ErrorCode.NETWORK_REQUEST_FAILED, "no route"),
            )

        composeRule.setContent {
            NetSwissKnifeTheme {
                NetInfoScreen(viewModel = viewModel(publicIp = PublicIpUiState.Loaded(report)))
            }
        }

        composeRule.onNodeWithText("203.0.113.7").assertExists()
    }
}
