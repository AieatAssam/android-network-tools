package net.aieat.netswissknife.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkStatus
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
    fun invalidCustomServer_disablesLookupAndImeSubmit() {
        val viewModel = fakeDnsViewModel(
            DnsUiState.Idle,
            selectedServer = DnsServer.Custom("resolver.example"),
            customServerAddress = "resolver.example",
            domainValue = "example.com"
        )
        composeRule.setContent {
            NetSwissKnifeTheme { DnsScreen(viewModel = viewModel) }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(DnsScreenTestTags.CANCEL_LOOKUP).assertIsNotEnabled()
        composeRule.onNodeWithTag(DnsScreenTestTags.DOMAIN_INPUT).performImeAction()
        verify(exactly = 0) { viewModel.performLookup() }
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
    fun cancelingState_showsCleanupProgress() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Canceling))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        scrollToStatePanel()
        composeRule.onNodeWithText(context.getString(R.string.dns_canceling)).assertIsDisplayed()
    }

    @Test
    fun cancelingState_disablesControls_untilCanceledStateRestoresLookup() {
        val state = MutableStateFlow<DnsUiState>(DnsUiState.Canceling)
        val viewModel = fakeDnsViewModel(
            DnsUiState.Canceling,
            domainValue = "query.example",
            uiStateFlow = state
        )
        composeRule.setContent {
            NetSwissKnifeTheme { DnsScreen(viewModel = viewModel) }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(DnsScreenTestTags.DOMAIN_INPUT).assertIsNotEnabled()
        composeRule.onNodeWithTag(DnsScreenTestTags.CANCEL_LOOKUP).assertIsNotEnabled()

        state.value = DnsUiState.Canceled
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(DnsScreenTestTags.DOMAIN_INPUT).assertIsEnabled()
        composeRule.onNodeWithTag(DnsScreenTestTags.CANCEL_LOOKUP).assertIsEnabled()
    }

    @Test
    fun canceledState_showsStatusAndClearReturnsToIdle() {
        val viewModel = fakeDnsViewModel(DnsUiState.Canceled)
        composeRule.setContent {
            NetSwissKnifeTheme { DnsScreen(viewModel = viewModel) }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        scrollToStatePanel()
        composeRule.onNodeWithText(context.getString(R.string.dns_canceled)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.clear)).performClick()
        verify(exactly = 1) { viewModel.onClearResults() }
    }

    @Test
    fun loadingState_cancelStopsLookup() {
        val viewModel = fakeDnsViewModel(DnsUiState.Loading)
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        scrollToStatePanel()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

        verify(exactly = 1) { viewModel.onStopLookup() }
    }

    @Test
    fun loadingState_disablesDomainInputAndDoesNotStartAnotherLookup() {
        val viewModel = fakeDnsViewModel(
            DnsUiState.Loading,
            domainValue = "query.example",
            recentHostsValue = listOf("recent.example")
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(DnsScreenTestTags.DOMAIN_INPUT).assertIsNotEnabled()
        composeRule.onNodeWithText("recent.example").assertIsNotEnabled()

        verify(exactly = 0) { viewModel.performLookup() }
    }

    @Test
    fun idleState_searchImeStartsLookup() {
        val viewModel = fakeDnsViewModel(DnsUiState.Idle, domainValue = "query.example")
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(DnsScreenTestTags.DOMAIN_INPUT).performImeAction()

        verify(exactly = 1) { viewModel.performLookup() }
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

    @Test
    fun noerrorEmptyResult_explainsNoRecordsAndKeepsRcodeAndRawResponse() {
        assertEmptyResultPresentation(
            rcode = "NOERROR",
            expectedTitle = context.getString(R.string.dns_no_records_title),
            expectedSubtitle = context.getString(
                R.string.dns_no_records_subtitle,
                DnsRecordType.A.displayName,
                "empty.example"
            )
        )
    }

    @Test
    fun nxdomainEmptyResult_explainsMissingDomainAndKeepsRcodeAndRawResponse() {
        assertEmptyResultPresentation(
            rcode = "NXDOMAIN",
            expectedTitle = context.getString(R.string.dns_nxdomain_title),
            expectedSubtitle = context.getString(R.string.dns_nxdomain_subtitle, "empty.example")
        )
    }

    @Test
    fun serverFailureEmptyResult_explainsRcodeAndKeepsRcodeAndRawResponse() {
        assertEmptyResultPresentation(
            rcode = "SERVFAIL",
            expectedTitle = context.getString(R.string.dns_rcode_error_title),
            expectedSubtitle = context.getString(R.string.dns_rcode_error_subtitle, "SERVFAIL", "empty.example")
        )
    }

    @Test
    fun refusedEmptyResult_usesOtherRcodeRecoveryCopy() {
        assertEmptyResultPresentation(
            rcode = "REFUSED",
            expectedTitle = context.getString(R.string.dns_rcode_error_title),
            expectedSubtitle = context.getString(R.string.dns_rcode_error_subtitle, "REFUSED", "empty.example")
        )
    }

    private fun assertEmptyResultPresentation(
        rcode: String,
        expectedTitle: String,
        expectedSubtitle: String,
        authority: List<DnsRecord> = emptyList()
    ) {
        val state = MutableStateFlow<DnsUiState>(DnsUiState.Success(DnsResult(
            domain = "empty.example",
            recordType = DnsRecordType.A,
            server = DnsServer.System(),
            records = emptyList(),
            queryTimeMs = 12,
            rawResponse = "raw response for $rcode",
            authority = authority,
            rcode = rcode,
            serverUsed = "192.0.2.53"
        )))
        val viewModel = fakeDnsViewModel(state.value, uiStateFlow = state)
        every { viewModel.onToggleRawView() } answers {
            val current = state.value as? DnsUiState.Success
            if (current != null) state.value = current.copy(showRaw = !current.showRaw)
        }
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        scrollToStatePanel()
        composeRule.onNodeWithText(expectedTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(expectedSubtitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.dns_rcode, rcode)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.dns_raw_response))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("raw response for $rcode").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noerrorEmptyReferral_doesNotClaimTheRecordTypeDoesNotExist() {
        assertEmptyResultPresentation(
            rcode = "NOERROR",
            expectedTitle = context.getString(R.string.dns_no_records_title),
            expectedSubtitle = "No A records were returned for empty.example.",
            authority = listOf(
                DnsRecord(
                    type = DnsRecordType.NS,
                    name = "example.",
                    value = "ns1.example.net.",
                    ttl = 3_600,
                    rawLine = "example. 3600 IN NS ns1.example.net."
                )
            )
        )
    }

    @Test
    fun input_exposesOneDnsServerLabelAndHorizontalOverflowCue() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                DnsScreen(viewModel = fakeDnsViewModel(DnsUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule
            .onAllNodesWithText(
                context.getString(R.string.dns_server_label),
                substring = false,
                useUnmergedTree = true
            )
            .assertCountEquals(1)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.dns_record_types_scroll_hint))
            .assertIsDisplayed()
    }

    private fun scrollToStatePanel() {
        composeRule
            .onNodeWithTag(DnsScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(DnsScreenTestTags.STATE_PANEL_INDEX)
    }

    private fun fakeDnsViewModel(
        state: DnsUiState,
        selectedServer: DnsServer = DnsServer.System(),
        customServerAddress: String = "",
        domainValue: String = "",
        recentHostsValue: List<String> = emptyList(),
        uiStateFlow: MutableStateFlow<DnsUiState> = MutableStateFlow(state)
    ): DnsViewModel {
        val viewModel = mockk<DnsViewModel>(relaxed = true)
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.domain } returns MutableStateFlow(domainValue)
        every { viewModel.recordType } returns MutableStateFlow(DnsRecordType.A)
        every { viewModel.selectedServer } returns MutableStateFlow(selectedServer)
        every { viewModel.customServerAddress } returns MutableStateFlow(customServerAddress)
        every { viewModel.recentHosts } returns MutableStateFlow(recentHostsValue)
        every { viewModel.networkStatus } returns MutableStateFlow(
            NetworkStatus(hasInternet = true, hasLocalNetwork = true)
        )
        return viewModel
    }
}
