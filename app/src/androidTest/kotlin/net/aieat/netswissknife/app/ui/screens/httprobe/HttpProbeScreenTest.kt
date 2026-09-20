package net.aieat.netswissknife.app.ui.screens.httprobe

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
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.HttpSecurityAnalyzer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the Security tab rendering the Cross-Origin-Opener-Policy and
 * Cross-Origin-Embedder-Policy checks added to [HttpSecurityAnalyzer].
 */
@RunWith(AndroidJUnit4::class)
class HttpProbeScreenTest {
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
                HttpProbeScreen(viewModel = fakeViewModel(HttpProbeUiState()))
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
            .onNodeWithText(context.getString(R.string.help_httprobe_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun loadingState_showsSendingIndicator() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = fakeViewModel(HttpProbeUiState(url = "https://example.com", isLoading = true)))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule
            .onAllNodesWithText(context.getString(R.string.httprobe_sending))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun invalidUrl_showsValidationError() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = fakeViewModel(HttpProbeUiState(url = "not-a-url.com")))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.error_invalid_url))
            .assertIsDisplayed()
    }

    @Test
    fun incompleteUrlHost_showsValidationErrorBeforeSend() {
        val viewModel = fakeViewModel(HttpProbeUiState(url = "https://example."))
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.error_invalid_url))
            .assertIsDisplayed()
        verify(exactly = 0) { viewModel.send() }
    }

    @Test
    fun tabClick_notifiesViewModelOfSelection() {
        val result = HttpProbeResult(
            request = HttpProbeRequest(url = "https://example.com"),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 42,
            responseHeaders = emptyMap(),
            responseBody = "",
            responseBodyBytes = 0,
            finalUrl = "https://example.com",
            redirectChain = emptyList(),
            securityChecks = emptyList()
        )
        val viewModel = fakeViewModel(HttpProbeUiState(result = result, selectedTab = 0))
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule
            .onNodeWithText(context.getString(R.string.httprobe_tab_headers))
            .performClick()

        verify(exactly = 1) { viewModel.onTabSelected(1) }
    }

    @Test
    fun overviewTab_showsMethodAndFinalUrl() {
        val result = HttpProbeResult(
            request = HttpProbeRequest(url = "https://example.com"),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 42,
            responseHeaders = emptyMap(),
            responseBody = "",
            responseBodyBytes = 0,
            finalUrl = "https://example.com/final",
            redirectChain = emptyList(),
            securityChecks = emptyList()
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = fakeViewModel(HttpProbeUiState(result = result, selectedTab = 0)))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule
            .onNodeWithText(context.getString(R.string.httprobe_method_used))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText("https://example.com/final")
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun fakeViewModel(state: HttpProbeUiState): HttpProbeViewModel {
        val viewModel = mockk<HttpProbeViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }

    @Test
    fun securityTab_showsCoopAndCoepChecks() {
        val result = HttpProbeResult(
            request = HttpProbeRequest(url = "https://example.com"),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 42,
            responseHeaders = emptyMap(),
            responseBody = "",
            responseBodyBytes = 0,
            finalUrl = "https://example.com",
            redirectChain = emptyList(),
            securityChecks = HttpSecurityAnalyzer.analyze(emptyMap(), isHttps = true)
        )
        val viewModel = mockk<HttpProbeViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(HttpProbeUiState(result = result, selectedTab = 3))
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())

        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)

        // The security checks render as a plain (non-lazy) Column inside this list item, so on
        // shorter/lower-density screens the item's top can be in view while later checks are
        // still clipped below the viewport. Scroll each target node individually rather than
        // relying on the whole item fitting on screen.
        composeRule.onNodeWithText("Cross-Origin-Opener-Policy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cross-Origin-Embedder-Policy").performScrollTo().assertIsDisplayed()
    }
}
