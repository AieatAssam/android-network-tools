package net.aieat.netswissknife.app.ui.screens.oui

import androidx.compose.ui.text.input.KeyboardCapitalization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OuiInputKeyboardTest {
    @Test
    fun `MAC input never auto-capitalises or autocorrects`() {
        assertEquals(KeyboardCapitalization.None, ouiInputKeyboardOptions.capitalization)
        assertEquals(false, ouiInputKeyboardOptions.autoCorrectEnabled)
    }
}
