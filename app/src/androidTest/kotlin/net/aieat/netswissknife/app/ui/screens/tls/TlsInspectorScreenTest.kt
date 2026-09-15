package net.aieat.netswissknife.app.ui.screens.tls

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.tls.TlsCertificate
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers TLS Inspector's help sheet, host/port input enable-gating, loading
 * state, error+retry, and success-state certificate rendering.
 */
@RunWith(AndroidJUnit4::class)
class TlsInspectorScreenTest {
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
                TlsInspectorScreen(viewModel = fakeViewModel(TlsInspectorUiState()))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_tls_concept_heading))
            .assertIsDisplayed()
    }

    @Test
    fun hostInput_gatesInspectButtonEnabled() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                TlsInspectorScreen(viewModel = fakeViewModel(TlsInspectorUiState()))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule
            .onNodeWithText(context.getString(R.string.tls_inspect_button))
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText(context.getString(R.string.tls_host_label))
            .performTextInput("example.com")
        composeRule.mainClock.advanceTimeBy(200L)

        composeRule
            .onNodeWithText(context.getString(R.string.tls_inspect_button))
            .assertIsEnabled()
    }

    @Test
    fun loadingState_showsInspectingIndicator() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                TlsInspectorScreen(
                    viewModel = fakeViewModel(TlsInspectorUiState(host = "example.com", isLoading = true))
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.tls_inspecting))
            .assertIsDisplayed()
    }

    @Test
    fun errorState_showsMessageAndRetryCallsInspect() {
        val viewModel = fakeViewModel(
            TlsInspectorUiState(host = "example.com", error = "Connection refused")
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TlsInspectorScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(context.getString(R.string.tls_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Connection refused").assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.tls_retry))
            .performClick()

        verify(exactly = 1) { viewModel.inspect() }
    }

    @Test
    fun successState_displaysCertificateSubject() {
        val cert = TlsCertificate(
            subjectCN = "example.com",
            subjectOrg = "Example Org",
            issuerCN = "Example CA",
            issuerOrg = "Example CA Inc",
            notBefore = 0L,
            notAfter = Long.MAX_VALUE / 2,
            isExpired = false,
            isSelfSigned = false,
            sans = listOf("example.com", "www.example.com"),
            serialNumber = "01",
            signatureAlgorithm = "SHA256withRSA",
            publicKeyAlgorithm = "RSA",
            publicKeyBits = 2048,
            sha256Fingerprint = "AA:BB:CC"
        )
        val result = TlsInspectorResult(
            host = "example.com",
            port = 443,
            tlsVersion = "TLSv1.3",
            cipherSuite = "TLS_AES_128_GCM_SHA256",
            chain = listOf(cert),
            isChainTrusted = true,
            handshakeTimeMs = 123
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TlsInspectorScreen(
                    viewModel = fakeViewModel(TlsInspectorUiState(host = "example.com", result = result))
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("example.com").assertIsDisplayed()
        composeRule.onNodeWithText("TLSv1.3", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun fakeViewModel(state: TlsInspectorUiState): TlsInspectorViewModel {
        val viewModel = mockk<TlsInspectorViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
