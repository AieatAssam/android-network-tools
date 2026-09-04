package net.aieat.netswissknife.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.ui.navigation.AppNavHost
import net.aieat.netswissknife.app.ui.screens.HomeScreen
import net.aieat.netswissknife.app.ui.screens.DnsScreen
import net.aieat.netswissknife.app.ui.screens.dns.DnsUiState
import net.aieat.netswissknife.app.ui.screens.dns.DnsViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsServer
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Small device-side Compose contract tests. These intentionally target stable
 * semantics and user-visible states instead of implementation details, so
 * screen refactors do not make the UI suite brittle.
 */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_exposesToolCardsAndInvokesNavigation() {
        var destination: String? = null
        composeRule.setContent {
            NetSwissKnifeTheme {
                HomeScreen(onNavigate = { destination = it })
            }
        }

        // The grid intentionally animates in; advancing the test clock makes
        // this deterministic while still exercising the rendered semantics.
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText("Ping").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals("ping", destination)
    }

    @Test
    fun appNavHost_startsAtHome() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                AppNavHost(navController = rememberNavController())
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText("Net Swiss Knife").assertIsDisplayed()
        composeRule.onNodeWithText("All Tools").assertIsDisplayed()
    }

    @Test
    fun dnsScreen_loadingState_hasAccessibleProgressSemantics() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Loading))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("Querying DNS…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun dnsScreen_errorState_exposesMessageAndRetryAction() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Error("NXDOMAIN")))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("Lookup failed").assertIsDisplayed()
        composeRule.onNodeWithText("NXDOMAIN").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertHasClickAction().assertIsDisplayed()
    }

    private fun fakeDnsViewModel(state: DnsUiState): DnsViewModel {
        val viewModel = mockk<DnsViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.domain } returns MutableStateFlow("")
        every { viewModel.recordType } returns MutableStateFlow(DnsRecordType.A)
        every { viewModel.selectedServer } returns MutableStateFlow(DnsServer.System())
        every { viewModel.customServerAddress } returns MutableStateFlow("")
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
