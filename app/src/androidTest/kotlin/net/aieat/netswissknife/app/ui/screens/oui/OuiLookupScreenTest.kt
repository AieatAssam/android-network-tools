package net.aieat.netswissknife.app.ui.screens.oui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.oui.MacAddressInfo
import net.aieat.netswissknife.core.network.oui.MacAddressKind
import net.aieat.netswissknife.core.network.oui.MacLookupEntry
import net.aieat.netswissknife.core.network.oui.OuiBlockSize
import net.aieat.netswissknife.core.network.oui.VendorResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MAC Lookup renders its idle, result and error states. The view model is mocked, so no
 * registry read or clipboard access runs during the test.
 */
@RunWith(AndroidJUnit4::class)
class OuiLookupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(state: OuiLookupUiState): OuiLookupViewModel {
        val viewModel = mockk<OuiLookupViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.recentLookups } returns MutableStateFlow(emptyList())
        return viewModel
    }

    @Test
    fun lookupIsDisabledUntilAnEntryIsTyped() {
        composeRule.setContent {
            NetSwissKnifeTheme { OuiLookupScreen(viewModel(OuiLookupUiState())) }
        }

        composeRule.onNodeWithTag(OuiLookupTestTags.LOOKUP).assertIsNotEnabled()
        composeRule.onNodeWithTag(OuiLookupTestTags.PASTE).assertIsEnabled()
    }

    @Test
    fun resultsShowTheVendorAndBlockForAnAddress() {
        val address =
            MacLookupEntry.Address(
                input = "B8:27:EB:11:22:33",
                info =
                    MacAddressInfo(
                        hex = "B827EB112233",
                        kind = MacAddressKind.UNICAST,
                        locallyAdministered = false,
                        vendor = VendorResult.Known("Raspberry Pi Foundation", OuiBlockSize.MA_L),
                    ),
            )
        val state =
            OuiLookupUiState(
                input = "B8:27:EB:11:22:33",
                phase = OuiLookupPhase.Results(listOf(address), truncated = false),
            )
        composeRule.setContent {
            NetSwissKnifeTheme { OuiLookupScreen(viewModel(state)) }
        }

        composeRule.onNodeWithTag(OuiLookupTestTags.RESULTS).assertIsDisplayed()
        composeRule.onNodeWithText("Raspberry Pi Foundation").assertIsDisplayed()
        composeRule.onNodeWithTag(OuiLookupTestTags.COPY_ALL).assertIsDisplayed()
    }

    @Test
    fun aFailedLookupShowsItsReasonWithATryAgainAction() {
        val state = OuiLookupUiState(phase = OuiLookupPhase.Failed(OuiLookupFailure.NO_VALID_ENTRY))
        composeRule.setContent {
            NetSwissKnifeTheme { OuiLookupScreen(viewModel(state)) }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithText(context.getString(R.string.oui_failed_no_valid_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oui_retry)).assertIsDisplayed()
    }
}
