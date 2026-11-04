package net.aieat.netswissknife.app.ui.screens.wol

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R

/** Explains what happened to the saved name when a saved MAC was saved again. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun WolSaveNoticeText(notice: WolSaveNotice) {
    val message =
        when (notice) {
            is WolSaveNotice.KeptName -> {
                stringResource(R.string.wol_device_name_kept, notice.name)
            }

            is WolSaveNotice.Renamed -> {
                stringResource(R.string.wol_device_renamed, notice.previousName, notice.name)
            }
        }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
