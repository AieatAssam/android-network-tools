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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.homeGridColumns
import net.aieat.netswissknife.app.ui.components.isCompactHero
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolInfo
import net.aieat.netswissknife.app.ui.theme.AppMotion

object HomeScreenTestTags {
    const val TOOL_GRID = "home_tool_grid"
    const val SCROLL_HINT = "home_tools_scroll_hint"
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    var headerVisible by remember { mutableStateOf(false) }
    var cardsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(200)
        cardsVisible = true
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(AppMotion.enter(500)) + slideInVertically(AppMotion.enter(500)) { -40 },
        ) {
            val config = LocalConfiguration.current
            HeroHeader(compact = isCompactHero(config.screenHeightDp, config.screenWidthDp))
        }

        AnimatedVisibility(
            visible = cardsVisible,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            enter = fadeIn(tween(durationMillis = 400, delayMillis = 100, easing = AppMotion.EmphasizedDecelerate)),
        ) {
            ToolGrid(onNavigate = onNavigate)
        }
    }
}

@Composable
private fun HeroHeader(compact: Boolean) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ).padding(
                    horizontal = 24.dp,
                    vertical = if (compact) 8.dp else 28.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Compact (phone landscape): the icon and name share one row, and the tagline is dropped, so
        // the tool grid starts above the fold (PHA-21, PHB-25).
        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroHeaderIcon(size = 40.dp, iconSize = 22.dp)
                Text(
                    text = stringResource(R.string.app_name_full),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeroHeaderIcon(size = 72.dp, iconSize = 40.dp)

                Box(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.app_name_full),
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )

                Box(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HeroHeaderIcon(
    size: Dp,
    iconSize: Dp,
) {
    var iconReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { iconReady = true }

    val iconScale by animateFloatAsState(
        targetValue = if (iconReady) 1f else 0.4f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "icon-scale",
    )

    Box(
        modifier =
            Modifier
                .scale(iconScale)
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Hub,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(iconSize),
        )
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

    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.home_all_tools),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        // Rows are laid out by hand so every tile in a row shares that row's height (PHA-22). A lazy
        // grid measures each tile on its own, so tiles with longer descriptions left ragged row ends.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            val columns = homeGridColumns(availableWidthDp = maxWidth.value.toInt() - 32)
            val rows = NavRoutes.allTools.chunked(columns)
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag(HomeScreenTestTags.TOOL_GRID),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.first().route }) { rowIndex, row ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEachIndexed { column, tool ->
                            AnimatedToolCard(
                                tool = tool,
                                delayMs = (rowIndex * columns + column) * 60,
                                skipEntrance = gridAppeared,
                                onClick = hapticAction { onNavigate(tool.route) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                            )
                        }
                        // Keep a short last row at the same tile width as the full rows.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // The hint has its own slot below the grid. It never overlays a tile, and the slot keeps its
        // height whether or not the grid can scroll, so the grid does not resize when the hint appears.
        HomeScrollHint(visible = listState.canScrollForward)
    }
}

/** The "scroll for more tools" line. Only its alpha animates; the slot height is fixed. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HomeScrollHint(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(200),
        label = "home-scroll-hint-alpha",
    )
    Box(
        modifier = Modifier.fillMaxWidth().height(HOME_SCROLL_HINT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.home_tools_scroll_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alpha).testTag(HomeScreenTestTags.SCROLL_HINT),
        )
    }
}

private val HOME_SCROLL_HINT_HEIGHT = 32.dp

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun AnimatedToolCard(
    tool: ToolInfo,
    delayMs: Int,
    skipEntrance: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(skipEntrance) }
    LaunchedEffect(Unit) {
        if (!skipEntrance) {
            delay(delayMs.toLong())
            visible = true
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutBack),
        label = "card-scale-${tool.route}",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.effect(250),
        label = "card-alpha-${tool.route}",
    )

    ElevatedCard(
        onClick = onClick,
        modifier =
            modifier
                .scale(cardScale)
                .alpha(cardAlpha)
                .semantics(mergeDescendants = true) { role = Role.Button },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Text(
                text = stringResource(tool.labelRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )

            Text(
                text = stringResource(tool.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
