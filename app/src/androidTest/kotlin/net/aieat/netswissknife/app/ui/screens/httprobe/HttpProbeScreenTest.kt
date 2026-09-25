package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.HttpSecurityAnalyzer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

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
    fun loadingState_showsCancelAction() {
        val viewModel = fakeViewModel(
            HttpProbeUiState(url = "https://example.com", isLoading = true),
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.httprobe_cancel_request))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        verify { viewModel.cancel() }
    }

    @Test
    fun blockedRedirectWarning_showsStatusAndRedactedEvidence() {
        val warning = BlockedHttpRedirectWarning(
            sourceUrl = "https://alice:source-secret@source.example/start?source-token=private#frag",
            destinationUrl = "http://bob:destination-secret@target.example/reset?redirect-token=private#frag",
            statusCode = 302,
            location = "//bob:destination-secret@target.example/reset?redirect-token=private#frag",
        )
        val approval = PendingEntityReplayApproval(
            runId = "run",
            approvalId = "approval",
            destinationUrl = "http://bob:destination-secret@target.example/reset?consent-token=private#frag",
            method = HttpMethod.POST,
            statusCode = 307,
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = fakeViewModel(HttpProbeUiState(
                    blockedRedirectWarning = warning,
                    pendingEntityReplayApproval = approval,
                )))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule.onNodeWithTag(HttpProbeScreenTestTags.BLOCKED_REDIRECT_WARNING)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_blocked_redirect_title))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_blocked_redirect_message, 302))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("https://source.example/[path omitted]")
            .performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("http://target.example/[path omitted]")
            .assertCountEquals(2)
        composeRule.onNodeWithText("//target.example/[path omitted]")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("http://target.example/[path omitted]")
            .performScrollTo().assertIsDisplayed()
        listOf(
            "source-secret", "destination-secret", "source-token", "redirect-token", "consent-token", "frag"
        ).forEach { secret ->
            composeRule.onAllNodesWithText(secret, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun successfulResult_redactsOverviewUrlsAndShareSecrets() {
        val sourceUrl = "https://alice:source-secret@source.example/start?source-token=private#source-fragment"
        val finalUrl = "https://bob:destination-secret@target.example/final?final-token=private#final-fragment"
        val result = HttpProbeResult(
            request = HttpProbeRequest(url = sourceUrl),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 42,
            responseHeaders = mapOf(
                "Content-Type" to listOf("application/json"),
                "Location" to listOf("https://next.example/path?location-token=private"),
                "Set-Cookie" to listOf("sid=cookie-secret; Secure"),
                "Authorization" to listOf("Bearer auth-secret"),
                "Proxy-Authorization" to listOf("Basic proxy-secret"),
            ),
            responseBody = "{}",
            responseBodyBytes = 2,
            finalUrl = finalUrl,
            redirectChain = listOf(sourceUrl),
            securityChecks = emptyList(),
        )
        val shareText = buildHttpShareText(result, "2 B")

        assertTrue(shareText.contains("https://source.example/[path omitted]"))
        assertTrue(shareText.contains("https://target.example/[path omitted]"))
        assertTrue(shareText.contains("https://next.example/[path omitted]"))
        listOf("source-secret", "destination-secret", "source-token", "final-token", "location-token", "cookie-secret", "auth-secret", "proxy-secret", "fragment").forEach {
            assertFalse("share text leaked $it", shareText.contains(it))
        }
        assertTrue(shareText.contains("Content-Type: application/json"))

        composeRule.setContent {
            NetSwissKnifeTheme { HttpProbeScreen(viewModel = fakeViewModel(HttpProbeUiState(result = result, selectedTab = 0))) }
        }
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule.onAllNodesWithText("https://target.example/[path omitted]")
            .onFirst().performScrollTo().assertIsDisplayed()
        listOf("source-secret", "destination-secret", "source-token", "final-token", "cookie-secret", "auth-secret").forEach {
            composeRule.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun cancelingState_showsStoppingAction() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(
                    viewModel = fakeViewModel(
                        HttpProbeUiState(
                            url = "https://example.com",
                            method = HttpMethod.POST,
                            customHeaders = listOf(HeaderEntry("X-Test", "value")),
                            body = "body",
                            headersExpanded = true,
                            isLoading = true,
                            isCanceling = true,
                        ),
                        recentHostValues = listOf("https://recent.example"),
                    ),
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.httprobe_stopping))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_url_label))
            .assertIsNotEnabled()
        composeRule.onNodeWithText("POST")
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_header_key))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_header_value))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_body_label))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("https://recent.example")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun canceledState_showsRequestCanceledMessage() {
        val viewModel = fakeViewModel(
            HttpProbeUiState(url = "https://example.com", isCanceled = true),
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule
            .onNodeWithText(context.getString(R.string.httprobe_request_canceled))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.httprobe_send_button))
            .performScrollTo()
            .performClick()

        verify { viewModel.send() }
    }

    @Test
    fun changedInputAfterSuccess_hidesOldResponseAndReturnsToIdlePanel() {
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
            securityChecks = emptyList(),
        )
        val state = MutableStateFlow(HttpProbeUiState(url = "https://example.com", result = result))
        val viewModel = fakeViewModel(state.value)
        every { viewModel.uiState } returns state

        composeRule.setContent {
            NetSwissKnifeTheme { HttpProbeScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag(HttpProbeScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(HttpProbeScreenTestTags.RESULT_PANEL_INDEX)
        composeRule.onNodeWithText("200").assertIsDisplayed()

        state.value = state.value.copy(
            url = "https://edited.example",
            result = null,
            selectedTab = 0,
        )
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText("200").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_idle_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun mdnsHandoff_showsEditablePrefillAndSourceWithoutSending() {
        val viewModel = mockk<HttpProbeViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            HttpProbeUiState(url = "http://printer.local:8080/"),
        )
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        every { viewModel.sourceContextState } returns MutableStateFlow(ToolSource.MDNS)
        every { viewModel.hasInvalidHandoff } returns MutableStateFlow(false)

        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(HttpProbeScreenTestTags.SOURCE_CONTEXT)
            .assertIsDisplayed()
        val urlField = composeRule.onNodeWithText("http://printer.local:8080/")
        urlField.performScrollTo().assert(hasSetTextAction())
        urlField.performTextReplacement("http://edited.local:9000/")

        verify(exactly = 1) { viewModel.onUrlChange("http://edited.local:9000/") }
        verify(exactly = 0) { viewModel.send() }
    }

    @Test
    fun lanHandoff_showsEditablePrefillAndSourceWithoutSending() {
        val viewModel = mockk<HttpProbeViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            HttpProbeUiState(url = "http://192.0.2.8:8080/"),
        )
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        every { viewModel.sourceContextState } returns MutableStateFlow(ToolSource.LAN)
        every { viewModel.hasInvalidHandoff } returns MutableStateFlow(false)

        composeRule.setContent { NetSwissKnifeTheme { HttpProbeScreen(viewModel = viewModel) } }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(HttpProbeScreenTestTags.SOURCE_CONTEXT).assertIsDisplayed()
        val urlField = composeRule.onNodeWithText("http://192.0.2.8:8080/")
        urlField.performScrollTo().assert(hasSetTextAction())
        verify(exactly = 0) { viewModel.send() }
    }

    @Test
    fun clearPrefillAction_clearsSourceContext() {
        val uiState = MutableStateFlow(HttpProbeUiState(url = "http://printer.local:8080/"))
        val sourceContext = MutableStateFlow<ToolSource?>(ToolSource.MDNS)
        val viewModel = mockk<HttpProbeViewModel>(relaxed = true)
        every { viewModel.uiState } returns uiState
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        every { viewModel.sourceContextState } returns sourceContext
        every { viewModel.hasInvalidHandoff } returns MutableStateFlow(false)
        every { viewModel.clearPrefill() } answers {
            uiState.value = uiState.value.copy(url = "")
            sourceContext.value = null
        }

        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(HttpProbeScreenTestTags.CLEAR_PREFILL_ACTION)
            .performScrollTo()
            .performClick()

        verify(exactly = 1) { viewModel.clearPrefill() }
        composeRule.onNodeWithTag(HttpProbeScreenTestTags.SOURCE_CONTEXT).assertDoesNotExist()
        composeRule.onNodeWithTag(HttpProbeScreenTestTags.CLEAR_PREFILL_ACTION).assertDoesNotExist()
    }

    @Test
    fun invalidHandoff_showsRecoveryMessageAndEditableUrlWithoutSending() {
        val viewModel = fakeViewModel(HttpProbeUiState())
        every { viewModel.hasInvalidHandoff } returns MutableStateFlow(true)

        composeRule.setContent {
            NetSwissKnifeTheme {
                HttpProbeScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(HttpProbeScreenTestTags.INVALID_HANDOFF)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.httprobe_url_label))
            .performScrollTo()
            .assert(hasSetTextAction())
        verify(exactly = 0) { viewModel.send() }
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
            .onAllNodesWithText("https://example.com/[path omitted]")
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun fakeViewModel(
        state: HttpProbeUiState,
        recentHostValues: List<String> = emptyList(),
    ): HttpProbeViewModel {
        val viewModel = mockk<HttpProbeViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.recentHosts } returns MutableStateFlow(recentHostValues)
        every { viewModel.sourceContextState } returns MutableStateFlow(null)
        every { viewModel.hasInvalidHandoff } returns MutableStateFlow(false)
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
        every { viewModel.sourceContextState } returns MutableStateFlow(null)
        every { viewModel.hasInvalidHandoff } returns MutableStateFlow(false)

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
