@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose functions are PascalCase by convention.

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
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TraceRouteMap
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TrailingHops
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
 * hop's RTT, thickened by speed against light in fibre, with a line pattern for reply stability.
 * While [live], the map grows as hops arrive. [colorFor] is the hop list's RTT colour, so both
 * views always agree.
 */
@Composable
internal fun TracerouteMapCard(
    hops: List<HopResult>,
    live: Boolean,
    colorFor: (Long?) -> Color,
    modifier: Modifier = Modifier,
) {
    val map = remember(hops) { TraceRouteMap.from(hops) }
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
            var selectedHop by remember { mutableStateOf<Int?>(null) }
            RouteMapCanvas(
                map = map,
                live = live,
                colorFor = colorFor,
                selectedHop = selectedHop,
                onSelect = { selectedHop = it },
            )
            map.trailing?.let { TrailingHopsNote(it, live) }
            (map.stops + map.disputedStops)
                .firstOrNull { selectedHop in it.hopNumbers }
                ?.let { StopCallout(stop = it, colorFor = colorFor) }
            Text(
                text = stringResource(R.string.traceroute_map_gesture_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MapLegend(map = map, live = live, colorFor = colorFor)
            Text(
                text = stringResource(R.string.traceroute_map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/**
 * Hops after the last stop: those still waiting for a location while the trace runs, and all of them
 * once it is done.
 */
@Composable
private fun TrailingHopsNote(
    trailing: TrailingHops,
    live: Boolean,
) {
    val offMap = trailing.notLocated + trailing.noReply
    val text =
        when {
            live && trailing.notLocated > 0 -> {
                pluralStringResource(R.plurals.traceroute_map_not_located, trailing.notLocated, trailing.notLocated)
            }

            live || offMap == 0 -> {
                return
            }

            else -> {
                pluralStringResource(R.plurals.traceroute_map_off_map, offMap, offMap)
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("traceroute_map_trailing"),
    )
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
        rttText(stop.hops.first().rtTimeMs),
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
        map.trailing?.notLocated?.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.traceroute_map_not_located, it, it)
        }
    val running = stringResource(R.string.traceroute_map_live_description)
    return listOfNotNull(running, route, head, pending).joinToString(". ")
}
