package net.aieat.netswissknife.app.ui.screens.oui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * MAC addresses are hex text: no capital first letter and no autocorrect, which otherwise rewrote
 * "zz 00:1A" as "Zz 00:1A" (PHB-10).
 */
internal val ouiInputKeyboardOptions: KeyboardOptions =
    KeyboardOptions(
        keyboardType = KeyboardType.Ascii,
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        imeAction = ImeAction.Search,
    )
