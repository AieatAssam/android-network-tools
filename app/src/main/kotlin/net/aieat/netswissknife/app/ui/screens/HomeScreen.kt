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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolInfo
import kotlinx.coroutines.delay

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
            enter   = fadeIn(tween(500)) + slideInVertically(tween(500)) { -40 }
        ) {
            HeroHeader()
        }

        AnimatedVisibility(
            visible = cardsVisible,
            enter   = fadeIn(tween(400, delayMillis = 100))
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
                text      = stringResource(R.string.app_name),
                style     = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = stringResource(R.string.home_all_tools),
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(NavRoutes.allTools) { index, tool ->
                AnimatedToolCard(
                    tool    = tool,
                    delayMs = index * 60,
                    onClick = { onNavigate(tool.route) }
                )
            }
        }
    }
}

@Composable
private fun AnimatedToolCard(tool: ToolInfo, delayMs: Int, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    val cardScale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutBack),
        label         = "card-scale-${tool.route}"
    )
    val cardAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(250),
        label         = "card-alpha-${tool.route}"
    )

    ElevatedCard(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .alpha(cardAlpha),
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
