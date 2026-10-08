package net.aieat.netswissknife.app.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolStateAnnouncerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stateChangeUpdatesPoliteLiveRegionSemantics() {
        val phase = mutableStateOf(ToolAnnouncementPhase.RUNNING)
        composeRule.setContent {
            NetSwissKnifeTheme {
                ToolStateAnnouncer(toolName = "Ping", phase = phase.value)
            }
        }

        composeRule.onNodeWithContentDescription("Ping is running", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.runOnIdle { phase.value = ToolAnnouncementPhase.PARTIAL }

        composeRule.onNodeWithContentDescription(
            "Ping stopped with partial results",
            useUnmergedTree = true,
        ).assertContentDescriptionEquals("Ping stopped with partial results")

        composeRule.runOnIdle { phase.value = ToolAnnouncementPhase.CANCELED }

        composeRule.onNodeWithContentDescription("Ping stopped", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.runOnIdle { phase.value = ToolAnnouncementPhase.FINISHED }
        composeRule.onNodeWithContentDescription("Ping finished", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.runOnIdle { phase.value = ToolAnnouncementPhase.ERROR }
        composeRule.onNodeWithContentDescription("Ping failed", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun finishedStateIncludesLocalizedStableResultDetail() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                ToolStateAnnouncer(
                    toolName = "DNS Lookup",
                    phase = ToolAnnouncementPhase.FINISHED,
                    detail = pluralStringResource(R.plurals.a11y_dns_record_count, 3, 3),
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "DNS Lookup finished. 3 records found",
            useUnmergedTree = true,
        ).assertContentDescriptionEquals("DNS Lookup finished. 3 records found")
    }
}
