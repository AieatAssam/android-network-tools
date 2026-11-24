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

    /** The arriving hop sent one probe, so stability was not measured (dash-dot line). */
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
    val sent: Int,
    val answered: Int,
    /** Fastest answered probe, or null when none answered. */
    val fastestMs: Long?,
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
                fastestMs = answered.minOrNull(),
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

    companion object {
        /** "7", "3-5", or "3-4, 6" for hop numbers merged across a gap. */
        fun labelOf(hopNumbers: List<Int>): String {
            val runs = mutableListOf<IntRange>()
            hopNumbers.sorted().forEach { number ->
                val last = runs.lastOrNull()
                if (last != null && number == last.last + 1) {
                    runs[runs.lastIndex] = last.first..number
                } else {
                    runs += number..number
                }
            }
            return runs.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
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
    /** RTT of the arriving hop, the value its hop list row shows; sets the colour. Null for an unknown path. */
    val rttMs: Long?,
    val probes: ProbeStats,
    val distanceKm: Double,
    /** Hops between the two stops that did not answer, have no location or have a disputed one. */
    val skippedHops: Int,
    val speedFraction: Double?,
    val speed: SpeedBand,
    val stability: Stability,
) {
    val isUnknown: Boolean get() = stability == Stability.UNKNOWN
}

/**
 * Hops after the last stop on the route: [noReply] sent nothing back and [notLocated] answered but have
 * no location yet. A disputed hop in the range is drawn as its own marker, so it is in neither count.
 */
data class TrailingHops(
    val firstHop: Int,
    val lastHop: Int,
    val notLocated: Int,
    val noReply: Int,
)

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

    /** ... and at least this fraction of the hop's fastest probe. */
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

    fun stability(
        probes: ProbeStats,
        skippedHops: Int,
    ): Stability {
        val jitter = probes.jitterMs
        return when {
            skippedHops > 0 -> Stability.UNKNOWN
            probes.sent < 2 -> Stability.NOT_MEASURED
            probes.answered < probes.sent -> Stability.UNSTABLE
            jitter != null && isHighJitter(jitter, probes) -> Stability.UNSTABLE
            else -> Stability.STABLE
        }
    }

    private fun isHighJitter(
        jitterMs: Long,
        probes: ProbeStats,
    ): Boolean {
        val relativeFloorMs = ((probes.fastestMs ?: 0L) * HIGH_JITTER_MIN_FRACTION).roundToLong()
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
    /** Hops after the last stop, which the map cannot place yet. */
    val trailing: TrailingHops? = null,
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

    companion object {
        private const val FULL_WORLD = 360.0

        fun from(hops: List<HopResult>): TraceRouteMap {
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
                if (merges && last != null) {
                    target[target.lastIndex] = last.copy(hops = last.hops + hop)
                } else {
                    target += RouteStop(hops = listOf(hop), lat = geo.lat, lon = lon, disputed = isDisputed)
                }
                if (!isDisputed) previousLon = lon
            }
            return TraceRouteMap(
                stops = stops,
                segments = segmentsOf(stops, ordered),
                disputedStops = disputedStops,
                trailing = trailingOf(stops, ordered, disputed),
            )
        }

        private fun trailingOf(
            stops: List<RouteStop>,
            ordered: List<HopResult>,
            disputed: Set<Int>,
        ): TrailingHops? {
            val lastStopHop =
                stops
                    .lastOrNull()
                    ?.hops
                    ?.last()
                    ?.hopNumber
            val after = ordered.filter { lastStopHop != null && it.hopNumber > lastStopHop }
            if (after.isEmpty()) return null
            val placeable = after.filter { it.hopNumber !in disputed }
            return TrailingHops(
                firstHop = after.first().hopNumber,
                lastHop = after.last().hopNumber,
                notLocated = placeable.count { it.ip != null && it.geoLocation == null },
                noReply = placeable.count { it.ip == null },
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
            ordered: List<HopResult>,
        ): List<RouteSegment> =
            stops.zipWithNext().mapIndexed { index, (from, to) ->
                val arriving = to.hops.first()
                val fromLast = from.hops.last().hopNumber
                val between = ordered.filter { it.hopNumber > fromLast && it.hopNumber < arriving.hopNumber }
                val unknown = between.isNotEmpty()
                val distanceKm = GeoPlausibility.distanceKm(from.location, to.location)
                val anchorRtt = from.hops.lastOrNull { it.fastestRtt() != null }?.fastestRtt()
                val arrivingRtt = arriving.fastestRtt()
                val fraction =
                    if (anchorRtt == null || arrivingRtt == null || unknown) {
                        null
                    } else {
                        RouteClassification.speedFraction(distanceKm, (arrivingRtt - anchorRtt).toDouble())
                    }
                val probes = ProbeStats.of(arriving)
                RouteSegment(
                    fromStop = index,
                    toStop = index + 1,
                    key = SegmentKey(fromHop = from.hops.first().hopNumber, toHop = arriving.hopNumber),
                    rttMs = arriving.rtTimeMs.takeUnless { unknown },
                    probes = probes,
                    distanceKm = distanceKm,
                    skippedHops = between.size,
                    speedFraction = fraction,
                    speed = RouteClassification.speedBand(fraction),
                    stability = RouteClassification.stability(probes, between.size),
                )
            }

        /** The same RTT [GeoPlausibility] uses: fastest probe, else the reported RTT. */
        private fun HopResult.fastestRtt(): Long? = rttMinMs ?: rtTimeMs
    }
}
