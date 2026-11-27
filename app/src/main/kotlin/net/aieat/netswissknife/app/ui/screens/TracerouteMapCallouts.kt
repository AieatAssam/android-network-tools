
package net.aieat.netswissknife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ProbeStats
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteSegment
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.Stability
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TraceRouteMap
import net.aieat.netswissknife.core.network.traceroute.HopResult
import kotlin.math.roundToInt

private const val PERCENT = 100

/**
 * Details of a tapped marker or cluster: every hop in it, with the same RTT text and colour as the hop
 * list, and for a disputed stop the reason it is off the route.
 */
@Composable
internal fun StopCallout(
    stops: List<RouteStop>,
    colorFor: (Long?) -> Color,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("traceroute_map_callout")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stops.forEach { stop ->
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
}

/** "median of 3" or "1 reply": what a hop's single RTT figure stands for. */
@Composable
internal fun rttBasis(answered: Int): String = pluralStringResource(R.plurals.traceroute_rtt_basis, answered, answered)

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
                rttText(hop.rttMedianMs),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colorFor(hop.rttMedianMs),
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
        val basis = rttBasis(probes.answered).takeIf { probes.answered > 0 }
        val jitter = probes.jitterMs?.let { stringResource(R.string.traceroute_map_callout_jitter, it) }
        Text(
            listOfNotNull(replies, basis, jitter).joinToString(stringResource(R.string.traceroute_list_separator)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Details of a tapped arc in plain words: where it runs, the RTT it adds, how far and how fast, and how
 * steady the replies were. An unknown path names the hops in between and nothing it cannot know.
 */
@Composable
internal fun SegmentCallout(
    map: TraceRouteMap,
    segment: RouteSegment,
    colorFor: (Long?) -> Color,
) {
    val from = map.allStops[segment.fromStop]
    val to = map.allStops[segment.toStop]
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("traceroute_map_segment_callout")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.traceroute_map_segment_title, from.label, to.label),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                stringResource(
                    R.string.traceroute_map_segment_places,
                    hopLocationLabel(from.location),
                    hopLocationLabel(to.location),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (segment.joinsDisputed) {
                CalloutLine(
                    pluralStringResource(
                        R.plurals.traceroute_map_segment_disputed,
                        segment.disputedHopNumbers.size,
                        hopListText(segment.disputedHopNumbers),
                    ),
                )
            }
            if (segment.isUnknown && segment.skippedHops > 0) {
                CalloutLine(
                    pluralStringResource(
                        R.plurals.traceroute_map_segment_unknown,
                        segment.skippedHops,
                        hopListText(segment.skippedHopNumbers),
                    ),
                )
            }
            if (!segment.isUnknown) MeasuredSegmentLines(to, segment, colorFor)
        }
    }
}

@Composable
private fun MeasuredSegmentLines(
    to: RouteStop,
    segment: RouteSegment,
    colorFor: (Long?) -> Color,
) {
    segment.rttMs?.let { rtt ->
        Text(
            stringResource(R.string.traceroute_map_segment_rtt, to.label, rtt, rttBasis(segment.probes.answered)),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colorFor(rtt),
        )
    }
    segment.addedRttMs?.let { added ->
        CalloutLine(
            if (added >= 0) {
                stringResource(R.string.traceroute_map_segment_added_rtt, added)
            } else {
                stringResource(R.string.traceroute_map_segment_rtt_drop, -added)
            },
        )
    }
    segment.distanceKm?.let { CalloutLine(stringResource(R.string.traceroute_map_segment_distance, it.roundToInt())) }
    val speed = segment.speedFraction
    CalloutLine(
        if (speed != null) {
            stringResource(R.string.traceroute_map_segment_speed, (speed * PERCENT).roundToInt())
        } else {
            stringResource(R.string.traceroute_map_segment_speed_unknown)
        },
    )
    CalloutLine(stabilityText(segment))
}

@Composable
private fun stabilityText(segment: RouteSegment): String {
    val probes = segment.probes
    return when (segment.stability) {
        Stability.STABLE -> {
            stringResource(R.string.traceroute_map_segment_steady)
        }

        Stability.UNSTABLE -> {
            val jitter = probes.jitterMs
            if (probes.answered < probes.sent || jitter == null) {
                stringResource(R.string.traceroute_map_segment_unsteady_loss, probes.answered, probes.sent)
            } else {
                stringResource(R.string.traceroute_map_segment_unsteady_jitter, jitter)
            }
        }

        Stability.NOT_MEASURED, Stability.UNKNOWN -> {
            stringResource(R.string.traceroute_map_segment_not_measured)
        }
    }
}

@Composable
private fun CalloutLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
