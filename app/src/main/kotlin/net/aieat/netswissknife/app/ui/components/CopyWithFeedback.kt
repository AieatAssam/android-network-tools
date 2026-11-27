package net.aieat.netswissknife.app.ui.components

import android.content.ClipData
import android.os.Build
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
 * Android 13 (API 33) and later show their own clipboard confirmation, so an extra toast
 * would duplicate it. Earlier releases get the app's toast as the only feedback.
 */
internal fun copyToastNeeded(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.TIRAMISU

/**
 * Shared Copy action for result cards. Puts the text on the clipboard and, on releases
 * without the system confirmation, shows a "Copied to clipboard" toast.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun rememberCopyWithFeedback(): (String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.copied_to_clipboard)
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
            if (copyToastNeeded(Build.VERSION.SDK_INT)) {
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
