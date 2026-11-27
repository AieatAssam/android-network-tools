package net.aieat.netswissknife.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.aieat.netswissknife.app.MainActivity
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.HomeScreenTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises a complete, network-independent tool flow through the real app activity. */
@RunWith(AndroidJUnit4::class)
class FullAppSubnetEndToEndTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun launchesApp_navigatesToSubnet_calculatesAndDisplaysNetworkDetails() {
        composeRule.mainClock.advanceTimeBy(2_000L)
        completeFirstRunOnboardingIfVisible()

        composeRule.onNodeWithText(context.getString(R.string.app_name_full)).assertIsDisplayed()
        composeRule
            .onNodeWithTag(HomeScreenTestTags.TOOL_GRID)
            .performScrollToNode(hasText(context.getString(R.string.tool_subnet_label)))
        composeRule.onNodeWithText(context.getString(R.string.tool_subnet_label)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(context.getString(R.string.subnet_screen_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.subnet_input_label))
            .performClick()
            .performTextInput("192.168.1.0/24")
        composeRule.onNodeWithText(context.getString(R.string.subnet_calculate_button)).performClick()

        repeat(2) {
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }
            composeRule.mainClock.advanceTimeBy(500L)
        }
        composeRule.onNodeWithText(context.getString(R.string.subnet_network_address)).assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.0").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.subnet_broadcast)).assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.255").assertIsDisplayed()
    }

    private fun completeFirstRunOnboardingIfVisible() {
        val dismissLabel = context.getString(R.string.onboarding_dont_show_again)
        val homeTitle = context.getString(R.string.app_name_full)
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(dismissLabel).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(homeTitle).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText(dismissLabel).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(dismissLabel).performClick()
        }
    }
}
