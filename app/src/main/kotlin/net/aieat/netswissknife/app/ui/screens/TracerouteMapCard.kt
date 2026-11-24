
package net.aieat.netswissknife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewMode
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapLegendContent
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapSelection
import net.aieat.netswissknife.app.ui.screens.traceroute.map.OffRouteHops
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TraceRouteMap
import net.aieat.netswissknife.core.network.traceroute.CountryFlags
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult

/** City and country of a hop, as both the hop list and the route map callout show it. */
internal fun hopLocationLabel(geo: HopGeoLocation): String =
    buildString {
        if (geo.city.isNotBlank()) append("${geo.city}, ")
        append(CountryFlags.label(geo.countryCode, geo.country))
    }

/** Icon and label for each result tab, in the order of [modes]. */
@Composable
internal fun tracerouteViewTabs(modes: List<TracerouteViewMode>): List<Pair<ImageVector, String>> =
    modes.map { mode ->
        val icon =
            when (mode) {
                TracerouteViewMode.Visual -> Icons.Default.Router
                TracerouteViewMode.Map -> Icons.Default.Public
                TracerouteViewMode.Raw -> Icons.AutoMirrored.Filled.TextSnippet
            }
        val label =
            when (mode) {
                TracerouteViewMode.Visual -> R.string.traceroute_tab_hops
                TracerouteViewMode.Map -> R.string.traceroute_tab_map
                TracerouteViewMode.Raw -> R.string.traceroute_tab_raw
            }
        icon to stringResource(label)
    }

/**
 * World map of the route: arcs between located hops in hop order, coloured by the arriving
 * hop's median RTT, thickened by speed against light in fibre, with a line pattern for reply
 * stability. While [live], the map grows as hops arrive. [stopped] marks a trace the user or a
 * time limit stopped, whose missing replies are not counted as lost. [colorFor] is the hop list's
 * RTT colour, so both views always agree.
 */
@Composable
internal fun TracerouteMapCard(
    hops: List<HopResult>,
    live: Boolean,
    stopped: Boolean,
    colorFor: (Long?) -> Color,
    modifier: Modifier = Modifier,
) {
    val map = remember(hops, stopped) { TraceRouteMap.from(hops, stopped) }
    ElevatedCard(modifier = modifier.fillMaxWidth().testTag("traceroute_map_card")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (map.isEmpty) {
                MapEmptyState(live)
                return@Column
            }
            map.firstLocatedHop?.let { first ->
                Text(
                    text = stringResource(R.string.traceroute_map_starts_at, first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var selection by remember { mutableStateOf<MapSelection?>(null) }
            RouteMapCanvas(
                map = map,
                live = live,
                colorFor = colorFor,
                selection = selection,
                onSelect = { selection = it },
            )
            OffRouteNotes(map.offRoute, live)
            MapSelectionCallout(map, selection, colorFor)
            Text(
                text = stringResource(R.string.traceroute_map_gesture_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MapLegend(content = remember(map, live) { MapLegendContent.of(map, live) }, colorFor = colorFor)
            Text(
                text = stringResource(R.string.traceroute_map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MapSelectionCallout(
    map: TraceRouteMap,
    selection: MapSelection?,
    colorFor: (Long?) -> Color,
) {
    when (selection) {
        is MapSelection.Stops -> {
            MapSelection.stopsOf(map, selection).takeIf { it.isNotEmpty() }?.let { StopCallout(it, colorFor) }
        }

        is MapSelection.Segment -> {
            MapSelection.segmentOf(map, selection)?.let { SegmentCallout(map, it, colorFor) }
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun MapEmptyState(live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("traceroute_map_empty")) {
        Icon(
            Icons.Default.FmdGood,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (live) R.string.traceroute_map_waiting else R.string.traceroute_stats_no_geo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "18 to 30", "3, 5 and 7 to 9": hop numbers as prose, consecutive runs as ranges. */
@Composable
internal fun hopListText(hopNumbers: List<Int>): String {
    val runs =
        RouteStop.runsOf(hopNumbers).map { run ->
            if (run.first == run.last) {
                "${run.first}"
            } else {
                stringResource(R.string.traceroute_map_hop_range, run.first, run.last)
            }
        }
    if (runs.size < 2) return runs.joinToString()
    val head = runs.dropLast(1).joinToString(stringResource(R.string.traceroute_map_list_comma))
    return stringResource(R.string.traceroute_map_list_and, head, runs.last())
}

/**
 * One exact sentence per reason a hop after the first located one is missing from the drawn route.
 * While [live], hops without a location may still get one.
 */
@Composable
private fun OffRouteNotes(
    offRoute: OffRouteHops,
    live: Boolean,
) {
    val notLocated =
        if (live) R.plurals.traceroute_map_off_not_located_yet else R.plurals.traceroute_map_off_not_located
    val notes =
        listOf(
            R.plurals.traceroute_map_off_no_reply to offRoute.noReply,
            notLocated to offRoute.notLocated,
            R.plurals.traceroute_map_off_disputed to offRoute.disputed,
        ).filter { it.second.isNotEmpty() }
    if (notes.isEmpty()) return
    Column(modifier = Modifier.testTag("traceroute_map_off_route"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        notes.forEach { (plural, hops) ->
            Text(
                text = pluralStringResource(plural, hops.size, hopListText(hops)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An RTT in the same format as the hop list's RTT badge. */
@Composable
internal fun rttText(rttMs: Long?): String {
    val noReply = stringResource(R.string.traceroute_probe_no_reply)
    return if (rttMs != null) "${rttMs}ms" else noReply
}

@Composable
private fun stopDescription(stop: RouteStop): String =
    stringResource(
        R.string.traceroute_map_stop_description,
        stop.label,
        hopLocationLabel(stop.location),
        rttText(stop.rttMs),
    )

/** Spoken summary of every stop, and while [live] of the trace's progress, for the canvas description. */
@Composable
internal fun mapDescription(
    map: TraceRouteMap,
    live: Boolean,
): String {
    val parts =
        map.stops.map { stopDescription(it) } +
            map.disputedStops.map { stringResource(R.string.traceroute_map_disputed_description, stopDescription(it)) }
    val route = stringResource(R.string.traceroute_map_description, parts.joinToString("; "))
    if (!live) return route
    val head = map.head?.let { stringResource(R.string.traceroute_map_live_head, it.label) }
    val pending =
        map.offRoute.notLocated.takeIf { it.isNotEmpty() }?.let {
            pluralStringResource(R.plurals.traceroute_map_off_not_located_yet, it.size, hopListText(it))
        }
    val running = stringResource(R.string.traceroute_map_live_description)
    return listOfNotNull(running, route, head, pending).joinToString(". ")
}
