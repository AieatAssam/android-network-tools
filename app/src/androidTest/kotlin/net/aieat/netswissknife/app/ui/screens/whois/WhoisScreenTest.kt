package net.aieat.netswissknife.app.ui.screens.whois

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers WHOIS Lookup's help sheet, incremental relay-chain hop display,
 * example-chip prefill, and result rendering.
 */
@RunWith(AndroidJUnit4::class)
class WhoisScreenTest {
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
                WhoisScreen(viewModel = fakeViewModel(WhoisUiState()))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_whois_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun incrementalHopChain_accumulatesAsHopsArrive() {
        val stateFlow = MutableStateFlow(
            WhoisUiState(
                query = "example.com",
                isLoading = true,
                hopStates = listOf(fakeHop("whois.iana.org"))
            )
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                WhoisScreen(viewModel = fakeViewModel(flow = stateFlow))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("whois.iana.org").assertIsDisplayed()

        stateFlow.value = stateFlow.value.copy(
            hopStates = listOf(fakeHop("whois.iana.org"), fakeHop("whois.verisign-grs.com"))
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("whois.iana.org").assertIsDisplayed()
        composeRule.onNodeWithText("whois.verisign-grs.com").assertIsDisplayed()
    }

    @Test
    fun exampleChip_prefillsQuery() {
        val viewModel = fakeViewModel(WhoisUiState())
        composeRule.setContent {
            NetSwissKnifeTheme {
                WhoisScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule
            .onNodeWithText(context.getString(R.string.whois_example_chip_domain))
            .performScrollTo()
            .performClick()

        verify(exactly = 1) { viewModel.onQueryChange("example.com") }
    }

    @Test
    fun resultState_displaysDomainName() {
        val result = fakeDomainResult()
        composeRule.setContent {
            NetSwissKnifeTheme {
                WhoisScreen(viewModel = fakeViewModel(WhoisUiState(query = "example.com", result = result)))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onAllNodesWithText("example.com").onFirst().assertIsDisplayed()
    }

    private fun fakeHop(host: String) = HopUiState(
        server = WhoisServer(host = host, role = WhoisServerRole.IANA),
        status = HopStatus.DONE,
        queryTimeMs = 42
    )

    private fun fakeDomainResult() = WhoisResult(
        query = "example.com",
        queryType = WhoisQueryType.DOMAIN,
        hops = emptyList(),
        domainName = "example.com",
        registrar = "Example Registrar",
        registrarUrl = null,
        registeredOn = null,
        expiresOn = null,
        updatedOn = null,
        nameServers = emptyList(),
        statusCodes = emptyList(),
        registrantOrg = null,
        registrantCountry = null,
        dnssec = null,
        netName = null,
        netRange = null,
        orgName = null,
        country = null,
        totalQueryTimeMs = 100
    )

    private fun fakeViewModel(
        state: WhoisUiState? = null,
        flow: MutableStateFlow<WhoisUiState>? = null
    ): WhoisViewModel {
        val viewModel = mockk<WhoisViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: WhoisUiState()))
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
