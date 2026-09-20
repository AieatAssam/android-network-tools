package net.aieat.netswissknife.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolInfo
import net.aieat.netswissknife.app.ui.theme.AppMotion
import kotlinx.coroutines.delay

object HomeScreenTestTags {
    const val TOOL_GRID = "home_tool_grid"
    const val SCROLL_HINT = "home_tools_scroll_hint"
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    var headerVisible by remember { mutableStateOf(false) }
    var cardsVisible  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(200)
        cardsVisible  = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = headerVisible,
            enter   = fadeIn(AppMotion.enter(500)) + slideInVertically(AppMotion.enter(500)) { -40 }
        ) {
            HeroHeader()
        }

        AnimatedVisibility(
            visible = cardsVisible,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            enter   = fadeIn(tween(durationMillis = 400, delayMillis = 100, easing = AppMotion.EmphasizedDecelerate))
        ) {
            ToolGrid(onNavigate = onNavigate)
        }
    }
}

@Composable
private fun HeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var iconReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { iconReady = true }

            val iconScale by animateFloatAsState(
                targetValue   = if (iconReady) 1f else 0.4f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                label = "icon-scale"
            )

            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Hub,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimary,
                    modifier           = Modifier.size(40.dp)
                )
            }

            Box(Modifier.height(12.dp))

            Text(
                text      = stringResource(R.string.app_name_full),
                style     = MaterialTheme.typography.displaySmall,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                color     = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Box(Modifier.height(4.dp))

            Text(
                text      = stringResource(R.string.home_subtitle),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ToolGrid(onNavigate: (String) -> Unit) {
    // Cards animate in with a per-index stagger only on the grid's first appearance.
    // Off-screen grid items get disposed and recomposed as they scroll back into view;
    // without this flag each recomposition would replay the stagger delay + fade/scale,
    // making icons appear to vanish and slowly fade back in while scrolling.
    var gridAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(NavRoutes.allTools.size * 60L + AppMotion.DurationMedium)
        gridAppeared = true
    }

    val gridState = rememberLazyGridState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text     = stringResource(R.string.home_all_tools),
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyVerticalGrid(
                state                 = gridState,
                modifier              = Modifier
                    .fillMaxSize()
                    .testTag(HomeScreenTestTags.TOOL_GRID),
                columns               = GridCells.Adaptive(minSize = 160.dp),
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(NavRoutes.allTools, key = { _, tool -> tool.route }) { index, tool ->
                    AnimatedToolCard(
                        tool           = tool,
                        delayMs        = index * 60,
                        skipEntrance   = gridAppeared,
                        onClick        = hapticAction { onNavigate(tool.route) }
                    )
                }
            }

            if (gridState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .testTag(HomeScreenTestTags.SCROLL_HINT),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = stringResource(R.string.home_tools_scroll_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedToolCard(tool: ToolInfo, delayMs: Int, skipEntrance: Boolean, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(skipEntrance) }
    LaunchedEffect(Unit) {
        if (!skipEntrance) {
            delay(delayMs.toLong())
            visible = true
        }
    }

    val cardScale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutBack),
        label         = "card-scale-${tool.route}"
    )
    val cardAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = AppMotion.effect(250),
        label         = "card-alpha-${tool.route}"
    )

    ElevatedCard(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .alpha(cardAlpha)
            .semantics(mergeDescendants = true) { role = Role.Button },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = tool.icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier           = Modifier.size(22.dp)
                )
            }

            Text(
                text  = tool.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Text(
                text  = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
