package com.example.netswissknife.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.netswissknife.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsSheet(
    pinnedRoutes: List<String>,
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    maxPinned: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.more_sheet_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.more_sheet_subtitle, maxPinned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            NavRoutes.allTools.forEachIndexed { index, tool ->
                val isPinned = pinnedRoutes.contains(tool.route)
                val canPin = isPinned || pinnedRoutes.size < maxPinned

                ToolSheetRow(
                    tool = tool,
                    isPinned = isPinned,
                    canPin = canPin,
                    onNavigate = { onNavigate(tool.route) },
                    onTogglePin = { onTogglePin(tool.route) }
                )

                if (index < NavRoutes.allTools.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToolSheetRow(
    tool: ToolInfo,
    isPinned: Boolean,
    canPin: Boolean,
    onNavigate: () -> Unit,
    onTogglePin: () -> Unit
) {
    val pinTint by animateColorAsState(
        targetValue = when {
            isPinned -> MaterialTheme.colorScheme.primary
            canPin   -> MaterialTheme.colorScheme.onSurfaceVariant
            else     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        animationSpec = tween(200),
        label = "pin-tint-${tool.route}"
    )
    val iconBg by animateColorAsState(
        targetValue = if (isPinned)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(200),
        label = "icon-bg-${tool.route}"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isPinned)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(200),
        label = "icon-tint-${tool.route}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = tool.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onTogglePin,
            enabled = canPin
        ) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (isPinned)
                    stringResource(R.string.more_unpin_description, tool.label)
                else
                    stringResource(R.string.more_pin_description, tool.label),
                tint = pinTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
