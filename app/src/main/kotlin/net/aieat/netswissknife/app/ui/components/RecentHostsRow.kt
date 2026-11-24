package net.aieat.netswissknife.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R

@Composable
fun RecentHostsRow(
    recentHosts: List<String>,
    onHostSelected: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    selectionEnabled: Boolean = true,
    actionsEnabled: Boolean = true,
    currentValue: String? = null,
    removeDescription: String = stringResource(R.string.action_remove_recent),
    clearDescription: String = stringResource(R.string.action_clear_recents),
) {
    val shownHosts = recentHostsExcept(recentHosts, currentValue)
    AnimatedVisibility(
        visible = shownHosts.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val listState = rememberLazyListState()
        // Keyed items keep their scroll offset when a new host is prepended, which left the
        // newest chip clipped at the start edge. Re-anchor to the first chip on every change.
        LaunchedEffect(shownHosts.firstOrNull()) { listState.scrollToItem(0) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.recent_hosts_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            LazyRow(
                modifier =
                    Modifier
                        .weight(1f)
                        .scrollEdgeFade(
                            canScrollBackward = listState.canScrollBackward,
                            canScrollForward = listState.canScrollForward,
                        ),
                state = listState,
                contentPadding = PaddingValues(start = 4.dp, end = ScrollEdgeFadeWidth),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shownHosts, key = { it }) { host ->
                    RecentHostChip(
                        host = host,
                        selectionEnabled = selectionEnabled,
                        actionsEnabled = actionsEnabled,
                        onSelect = { onHostSelected(host) },
                        onRemove = { onRemoveHost(host) },
                        removeDescription = removeDescription,
                    )
                }
            }
            IconButton(
                onClick = onClearAll,
                enabled = actionsEnabled,
                modifier = Modifier.size(ToolTouchTargetSize),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = clearDescription,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ChipHeight = 32.dp
private val ChipCorner = 8.dp
private val ChipBorderWidth = 1.dp

// The outline ends this far past the remove icon, like a Material input chip's trailing padding.
private val ChipEndInset = 8.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * A recent host as an outlined input chip with the host and its remove button centred on one line. The
 * row is a full touch target tall, the outline a chip's 32dp, so the remove button keeps a 48dp target
 * without pushing the text off centre.
 */
@Composable
private fun RecentHostChip(
    host: String,
    selectionEnabled: Boolean,
    actionsEnabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    removeDescription: String,
) {
    val alpha = if (selectionEnabled) 1f else DISABLED_ALPHA
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = alpha)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier.height(ToolTouchTargetSize).drawBehind {
                val chipHeight = ChipHeight.toPx()
                val border = ChipBorderWidth.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(border / 2f, (size.height - chipHeight) / 2f),
                    size = Size(size.width - ChipEndInset.toPx() - border, chipHeight),
                    cornerRadius = CornerRadius(ChipCorner.toPx()),
                    style = Stroke(border),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = selectionEnabled,
                        role = Role.Button,
                        onClick = onSelect,
                    ).padding(vertical = (ToolTouchTargetSize - ChipHeight) / 2)
                    .clip(RoundedCornerShape(topStart = ChipCorner, bottomStart = ChipCorner))
                    .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = host,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        IconButton(onClick = onRemove, enabled = actionsEnabled, modifier = Modifier.size(ToolTouchTargetSize)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = removeDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Recents without the entry that is already in the field, so the row never repeats the typed value. */
internal fun recentHostsExcept(
    recentHosts: List<String>,
    currentValue: String?,
): List<String> {
    val current = currentValue?.trim().orEmpty()
    if (current.isEmpty()) return recentHosts
    return recentHosts.filterNot { it.trim().equals(current, ignoreCase = true) }
}
