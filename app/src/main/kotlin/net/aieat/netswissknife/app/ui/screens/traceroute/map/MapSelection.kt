package net.aieat.netswissknife.app.ui.screens.traceroute.map

/** What the user tapped on the map: a marker, or cluster of markers, or an arc. */
sealed interface MapSelection {
    /** The hops of a tapped marker or cluster; kept as hop numbers, since clusters change with zoom. */
    data class Stops(
        val hopNumbers: List<Int>,
    ) : MapSelection

    data class Segment(
        val key: SegmentKey,
    ) : MapSelection

    companion object {
        /** The stops of [map] holding any hop of [selection], route stops first. */
        fun stopsOf(
            map: TraceRouteMap,
            selection: Stops,
        ): List<RouteStop> = map.allStops.filter { stop -> stop.hopNumbers.any { it in selection.hopNumbers } }

        /** The segment of [map] that [selection] names, or null when the route no longer has it. */
        fun segmentOf(
            map: TraceRouteMap,
            selection: Segment,
        ): RouteSegment? = map.segments.firstOrNull { it.key == selection.key }

        /** A second tap on the same thing clears the selection. */
        fun toggle(
            current: MapSelection?,
            tapped: MapSelection?,
        ): MapSelection? = tapped.takeUnless { it == current }
    }
}
