package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Smallest bottom clearance for sheet content: the standard Android navigation bar height.
 * Sheet dialog windows report a zero navigation bar inset on some devices (seen on an API 26
 * tablet with on-screen buttons), so the reported inset alone can leave the last row under
 * the buttons. The union with the M3 defaults takes the larger value per edge.
 */
private val SHEET_NAV_BAR_CLEARANCE: Dp = 48.dp

/**
 * The app's only modal bottom sheet. Its content is padded above the system navigation
 * bar through the sheet's window insets, so the last row of a sheet is never drawn under
 * the nav buttons. Sheets open fully expanded so long lists scroll inside the sheet instead of
 * running under the buttons at half height. Sheets keep their own scrolling inside [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets.union(WindowInsets(bottom = SHEET_NAV_BAR_CLEARANCE))
        },
    ) {
        content()
    }
}
