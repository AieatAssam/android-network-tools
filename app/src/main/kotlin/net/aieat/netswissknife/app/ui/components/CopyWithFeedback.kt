package net.aieat.netswissknife.app.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R

/**
 * Shared Copy action for result cards. Puts the text on the clipboard and shows the same
 * "Copied to clipboard" toast that the other tools use, so every Copy gives visible feedback.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun rememberCopyWithFeedback(): (String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.lan_copied)
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
}
