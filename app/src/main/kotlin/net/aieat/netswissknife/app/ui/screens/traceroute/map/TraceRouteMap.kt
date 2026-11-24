package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.core.network.traceroute.GeoPlausibility
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import kotlin.math.roundToLong

/** The arc's line pattern. Each pattern has one meaning. */
enum class Stability {
    /** Two or more probes, every one answered, low jitter (solid line). */
    STABLE,

    /** A probe was lost or jitter is high (dotted line). */
    UNSTABLE,

    /**
     * Stability was not measured (dash-dot line): the arriving hop sent one probe, not all of its probes
     * have reported yet, or the trace was stopped before its missing replies could count as lost.
     */
    NOT_MEASURED,

    /**
     * The path runs through hops that did not answer, have no location, or have a disputed one. Nothing
     * about it is measured: grey dashed line, with no latency, speed or stability claim.
     */
    UNKNOWN,
}

/** Distance covered per ms of added RTT, against light in fibre; drawn as the arc's width. */
enum class SpeedBand { FAST, MEDIUM, SLOW, UNKNOWN }

/** Probe results of one hop, the same data the hop list shows. */
data class ProbeStats(
    /** Probes that have reported, answered or not. */
    val sent: Int,
    val answered: Int,
    /** [HopResult.rttMedianMs]: the hop's RTT everywhere it is shown as one figure. */
    val medianMs: Long?,
    /** Fastest-to-slowest spread of answered probes, or null when fewer than two answered. */
    val jitterMs: Long?,
) {
    val lossFraction: Double get() = if (sent == 0) 0.0 else (sent - answered).toDouble() / sent

    companion object {
        fun of(hop: HopResult): ProbeStats {
            // Results without per-probe RTTs count as a single probe.
            val probes = hop.probeRttsMs.ifEmpty { listOf(hop.rtTimeMs) }
            val answered = probes.filterNotNull()
            val jitter = if (answered.size >= 2) answered.max() - answered.min() else null
            return ProbeStats(
                sent = probes.size,
                answered = answered.size,
                medianMs = hop.rttMedianMs,
                jitterMs = jitter,
            )
        }
    }
}

/**
 * One place on the map: one hop, or consecutive located hops that GeoIP puts at the same
 * coordinates. [lon] is unwrapped so each step from the previous stop is at most 180 degrees
 * (Tokyo to Los Angeles crosses the Pacific); [location] keeps the GeoIP value.
 */
data class RouteStop(
    val hops: List<HopResult>,
    val lat: Double,
    val lon: Double,
    /** True when [GeoPlausibility] found the location contradicted by the measured RTT. */
    val disputed: Boolean,
) {
    val hopNumbers: List<Int> get() = hops.map { it.hopNumber }
    val location: HopGeoLocation get() = requireNotNull(hops.first().geoLocation)
    val isDestination: Boolean get() = hops.any { it.destinationReached }
    val label: String get() = labelOf(hopNumbers)

    /** The marker's colour: the median RTT of its first hop, as that hop's row shows it. */
    val rttMs: Long? get() = hops.first().rttMedianMs

    companion object {
        /** "7", "3-5", or "3-4, 6" for hop numbers merged across a gap. */
        fun labelOf(hopNumbers: List<Int>): String {
            val runs = runsOf(hopNumbers)
            return runs.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
        }

        /** Consecutive [hopNumbers] as ranges, in order: 3, 4, 6 gives 3..4 and 6..6. */
        fun runsOf(hopNumbers: Collection<Int>): List<IntRange> {
            val runs = mutableListOf<IntRange>()
            hopNumbers.distinct().sorted().forEach { number ->
                val last = runs.lastOrNull()
                if (last != null && number == last.last + 1) {
                    runs[runs.lastIndex] = last.first..number
                } else {
                    runs += number..number
                }
            }
            return runs
        }
    }
}

/**
 * Identifies an arc by the first hop of the stops it joins, so it keeps its identity while a running
 * trace adds hops and an arc that is drawn once is not drawn again.
 */
