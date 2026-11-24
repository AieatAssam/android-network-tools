package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.core.network.traceroute.GeoPlausibility
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import kotlin.math.roundToLong

/** How steady the arriving hop's replies were; drawn as the arc's dash pattern. */
enum class Stability {
    /** Every probe answered and jitter is low (solid line). */
    STABLE,

    /** A probe was lost or jitter is high (dashed line). */
    UNSTABLE,

    /** Only one probe was sent, so stability cannot be judged (solid line, explained by the legend). */
    NOT_MEASURED,

    /** The arc bridges hops that did not answer, have no location, or have a disputed location (dotted line). */
    INFERRED,
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

/** An arc between two consecutive [TraceRouteMap.stops]. */
data class RouteSegment(
    val fromStop: Int,
    val toStop: Int,
    /** RTT of the arriving hop, the value its hop list row shows; sets the colour. */
    val rttMs: Long?,
    val probes: ProbeStats,
    val distanceKm: Double,
    /** Hops between the two stops that did not answer, have no location or have a disputed one. */
    val skippedHops: Int,
    val speedFraction: Double?,
    val speed: SpeedBand,
    val stability: Stability,
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
            skippedHops > 0 -> Stability.INFERRED
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
 * [disputedStops]; the arc across them is drawn as inferred, with no speed claim.
 */
data class TraceRouteMap(
    val stops: List<RouteStop>,
    val segments: List<RouteSegment>,
    val disputedStops: List<RouteStop>,
) {
    val isEmpty: Boolean get() = stops.isEmpty() && disputedStops.isEmpty()

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
            return TraceRouteMap(stops, segmentsOf(stops, ordered, disputed), disputedStops)
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
            disputed: Set<Int>,
        ): List<RouteSegment> =
            stops.zipWithNext().mapIndexed { index, (from, to) ->
                val arriving = to.hops.first()
                val fromLast = from.hops.last().hopNumber
                val between = ordered.filter { it.hopNumber > fromLast && it.hopNumber < arriving.hopNumber }
                val bridgesDisputed = between.any { it.hopNumber in disputed }
                val distanceKm = GeoPlausibility.distanceKm(from.location, to.location)
                val anchorRtt = from.hops.lastOrNull { it.fastestRtt() != null }?.fastestRtt()
                val arrivingRtt = arriving.fastestRtt()
                val fraction =
                    if (anchorRtt == null || arrivingRtt == null || bridgesDisputed) {
                        null
                    } else {
                        RouteClassification.speedFraction(distanceKm, (arrivingRtt - anchorRtt).toDouble())
                    }
                val probes = ProbeStats.of(arriving)
                RouteSegment(
                    fromStop = index,
                    toStop = index + 1,
                    rttMs = arriving.rtTimeMs,
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
