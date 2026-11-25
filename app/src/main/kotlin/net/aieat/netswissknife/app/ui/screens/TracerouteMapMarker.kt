package net.aieat.netswissknife.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val MarkerRingWidth = 1.5.dp
private val DestinationRingRadius = 10.dp

/** The map's theme colours; the legend reads the same ones, so its samples match the drawing. */
@Composable
internal fun mapColors(): MapColors {
    val scheme = MaterialTheme.colorScheme
    return MapColors(
        ocean = scheme.surfaceContainerHigh,
        land = scheme.outlineVariant,
        surface = scheme.surface,
        outline = scheme.onSurface,
        selected = scheme.primary,
        disputed = scheme.error,
    )
}

/** A stop's marker as the map draws it; the legend draws its samples with this too. */
internal fun DrawScope.drawMarker(
    center: Offset,
    fill: Color,
    colors: MapColors,
    disputed: Boolean,
    destination: Boolean,
) {
    val radius = MarkerRadius.toPx()
    val ringWidth = MarkerRingWidth.toPx()
    if (destination) drawCircle(colors.outline, DestinationRingRadius.toPx(), center, style = Stroke(ringWidth))
    if (disputed) {
        drawCircle(colors.surface, radius, center)
        drawCircle(fill, radius, center, style = Stroke(ringWidth * 2))
    } else {
        drawCircle(fill, radius, center)
        drawCircle(colors.surface, radius, center, style = Stroke(ringWidth))
    }
}
