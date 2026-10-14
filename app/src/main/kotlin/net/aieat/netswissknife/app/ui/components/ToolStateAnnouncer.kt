package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R

/** Operation states that should be announced when a tool transitions. */
enum class ToolAnnouncementPhase {
    RUNNING,
    CANCELED,
    FINISHED,
    PARTIAL,
    ERROR,
}

/**
 * Announces a concise tool-state change to accessibility services.
 *
 * The one-dp node stays in the semantics tree so TalkBack can receive live-region
 * updates without adding visible text or changing the screen layout. Callers should
 * pass stable state summaries only; do not feed per-result or streaming progress here.
 */
@Composable
fun ToolStateAnnouncer(
    toolName: String,
    phase: ToolAnnouncementPhase?,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    if (phase == null) return

    val message = when (phase) {
        ToolAnnouncementPhase.RUNNING -> stringResource(R.string.a11y_tool_running, toolName)
        ToolAnnouncementPhase.CANCELED -> stringResource(R.string.a11y_tool_canceled, toolName)
        ToolAnnouncementPhase.FINISHED -> if (detail.isNullOrBlank()) {
            stringResource(R.string.a11y_tool_finished, toolName)
        } else {
            stringResource(R.string.a11y_tool_finished_detail, toolName, detail)
        }
        ToolAnnouncementPhase.PARTIAL -> if (detail.isNullOrBlank()) {
            stringResource(R.string.a11y_tool_partial, toolName)
        } else {
            stringResource(R.string.a11y_tool_partial_detail, toolName, detail)
        }
        ToolAnnouncementPhase.ERROR -> if (detail.isNullOrBlank()) {
            stringResource(R.string.a11y_tool_error, toolName)
        } else {
            stringResource(R.string.a11y_tool_error_detail, toolName, detail)
        }
    }

    Text(
        text = message,
        modifier = modifier
            .size(1.dp)
            .alpha(0f)
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            },
    )
}
