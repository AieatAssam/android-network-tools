package net.aieat.netswissknife.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.dns.DnsUiState
import net.aieat.netswissknife.app.ui.screens.dns.DnsViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers DNS Lookup's help sheet, custom-server validation, loading indicator,
 * and success-state record rendering.
 */
@RunWith(AndroidJUnit4::class)
class DnsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun helpSheet_showsConceptAndParameterBullets() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_dns_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.help_dns_concept_body))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun customServerAddress_invalidValue_showsValidationError() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(
                    viewModel = fakeDnsViewModel(
                        DnsUiState.Idle,
                        selectedServer = DnsServer.Custom("not-an-ip"),
                        customServerAddress = "not-an-ip"
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.dns_custom_server_invalid))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun loadingState_showsQueryingIndicator() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Loading))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        scrollToStatePanel()
        composeRule.onNodeWithText(context.getString(R.string.dns_querying)).assertIsDisplayed()
    }

    @Test
    fun successState_displaysRecordsAndSummary() {
        val result = DnsResult(
            domain = "example.com",
            recordType = DnsRecordType.A,
            server = DnsServer.System(),
            records = listOf(
                DnsRecord(type = DnsRecordType.A, name = "example.com", value = "93.184.216.34", ttl = 300, rawLine = "example.com. 300 IN A 93.184.216.34")
            ),
            queryTimeMs = 42,
            rawResponse = "raw dns response"
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Success(result)))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        scrollToStatePanel()
        composeRule.onAllNodesWithText("example.com").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("93.184.216.34", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun scrollToStatePanel() {
        composeRule
            .onNodeWithTag(DnsScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(DnsScreenTestTags.STATE_PANEL_INDEX)
    }

    private fun fakeDnsViewModel(
        state: DnsUiState,
        selectedServer: DnsServer = DnsServer.System(),
        customServerAddress: String = ""
    ): DnsViewModel {
        val viewModel = mockk<DnsViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.domain } returns MutableStateFlow("")
        every { viewModel.recordType } returns MutableStateFlow(DnsRecordType.A)
        every { viewModel.selectedServer } returns MutableStateFlow(selectedServer)
        every { viewModel.customServerAddress } returns MutableStateFlow(customServerAddress)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
