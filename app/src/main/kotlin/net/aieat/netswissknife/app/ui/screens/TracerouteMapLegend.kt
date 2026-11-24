
package net.aieat.netswissknife.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ProbeStats
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteClassification
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.Stability
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TraceRouteMap
import net.aieat.netswissknife.core.network.traceroute.HopResult
import kotlin.math.roundToInt

private const val PERCENT = 100
private val LegendSampleWidth = 28.dp
private val LegendSampleHeight = 14.dp

/** Details of a tapped stop, with the same RTT text and colour as the hop list. */
@Composable
internal fun StopCallout(
    stop: RouteStop,
    colorFor: (Long?) -> Color,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("traceroute_map_callout")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (stop.disputed) {
                Text(
                    stringResource(R.string.traceroute_map_callout_disputed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            stop.hops.forEach { hop -> CalloutHopRow(hop, colorFor) }
        }
    }
}

@Composable
private fun CalloutHopRow(
    hop: HopResult,
    colorFor: (Long?) -> Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.traceroute_map_callout_hop, hop.hopNumber),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                hop.ip.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            Text(
                rttText(hop.rtTimeMs),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colorFor(hop.rtTimeMs),
            )
        }
        hop.geoLocation?.let {
            Text(
                hopLocationLabel(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        val probes = ProbeStats.of(hop)
        val replies = stringResource(R.string.traceroute_map_callout_replies, probes.answered, probes.sent)
        val jitter = probes.jitterMs?.let { stringResource(R.string.traceroute_map_callout_jitter, it) }
        Text(
            listOfNotNull(replies, jitter).joinToString(stringResource(R.string.traceroute_list_separator)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Text legend for every visual channel the map draws, and only those; colour is never the only signal.
 * While [live], the ring marking the newest stop is explained too.
 */
@Composable
internal fun MapLegend(
    map: TraceRouteMap,
    live: Boolean,
    colorFor: (Long?) -> Color,
) {
    Column(modifier = Modifier.testTag("traceroute_map_legend"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (map.segments.any { !it.isUnknown }) {
            LegendHeading(stringResource(R.string.traceroute_map_legend_colour))
            TraceLatencyBand.entries.filter { it != TraceLatencyBand.UNKNOWN }.forEach { band ->
                LegendRow(bandLabel(band), colorFor(band.lowerBoundMs), ArcWidthMedium, Stability.STABLE)
            }
            WidthLegend()
        }
        if (map.segments.isNotEmpty()) {
            LineLegend(map.linePatterns, map.stabilityMeasurable, colorFor(null))
        }
        if (live && map.head != null) LegendNote(stringResource(R.string.traceroute_map_marker_head))
        if ((map.stops + map.disputedStops).any { it.isDestination }) {
            LegendNote(stringResource(R.string.traceroute_map_marker_destination))
        }
        if (map.disputedStops.isNotEmpty()) LegendNote(stringResource(R.string.traceroute_map_marker_disputed))
    }
}

@Composable
private fun WidthLegend() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fast = (RouteClassification.FAST_FROM_FRACTION * PERCENT).roundToInt()
    val slow = (RouteClassification.SLOW_BELOW_FRACTION * PERCENT).roundToInt()
    val minKm = RouteClassification.MIN_SPEED_DISTANCE_KM.roundToInt()
    LegendHeading(stringResource(R.string.traceroute_map_legend_width))
    LegendRow(stringResource(R.string.traceroute_map_width_fast, fast), muted, ArcWidthFast, Stability.STABLE)
    LegendRow(
        stringResource(R.string.traceroute_map_width_medium, slow, fast - 1, minKm),
        muted,
        ArcWidthMedium,
        Stability.STABLE,
    )
    LegendRow(stringResource(R.string.traceroute_map_width_slow, slow), muted, ArcWidthSlow, Stability.STABLE)
}

@Composable
private fun LineLegend(
    patterns: Set<Stability>,
    stabilityMeasurable: Boolean,
    unknownColor: Color,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LegendHeading(stringResource(R.string.traceroute_map_legend_line))
    if (Stability.STABLE in patterns) {
        LegendRow(stringResource(R.string.traceroute_map_line_solid), muted, ArcWidthMedium, Stability.STABLE)
    }
    if (Stability.UNSTABLE in patterns) {
        val jitterPct = (RouteClassification.HIGH_JITTER_MIN_FRACTION * PERCENT).roundToInt()
        LegendRow(
            stringResource(R.string.traceroute_map_line_dotted, RouteClassification.HIGH_JITTER_MIN_MS, jitterPct),
            muted,
            ArcWidthMedium,
            Stability.UNSTABLE,
        )
    }
    if (Stability.NOT_MEASURED in patterns) {
        LegendRow(stringResource(R.string.traceroute_map_line_dash_dot), muted, ArcWidthMedium, Stability.NOT_MEASURED)
        if (!stabilityMeasurable) LegendNote(stringResource(R.string.traceroute_map_stability_needs_probes))
    }
    if (Stability.UNKNOWN in patterns) {
        LegendRow(stringResource(R.string.traceroute_map_line_dashed), unknownColor, ArcWidthMedium, Stability.UNKNOWN)
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
