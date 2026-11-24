package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.app.ui.screens.TraceLatencyBand

/** A marker style the legend explains. */
enum class MarkerNote {
    /** Pulsing ring on the newest located hop while the trace runs. */
    HEAD,

    /** Double ring where the destination answered. */
    DESTINATION,

    /** Hollow marker: a location the measured RTT rules out. */
    DISPUTED,
}

/** Arc widths as drawn: a segment with no speed measurement is drawn at the medium width. */
enum class ArcWidth { THICK, MEDIUM, THIN }

/**
 * What the legend lists: exactly the colours, widths, line patterns and marker styles the map draws,
 * read from the same [TraceRouteMap] the canvas draws, so the two cannot disagree.
 */
data class MapLegendContent(
    /** Latency colours of measured arcs and route markers, in band order. */
    val latencyBands: List<TraceLatencyBand>,
    /** Widths of measured arcs, thick to thin; empty when every arc is an unknown path. */
    val widths: List<ArcWidth>,
    /** Line patterns of the arcs, in [Stability] order. */
    val linePatterns: List<Stability>,
    /** True when no hop on the route was probed more than once, so no arc can show stability. */
    val singleProbeTrace: Boolean,
    val markers: List<MarkerNote>,
) {
    val isEmpty: Boolean
        get() = latencyBands.isEmpty() && widths.isEmpty() && linePatterns.isEmpty() && markers.isEmpty()

    companion object {
        /** The width [speed] is drawn at. */
        fun widthOf(speed: SpeedBand): ArcWidth =
            when (speed) {
                SpeedBand.FAST -> ArcWidth.THICK
                SpeedBand.MEDIUM, SpeedBand.UNKNOWN -> ArcWidth.MEDIUM
                SpeedBand.SLOW -> ArcWidth.THIN
            }

        fun of(
            map: TraceRouteMap,
            live: Boolean,
        ): MapLegendContent {
            val measured = map.segments.filterNot { it.isUnknown }
            val colouredRtts = measured.map { it.rttMs } + map.stops.map { it.rttMs }
            val markers =
                listOfNotNull(
                    MarkerNote.HEAD.takeIf { live && map.head != null },
                    MarkerNote.DESTINATION.takeIf { map.allStops.any { it.isDestination } },
                    MarkerNote.DISPUTED.takeIf { map.disputedStops.isNotEmpty() },
                )
            val patterns = map.segments.map { it.stability }.toSet()
            return MapLegendContent(
                latencyBands =
                    colouredRtts
                        .map { TraceLatencyBand.of(it) }
                        .filter { it != TraceLatencyBand.UNKNOWN }
                        .distinct()
                        .sorted(),
                widths = measured.map { widthOf(it.speed) }.distinct().sorted(),
                linePatterns = Stability.entries.filter { it in patterns },
                singleProbeTrace = Stability.NOT_MEASURED in patterns && !map.stabilityMeasurable,
                markers = markers,
            )
        }
    }
}