data class SegmentKey(
    val fromHop: Int,
    val toHop: Int,
)

/** An arc between two consecutive [TraceRouteMap.stops]. */
data class RouteSegment(
    val fromStop: Int,
    val toStop: Int,
    val key: SegmentKey,
    /** Median RTT of the arriving hop, the value its hop list row shows; sets the colour. Null for an unknown path. */
    val rttMs: Long?,
    val probes: ProbeStats,
    val distanceKm: Double,
    /** Hops between the two stops that did not answer, have no location or have a disputed one. */
    val skippedHopNumbers: List<Int>,
    /** Median RTT added since the previous stop's last answering hop; null for an unknown path. */
    val addedRttMs: Long?,
    val speedFraction: Double?,
    val speed: SpeedBand,
    val stability: Stability,
) {
    val isUnknown: Boolean get() = stability == Stability.UNKNOWN
    val skippedHops: Int get() = skippedHopNumbers.size
}

/**
 * Hops after the first located one that are not on the drawn route, each for one stated reason: [noReply]
 * sent nothing back, [notLocated] answered but have no location (yet, while the trace runs), and
 * [disputed] have a GeoIP location the measured RTT rules out, drawn as hollow markers off the route.
 */
data class OffRouteHops(
    val noReply: List<Int>,
    val notLocated: List<Int>,
    val disputed: List<Int>,
) {
    val isEmpty: Boolean get() = noReply.isEmpty() && notLocated.isEmpty() && disputed.isEmpty()

    companion object {
        val NONE = OffRouteHops(emptyList(), emptyList(), emptyList())
    }
}

/** Thresholds for the map's line width and dash pattern. */
object RouteClassification {
    /** Width: thick at or above this fraction of light in fibre. */
    const val FAST_FROM_FRACTION = 0.5

    /** Width: thin below this fraction of light in fibre. */
    const val SLOW_BELOW_FRACTION = 0.2

    /** Shorter segments get no speed: router reply time outweighs the propagation delay. */
    const val MIN_SPEED_DISTANCE_KM = 300.0

    /** The RTT must grow by at least this much from the previous stop for a speed to be computed. */
    const val MIN_SPEED_DELTA_RTT_MS = 1.0

    /** Dashes: jitter is high when it is at least this many ms ... */
    const val HIGH_JITTER_MIN_MS = 20L

    /** ... and at least this fraction of the hop's median RTT. */
    const val HIGH_JITTER_MIN_FRACTION = 0.25

    /**
     * Fraction of light in fibre for [distanceKm] covered while the round trip grew by
     * [deltaRttMs] (one way is half of it), or null when the measurement cannot support a claim.
     */
    fun speedFraction(
        distanceKm: Double,
        deltaRttMs: Double,
    ): Double? {
        if (distanceKm < MIN_SPEED_DISTANCE_KM || deltaRttMs < MIN_SPEED_DELTA_RTT_MS) return null
        return distanceKm / (deltaRttMs / 2.0) / GeoPlausibility.FIBRE_KM_PER_MS
    }

    fun speedBand(fraction: Double?): SpeedBand =
        when {
            fraction == null -> SpeedBand.UNKNOWN
            fraction >= FAST_FROM_FRACTION -> SpeedBand.FAST
            fraction >= SLOW_BELOW_FRACTION -> SpeedBand.MEDIUM
            else -> SpeedBand.SLOW
        }

    /**
     * The arc's pattern. A hop with fewer reports than [expectedProbes] is still being measured. When the
     * missing replies may have been [cutShort] by stopping the trace, they are not counted as lost; high
     * jitter among the replies that did arrive still is.
     */
    fun stability(
        probes: ProbeStats,
        skippedHops: Int,
        expectedProbes: Int = probes.sent,
        cutShort: Boolean = false,
    ): Stability {
        val jitter = probes.jitterMs
        val highJitter = jitter != null && isHighJitter(jitter, probes)
        val lost = probes.answered < probes.sent
        return when {
            skippedHops > 0 -> Stability.UNKNOWN
            probes.sent < 2 || probes.sent < expectedProbes -> Stability.NOT_MEASURED
            highJitter || (lost && !cutShort) -> Stability.UNSTABLE
            lost -> Stability.NOT_MEASURED
            else -> Stability.STABLE
        }
    }

