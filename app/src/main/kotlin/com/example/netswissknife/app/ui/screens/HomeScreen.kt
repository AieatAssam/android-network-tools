package com.example.netswissknife.app.ui.screens

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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class ToolCardItem(
    val name: String,
    val description: String,
    val icon: ImageVector
)

private val toolCards = listOf(
    ToolCardItem("Ping",         "ICMP round-trip latency",     Icons.Default.NetworkCheck),
    ToolCardItem("Traceroute",   "Network path hop analysis",   Icons.Default.Router),
    ToolCardItem("Port Scanner", "TCP port reachability",       Icons.Default.Search),
    ToolCardItem("LAN Scanner",  "Local device discovery",      Icons.Default.Wifi),
    ToolCardItem("DNS Lookup",   "Resolve hostnames & records", Icons.Default.Language),
)

@Composable
fun HomeScreen() {
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
            ToolGrid()
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
            .padding(horizontal = 24.dp, vertical = 32.dp),
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
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Hub,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimary,
                    modifier           = Modifier.size(44.dp)
                )
            }

            Box(Modifier.height(16.dp))

            Text(
                text      = "Net Swiss Knife",
                style     = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color     = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Box(Modifier.height(6.dp))

            Text(
                text      = "Your all-in-one Android networking toolkit",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ToolGrid() {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(toolCards) { index, tool ->
            AnimatedToolCard(tool = tool, delayMs = index * 60)
        }
    }
}

@Composable
private fun AnimatedToolCard(tool: ToolCardItem, delayMs: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    val cardScale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutBack),
        label         = "card-scale-${tool.name}"
    )
    val cardAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(250),
        label         = "card-alpha-${tool.name}"
    )

    ElevatedCard(
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
                text  = tool.name,
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
