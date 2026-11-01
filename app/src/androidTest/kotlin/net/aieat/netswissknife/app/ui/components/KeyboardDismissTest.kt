package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records keyboard requests so the test can assert that dismissal never asks for the IME. */
private class RecordingKeyboard : SoftwareKeyboardController {
    var shows = 0
    var hides = 0

    override fun show() {
        shows++
    }

    override fun hide() {
        hides++
    }
}

private const val FIELD_TAG = "dismiss-field"
private const val DONE_TAG = "dismiss-done"

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun DismissHarness() {
    FocusSinkHost {
        val dismiss = rememberDismissKeyboard()
        var text by remember { mutableStateOf("") }
        Column {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.testTag(FIELD_TAG),
            )
            Button(onClick = dismiss, modifier = Modifier.testTag(DONE_TAG)) {
                Text("Done")
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class KeyboardDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dismissMovesFocusOutOfTextFieldAndHidesKeyboardWithoutRequestingIt() {
        val keyboard = RecordingKeyboard()
        composeRule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                DismissHarness()
            }
        }

        composeRule.onNodeWithTag(FIELD_TAG).performClick()
        composeRule.onNodeWithTag(FIELD_TAG).assertIsFocused()
        composeRule.onNodeWithTag(FIELD_TAG).performTextInput("8.8.8.8")
        val showsBeforeDismiss = keyboard.shows

        composeRule.onNodeWithTag(DONE_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FIELD_TAG).assertIsNotFocused()
        assertTrue("dismiss should hide the keyboard", keyboard.hides >= 1)
        assertEquals("dismiss must not request the keyboard", showsBeforeDismiss, keyboard.shows)
    }

    @Test
    fun focusSinkHasNoSemanticsAndNoNodeIsFocusedAfterDismiss() {
        val keyboard = RecordingKeyboard()
        composeRule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                DismissHarness()
            }
        }

        composeRule.onNodeWithTag(FIELD_TAG).performClick()
        composeRule.onNodeWithTag(DONE_TAG).performClick()
        composeRule.waitForIdle()

        // The sink holds focus but exposes nothing to TalkBack or the test tree, so no node is focused.
        composeRule.onAllNodes(isFocused(), useUnmergedTree = true).assertCountEquals(0)
        // Only the text field and the button are focusable in the semantics tree; the sink is not.
        composeRule.onAllNodes(isFocusable(), useUnmergedTree = true).assertCountEquals(2)
    }
}
