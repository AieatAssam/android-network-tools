package net.aieat.netswissknife.app.ui.screens.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_exitsSettings() {
        var exited = false
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        every { viewModel.themeOverride } returns MutableStateFlow("SYSTEM")
        every { viewModel.dynamicColor } returns MutableStateFlow(true)
        every { viewModel.defaultPingCount } returns MutableStateFlow(10)
        every { viewModel.defaultTimeoutMs } returns MutableStateFlow(2_000)
        every { viewModel.defaultConcurrency } returns MutableStateFlow(50)
        every { viewModel.wifiRefreshIntervalMs } returns MutableStateFlow(60_000L)

        composeRule.setContent {
            NetSwissKnifeTheme {
                SettingsScreen(
                    onBack = { exited = true },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()

        assertTrue(exited)
    }

    @Test
    fun attributions_remainReachableAtTheEndOfScrollableContent() {
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        every { viewModel.themeOverride } returns MutableStateFlow("SYSTEM")
        every { viewModel.dynamicColor } returns MutableStateFlow(true)
        every { viewModel.defaultPingCount } returns MutableStateFlow(10)
        every { viewModel.defaultTimeoutMs } returns MutableStateFlow(2_000)
        every { viewModel.defaultConcurrency } returns MutableStateFlow(50)
        every { viewModel.wifiRefreshIntervalMs } returns MutableStateFlow(60_000L)

        composeRule.setContent {
            NetSwissKnifeTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.settings_attribution_speedtest_body))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun concurrencySliderSupportsPersistedRangeThrough500() {
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        every { viewModel.themeOverride } returns MutableStateFlow("SYSTEM")
        every { viewModel.dynamicColor } returns MutableStateFlow(true)
        every { viewModel.defaultPingCount } returns MutableStateFlow(10)
        every { viewModel.defaultTimeoutMs } returns MutableStateFlow(2_000)
        every { viewModel.defaultConcurrency } returns MutableStateFlow(500)
        every { viewModel.wifiRefreshIntervalMs } returns MutableStateFlow(60_000L)

        composeRule.setContent {
            NetSwissKnifeTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_concurrency_label, 500))
            .performScrollTo()
            .assertIsDisplayed()

        val slider = composeRule.onNodeWithTag(SettingsScreenTestTags.DEFAULT_CONCURRENCY_SLIDER)
            .fetchSemanticsNode()
        val range = slider.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(500f, range.current)
        assertEquals(1f..500f, range.range)
        assertEquals(498, range.steps)
    }

    private val context
        get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
}
