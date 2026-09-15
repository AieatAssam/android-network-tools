package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepositoryImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers CIDR-mode success rendering and the IP-range min>max validation
 * error surfaced by [SubnetCalculatorRepositoryImpl.calculateRange].
 */
@RunWith(AndroidJUnit4::class)
class SubnetCalculatorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun validCidr_showsNetworkAddress() {
        val info = (SubnetCalculatorRepositoryImpl().calculate("192.168.1.0/24") as NetworkResult.Success).data
        val viewModel = fakeSubnetViewModel(SubnetCalculatorUiState(input = "192.168.1.0/24", result = info))

        composeRule.setContent {
            NetSwissKnifeTheme {
                SubnetCalculatorScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        // The network address appears in multiple places (input echo, overview
        // card, notation card) — assert at least one is displayed rather than
        // requiring the single-match uniqueness onNodeWithText enforces.
        composeRule.onAllNodesWithText(info.networkAddress, substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun invalidRange_showsMinMaxValidationError() {
        val errorResult = SubnetCalculatorRepositoryImpl().calculateRange("10.0.0.50", "10.0.0.10")
        val message = (errorResult as NetworkResult.Error).message
        val viewModel = fakeSubnetViewModel(
            SubnetCalculatorUiState(isRangeMode = true, minIpInput = "10.0.0.50", maxIpInput = "10.0.0.10", error = message)
        )

        composeRule.setContent {
            NetSwissKnifeTheme {
                SubnetCalculatorScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun helpSheet_showsConceptHeading() {
        val viewModel = fakeSubnetViewModel(SubnetCalculatorUiState())
        composeRule.setContent {
            NetSwissKnifeTheme {
                SubnetCalculatorScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_subnet_concept_heading))
            .assertIsDisplayed()
    }

    @Test
    fun cidrMode_showsCidrInputNotRangeInputs() {
        val viewModel = fakeSubnetViewModel(SubnetCalculatorUiState(isRangeMode = false))
        composeRule.setContent {
            NetSwissKnifeTheme {
                SubnetCalculatorScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(context.getString(R.string.subnet_input_label)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.subnet_min_ip_label)).assertDoesNotExist()
    }

    @Test
    fun rangeMode_showsMinMaxInputsNotCidrInput() {
        val viewModel = fakeSubnetViewModel(SubnetCalculatorUiState(isRangeMode = true))
        composeRule.setContent {
            NetSwissKnifeTheme {
                SubnetCalculatorScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(context.getString(R.string.subnet_min_ip_label)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.subnet_max_ip_label)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.subnet_input_label)).assertDoesNotExist()
    }

    @Test
    fun modeToggle_clickingRangeSegment_callsToggleMode() {
        val viewModel = fakeSubnetViewModel(SubnetCalculatorUiState(isRangeMode = false))
        composeRule.setContent {
            NetSwissKnifeTheme {
                SubnetCalculatorScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.subnet_mode_range))
            .performClick()

        verify(exactly = 1) { viewModel.toggleMode() }
    }

    private fun fakeSubnetViewModel(state: SubnetCalculatorUiState): SubnetCalculatorViewModel {
        val viewModel = mockk<SubnetCalculatorViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        return viewModel
    }
}
