package net.aieat.netswissknife.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.navigation.MoreToolsSheet
import net.aieat.netswissknife.app.ui.navigation.MoreToolsSheetTestTags
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.screens.HomeScreen
import net.aieat.netswissknife.app.ui.screens.HomeScreenTestTags
import net.aieat.netswissknife.app.ui.screens.onboarding.OnboardingSheet
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hasButtonRole(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun home_everyToolCard_isMergedClickableButton() {
        var destination: String? = null
        composeRule.setContent {
            NetSwissKnifeTheme {
                HomeScreen(onNavigate = { destination = it })
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)

        NavRoutes.allTools.forEach { tool ->
            composeRule
                .onNodeWithTag(HomeScreenTestTags.TOOL_GRID)
                .performScrollToNode(hasText(tool.label))
            composeRule
                .onNodeWithText(tool.label)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assert(hasButtonRole())
                // Matching and activating the visible title proves it is part of the
                // merged card action, rather than a separate non-clickable Text node.
                .performClick()
            assertEquals("Activating ${tool.label} should navigate to its tool", tool.route, destination)
        }
    }

    @Test
    fun home_showsScrollCueWhenMoreToolsExistBelowFold() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                HomeScreen(onNavigate = {})
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)

        composeRule
            .onNodeWithText(context.getString(R.string.home_tools_scroll_hint))
            .assertIsDisplayed()
    }

    @Test
    fun moreSheet_containsEveryHomeToolAsMergedClickableButton() {
        val navigated = mutableListOf<String>()
        composeRule.setContent {
            NetSwissKnifeTheme {
                MoreToolsSheet(
                    pinnedRoutes = emptyList(),
                    onNavigate = navigated::add,
                    onTogglePin = {},
                    maxPinned = 3,
                    onDismiss = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        val toolList = composeRule.onNodeWithTag(MoreToolsSheetTestTags.TOOL_LIST)

        NavRoutes.allTools.forEach { tool ->
            composeRule.onNodeWithText(tool.label).performScrollTo()
            composeRule
                .onNodeWithText(tool.label)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assert(hasButtonRole())
                // The label itself must activate the row. The nested pin button stays
                // independently available through its own content description.
                .performClick()
            assertEquals("Activating ${tool.label} should navigate to its tool", tool.route, navigated.last())
        }

        toolList.assertIsDisplayed()
    }

    @Test
    fun moreSheet_pinAndUnpinButtonsAreIndependentFromToolNavigation() {
        val toggledRoutes = mutableListOf<String>()
        val navigatedRoutes = mutableListOf<String>()
        val ping = NavRoutes.allTools.first { it.route == NavRoutes.Ping.baseRoute }

        composeRule.setContent {
            NetSwissKnifeTheme {
                var pinnedRoutes by remember { mutableStateOf(emptyList<String>()) }
                MoreToolsSheet(
                    pinnedRoutes = pinnedRoutes,
                    onNavigate = navigatedRoutes::add,
                    onTogglePin = { route ->
                        toggledRoutes += route
                        pinnedRoutes = if (route in pinnedRoutes) {
                            pinnedRoutes - route
                        } else {
                            pinnedRoutes + route
                        }
                    },
                    maxPinned = 3,
                    onDismiss = {},
                )
            }
        }

        val pinDescription = context.getString(R.string.more_pin_description, ping.label)
        composeRule
            .onNodeWithContentDescription(pinDescription)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(hasButtonRole())
            .performClick()

        assertEquals(listOf(ping.route), toggledRoutes)
        assertTrue("Pin action must not navigate", navigatedRoutes.isEmpty())

        val unpinDescription = context.getString(R.string.more_unpin_description, ping.label)
        composeRule
            .onNodeWithContentDescription(unpinDescription)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(hasButtonRole())
            .performClick()

        assertEquals(listOf(ping.route, ping.route), toggledRoutes)
        assertTrue("Unpin action must not navigate", navigatedRoutes.isEmpty())
    }

    @Test
    fun onboardingSkip_dismissesWelcomeSheet() {
        var dismissed = false
        composeRule.setContent {
            NetSwissKnifeTheme {
                OnboardingSheet(onDismiss = { dismissed = true })
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.onboarding_dont_show_again))
            .assertHasClickAction()
            .performClick()

        assertTrue(dismissed)
    }

    @Test
    fun launcherLabel_isShortEnoughForTheAppDrawer() {
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        assertEquals(context.getString(R.string.app_name), label)
        assertTrue("Launcher label is too long: $label", label.length <= 12)
    }
}
