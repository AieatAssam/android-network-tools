package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ToolTouchTargetSize
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing

private const val SHIMMER_MILLIS = 900
private const val SHIMMER_MIN_ALPHA = 0.3f
private const val SHIMMER_MAX_ALPHA = 0.8f
private const val SHIMMER_LAST_LINE_FRACTION = 0.6f
private const val SHIMMER_LINE_SPACING_DP = 8

/** A titled ElevatedCard: the common container for every Network Info section. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

/** A value that can be copied: by its copy icon, or by pressing and holding the row. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoValueRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
    detail: String? = null,
) {
    val copy = hapticAction { onCopy(value) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(value) { detectTapGestures(onLongPress = { copy() }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = copy,
            modifier = Modifier.size(ToolTouchTargetSize),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.netinfo_copy_description, label),
            )
        }
    }
}

/** A read-only value with no copy action, for settings such as MTU or proxy. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoFactRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Pulsing placeholder bars shown while a section has not been read yet. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoShimmerLines(lines: Int = 3) {
    val transition = rememberInfiniteTransition(label = "netinfo-shimmer")
    val alpha by transition.animateFloat(
        initialValue = SHIMMER_MIN_ALPHA,
        targetValue = SHIMMER_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS), RepeatMode.Reverse),
        label = "netinfo-shimmer-alpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(SHIMMER_LINE_SPACING_DP.dp)) {
        repeat(lines) { index ->
            val fraction = if (index == lines - 1) SHIMMER_LAST_LINE_FRACTION else 1f
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(AppSpacing.m)
                        .clip(AppShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}
