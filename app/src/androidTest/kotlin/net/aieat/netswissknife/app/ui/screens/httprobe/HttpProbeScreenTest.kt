package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
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
