
package net.aieat.netswissknife.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ArcWidth
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapLegendContent
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapSizing
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MarkerNote
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteClassification
import net.aieat.netswissknife.app.ui.screens.traceroute.map.Stability
import kotlin.math.roundToInt

private const val PERCENT = 100
private val LegendSampleWidth = 28.dp
private val LegendSampleHeight = 14.dp

/**
 * Text legend for exactly the colours, widths, line patterns and markers the map draws, all read from
 * [content]; colour is never the only signal. Two columns on a wide card; it can be collapsed.
 */
@Composable
internal fun MapLegend(
    content: MapLegendContent,
    colorFor: (Long?) -> Color,
) {
    if (content.isEmpty) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    // Width read after layout rather than with BoxWithConstraints, which nests a subcomposition in AnimatedContent.
    var widthDp by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .onSizeChanged { widthDp = with(density) { it.width.toDp().value } }
                .testTag("traceroute_map_legend"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.traceroute_map_legend_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val toggle = if (expanded) R.string.traceroute_map_legend_hide else R.string.traceroute_map_legend_show
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("traceroute_map_legend_toggle"),
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(toggle),
                )
            }
        }
        if (expanded) {
            if (MapSizing.legendColumns(widthDp) == 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendColumn(Modifier.weight(1f)) { RouteLegend(content, colorFor) }
                    LegendColumn(Modifier.weight(1f)) { MarkingLegend(content, colorFor) }
                }
            } else {
                LegendColumn(Modifier.fillMaxWidth()) {
                    RouteLegend(content, colorFor)
                    MarkingLegend(content, colorFor)
                }
            }
        }
    }
}

@Composable
private fun LegendColumn(
    modifier: Modifier,
    body: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { body() }
}

@Composable
private fun RouteLegend(
    content: MapLegendContent,
    colorFor: (Long?) -> Color,
) {
    if (content.latencyBands.isNotEmpty()) {
        LegendHeading(stringResource(R.string.traceroute_map_legend_colour))
        content.latencyBands.forEach { band ->
            LegendRow(bandLabel(band), colorFor(band.lowerBoundMs), ArcWidthMedium, Stability.STABLE)
        }
    }
    if (content.widths.isNotEmpty()) WidthLegend(content.widths)
}

@Composable
private fun MarkingLegend(
    content: MapLegendContent,
    colorFor: (Long?) -> Color,
) {
    if (content.linePatterns.isNotEmpty()) {
        LineLegend(content.linePatterns, content.singleProbeTrace, colorFor(null))
    }
    content.markers.forEach { marker ->
        val text =
            when (marker) {
                MarkerNote.HEAD -> R.string.traceroute_map_marker_head
                MarkerNote.DESTINATION -> R.string.traceroute_map_marker_destination
                MarkerNote.DISPUTED -> R.string.traceroute_map_marker_disputed
            }
        LegendNote(stringResource(text))
    }
}

@Composable
private fun WidthLegend(widths: List<ArcWidth>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fast = (RouteClassification.FAST_FROM_FRACTION * PERCENT).roundToInt()
    val slow = (RouteClassification.SLOW_BELOW_FRACTION * PERCENT).roundToInt()
    val minKm = RouteClassification.MIN_SPEED_DISTANCE_KM.roundToInt()
    LegendHeading(stringResource(R.string.traceroute_map_legend_width))
    widths.forEach { width ->
        val text =
            when (width) {
                ArcWidth.THICK -> stringResource(R.string.traceroute_map_width_fast, fast)
                ArcWidth.MEDIUM -> stringResource(R.string.traceroute_map_width_medium, slow, fast - 1, minKm)
                ArcWidth.THIN -> stringResource(R.string.traceroute_map_width_slow, slow)
            }
        LegendRow(text, muted, arcWidthDp(width), Stability.STABLE)
    }
}

@Composable
private fun LineLegend(
    patterns: List<Stability>,
    singleProbeTrace: Boolean,
    unknownColor: Color,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LegendHeading(stringResource(R.string.traceroute_map_legend_line))
    patterns.forEach { pattern ->
        when (pattern) {
            Stability.STABLE -> {
                LegendRow(stringResource(R.string.traceroute_map_line_solid), muted, ArcWidthMedium, pattern)
            }

            Stability.UNSTABLE -> {
                val jitterPct = (RouteClassification.HIGH_JITTER_MIN_FRACTION * PERCENT).roundToInt()
                val minMs = RouteClassification.HIGH_JITTER_MIN_MS
                val text = stringResource(R.string.traceroute_map_line_dotted, minMs, jitterPct)
                LegendRow(text, muted, ArcWidthMedium, pattern)
            }

            Stability.NOT_MEASURED -> {
                LegendRow(stringResource(R.string.traceroute_map_line_dash_dot), muted, ArcWidthMedium, pattern)
                if (singleProbeTrace) LegendNote(stringResource(R.string.traceroute_map_stability_needs_probes))
            }

            Stability.UNKNOWN -> {
                LegendRow(stringResource(R.string.traceroute_map_line_dashed), unknownColor, ArcWidthMedium, pattern)
            }
        }
    }
}

@Composable
private fun bandLabel(band: TraceLatencyBand): String {
    val lower = band.lowerBoundMs ?: 0L
    val upper = band.upperBoundMs
    return when {
        lower == 0L && upper != null -> stringResource(R.string.traceroute_map_band_under, upper)
        upper == null -> stringResource(R.string.traceroute_map_band_over, lower)
        else -> stringResource(R.string.traceroute_map_band_range, lower, upper - 1)
    }
}

@Composable
private fun LegendHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun LegendNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LegendRow(
    text: String,
    color: Color,
    width: Dp,
    stability: Stability,
) {
    val density = LocalDensity.current
    val stroke = remember(density, width, stability) { arcStroke(density, with(density) { width.toPx() }, stability) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val sampleModifier =
            Modifier.width(LegendSampleWidth).heightIn(min = LegendSampleHeight, max = LegendSampleHeight)
        Canvas(modifier = sampleModifier) {
            val y = size.height / 2f
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke.width, stroke.cap, stroke.pathEffect)
        }
        LegendNote(text)
    }
}