    private fun isHighJitter(
        jitterMs: Long,
        probes: ProbeStats,
    ): Boolean {
        val relativeFloorMs = ((probes.medianMs ?: 0L) * HIGH_JITTER_MIN_FRACTION).roundToLong()
        return jitterMs >= HIGH_JITTER_MIN_MS && jitterMs >= relativeFloorMs
    }
}

/**
 * The traceroute drawn on a world map, built from the hop list.
 *
 * The device's own location is unknown (its first hops are private), so the route starts
 * at the first located hop. Hops whose GeoIP position the RTTs contradict (see
 * [GeoPlausibility.implausibleHops]) are kept out of the arc chain and listed in
 * [disputedStops]; the arc across them is an unknown path, like one across hops with no reply or
 * no location.
 */
data class TraceRouteMap(
    val stops: List<RouteStop>,
    val segments: List<RouteSegment>,
    val disputedStops: List<RouteStop>,
    /** Hops after the first located one that are not on the drawn route, by reason. */
    val offRoute: OffRouteHops = OffRouteHops.NONE,
) {
    val isEmpty: Boolean get() = stops.isEmpty() && disputedStops.isEmpty()

    /** The newest stop on the route; marked as the head of a running trace. */
    val head: RouteStop? get() = stops.lastOrNull()

    /** The line patterns the map draws, so the legend lists only those. */
    val linePatterns: Set<Stability> get() = segments.mapTo(mutableSetOf()) { it.stability }

    /** First hop drawn on the map, or null when no hop is located. */
    val firstLocatedHop: Int? get() = (stops + disputedStops).flatMap { it.hopNumbers }.minOrNull()

    /** False when no hop on the route sent two or more probes, so no arc can show stability. */
    val stabilityMeasurable: Boolean get() = stops.any { stop -> stop.hops.any { ProbeStats.of(it).sent >= 2 } }

    /** Every stop drawn, route stops first, in the index order the canvas uses. */
    val allStops: List<RouteStop> get() = stops + disputedStops

    companion object {
        private const val FULL_WORLD = 360.0

        /**
         * The map of [hops]. [stopped] marks a trace that was stopped before its end: a hop's replies
         * missing after its last answered probe may have been cut short, so they are not counted as lost.
         * A missing reply followed by an answered one is still a loss.
         */
        fun from(
            hops: List<HopResult>,
            stopped: Boolean = false,
        ): TraceRouteMap {
            val ordered = hops.sortedBy { it.hopNumber }
            val disputed = GeoPlausibility.implausibleHops(ordered)
            val stops = mutableListOf<RouteStop>()
            val disputedStops = mutableListOf<RouteStop>()
            var previousLon: Double? = null
            ordered.forEach { hop ->
                val geo = hop.geoLocation ?: return@forEach
                val lon = unwrap(geo.lon, previousLon)
                val isDisputed = hop.hopNumber in disputed
                val target = if (isDisputed) disputedStops else stops
                val last = target.lastOrNull()
                val samePlace = last?.let { it.lat == geo.lat && it.location.lon == geo.lon } == true
                val merges = last != null && samePlace && (!isDisputed || adjacent(last, stops))
                if (merges) {
                    target[target.lastIndex] = last.copy(hops = last.hops + hop)
                } else {
                    target += RouteStop(hops = listOf(hop), lat = geo.lat, lon = lon, disputed = isDisputed)
                }
                if (!isDisputed) previousLon = lon
            }
            // Results with per-probe RTTs report each probe; the most any hop has reported is the trace's setting.
            val expectedProbes = ordered.maxOfOrNull { it.probeRttsMs.size }?.coerceAtLeast(1) ?: 1
            val context = SegmentContext(ordered, expectedProbes, stopped)
            return TraceRouteMap(
                stops = stops,
                segments = segmentsOf(stops, context),
                disputedStops = disputedStops,
                offRoute = offRouteOf(stops + disputedStops, ordered, disputed),
            )
        }

        /** True when every missing reply of this hop came after its last answered one. */
        private fun HopResult.missesOnlyTrailingReplies(): Boolean {
            val upToLastReply = probeRttsMs.dropLastWhile { it == null }
            return upToLastReply.none { it == null }
        }

        private class SegmentContext(
            val ordered: List<HopResult>,
            val expectedProbes: Int,
            val stopped: Boolean,
        )

        private fun offRouteOf(
            drawn: List<RouteStop>,
            ordered: List<HopResult>,
            disputed: Set<Int>,
        ): OffRouteHops {
            val first = drawn.flatMap { it.hopNumbers }.minOrNull() ?: return OffRouteHops.NONE
            val after = ordered.filter { it.hopNumber > first }
            return OffRouteHops(
                noReply = after.filter { it.ip == null }.map { it.hopNumber },
                notLocated = after.filter { it.ip != null && it.geoLocation == null }.map { it.hopNumber },
                disputed = after.filter { it.hopNumber in disputed }.map { it.hopNumber },
            )
        }

        /**
         * Disputed hops merge only when no route stop came in between. Route stops merge across
         * disputed and unanswered hops, which are off the drawn route, so no zero-length arc appears.
         */
        private fun adjacent(
            lastDisputed: RouteStop,
            stops: List<RouteStop>,
        ): Boolean {
            val after = lastDisputed.hops.last().hopNumber
            return stops.none { stop -> stop.hops.any { it.hopNumber > after } }
        }

        private fun unwrap(
            lon: Double,
            previousLon: Double?,
        ): Double {
            if (previousLon == null) return lon
            var unwrapped = lon
            while (unwrapped - previousLon > FULL_WORLD / 2) unwrapped -= FULL_WORLD
            while (previousLon - unwrapped > FULL_WORLD / 2) unwrapped += FULL_WORLD
            return unwrapped
        }

        private fun segmentsOf(
            stops: List<RouteStop>,
            context: SegmentContext,
        ): List<RouteSegment> =
            stops.zipWithNext().mapIndexed { index, (from, to) ->
                val arriving = to.hops.first()
                val fromLast = from.hops.last().hopNumber
                val between =
                    context.ordered
                        .filter { it.hopNumber > fromLast && it.hopNumber < arriving.hopNumber }
                        .map { it.hopNumber }
                val unknown = between.isNotEmpty()
                val distanceKm = GeoPlausibility.distanceKm(from.location, to.location)
                // The hop's median on both ends, the figure each hop's row shows.
                val anchorRtt = from.hops.lastOrNull { it.rttMedianMs != null }?.rttMedianMs
                val arrivingRtt = arriving.rttMedianMs
                val addedRtt =
                    if (anchorRtt == null || arrivingRtt == null || unknown) null else arrivingRtt - anchorRtt
                val fraction = addedRtt?.let { RouteClassification.speedFraction(distanceKm, it.toDouble()) }
                val probes = ProbeStats.of(arriving)
                RouteSegment(
                    fromStop = index,
                    toStop = index + 1,
                    key = SegmentKey(fromHop = from.hops.first().hopNumber, toHop = arriving.hopNumber),
                    rttMs = arrivingRtt.takeUnless { unknown },
                    probes = probes,
                    distanceKm = distanceKm,
                    skippedHopNumbers = between,
                    addedRttMs = addedRtt,
                    speedFraction = fraction,
                    speed = RouteClassification.speedBand(fraction),
                    stability =
                        RouteClassification.stability(
                            probes = probes,
                            skippedHops = between.size,
                            expectedProbes = context.expectedProbes,
                            cutShort = context.stopped && arriving.missesOnlyTrailingReplies(),
                        ),
                )
            }
    }
}
