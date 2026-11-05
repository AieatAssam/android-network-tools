package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
 *
 * Content is capped to [maxSheetHeightDp] so a tall sheet never reaches the top of the window.
 * Material3 moves the sheet's top inset with the drag offset, so a sheet pinned to the top
 * re-measures on every drag frame and snaps back. The top inset is therefore left out of the
 * content insets, and the cap keeps the sheet below the status bar.
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
    val windowHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val statusBarTopDp =
        WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding()
            .value
    val maxContentHeight = maxSheetHeightDp(windowHeightDp, statusBarTopDp).dp
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        contentWindowInsets = {
            WindowInsets.safeDrawing
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .union(WindowInsets(bottom = SHEET_NAV_BAR_CLEARANCE))
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxContentHeight)) {
            content()
        }
    }
}
