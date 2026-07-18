package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing

/**
 * Standard hero header for all tool screens.
 *
 * Provides an ElevatedCard with a gradient background, icon, title, subtitle,
 * and a trailing help icon button. Screens that need animated icons can pass a
 * custom [iconContent] composable instead of relying on the default.
 */
@Composable
fun ToolHeroHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconContent: @Composable (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        )
                    )
                )
                .padding(AppSpacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconContent != null) {
                    iconContent()
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(AppSpacing.m))

                Column(modifier = Modifier.weight(1f)) {
                    HeroTitleText(
                        text = title,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (onHelpClick != null) {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.action_help),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hero title that starts at displaySmall and shrinks until the text fits on
 * one line, so long tool names ("Wake-on-LAN", "Subnet Calculator") never
 * truncate with an ellipsis.
 */
@Composable
fun HeroTitleText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.displaySmall
    var textStyle by remember(text) { mutableStateOf(baseStyle) }
    var ready by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        style = textStyle,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.let { if (ready) it else it.alpha(0f) },
        onTextLayout = { result ->
            if (result.didOverflowWidth) {
                textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.92f)
            } else {
                ready = true
            }
        },
    )
}
