package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.core.network.traceroute.GeoPlausibility
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs

@DisplayName("TraceRouteMap")
class TraceRouteMapTest {
    private fun place(
        city: String,
        lat: Double,
        lon: Double,
    ) = HopGeoLocation(ip = "", country = "", countryCode = "", city = city, lat = lat, lon = lon)

    private val london = place("London", 51.5074, -0.1278)
    private val paris = place("Paris", 48.8566, 2.3522)
    private val manchester = place("Manchester", 53.4808, -2.2426)
    private val frankfurt = place("Frankfurt", 50.1109, 8.6821)
    private val newYork = place("New York", 40.7128, -74.0060)
    private val sydney = place("Sydney", -33.8688, 151.2093)
    private val tokyo = place("Tokyo", 35.6762, 139.6503)
    private val losAngeles = place("Los Angeles", 34.0522, -118.2437)

    private fun hop(
        number: Int,
        location: HopGeoLocation? = null,
        probes: List<Long?> = listOf(10L),
        destination: Boolean = false,
    ) = HopResult(
        hopNumber = number,
        ip = if (probes.any { it != null }) "198.51.100.$number" else null,
        hostname = null,
        rtTimeMs = probes.filterNotNull().firstOrNull(),
        status = if (probes.any { it != null }) HopStatus.SUCCESS else HopStatus.TIMEOUT,
        geoLocation = location?.copy(ip = "198.51.100.$number"),
        probeRttsMs = probes,
        destinationReached = destination,
    )

    private fun timeout(number: Int) = hop(number, probes = listOf(null))

    @Nested
    @DisplayName("stops")
    inner class Stops {
        @Test
        fun `no hops, or no located hops, give an empty map`() {
            assertTrue(TraceRouteMap.from(emptyList()).isEmpty)
            assertTrue(TraceRouteMap.from(listOf(hop(1), timeout(2))).isEmpty)
        }

        @Test
        fun `the route starts at the first located hop, not at an invented origin`() {
            val map = TraceRouteMap.from(listOf(hop(1), hop(2), hop(3, london), hop(4, paris)))

            assertEquals(listOf(3), map.stops.first().hopNumbers)
            assertEquals(3, map.firstLocatedHop)
            assertEquals(1, map.segments.size)
        }

        @Test
        fun `consecutive hops in the same place merge into one stop`() {
            val map = TraceRouteMap.from(listOf(hop(3, london), hop(4, london), hop(5, paris)))

            assertEquals(listOf(listOf(3, 4), listOf(5)), map.stops.map { it.hopNumbers })
            assertEquals("3-4", map.stops[0].label)
            assertEquals(1, map.segments.size)
        }

        @Test
        fun `a stop merges across a hop that did not answer, and draws no zero-length arc`() {
            val map = TraceRouteMap.from(listOf(hop(3, london), timeout(4), hop(5, london)))

            assertEquals(listOf(listOf(3, 5)), map.stops.map { it.hopNumbers })
            assertEquals("3, 5", map.stops[0].label)
            assertTrue(map.segments.isEmpty())
        }

        @Test
        fun `a return to an earlier place is a new stop`() {
            val map = TraceRouteMap.from(listOf(hop(3, london), hop(4, paris), hop(5, london)))

            assertEquals(3, map.stops.size)
            assertEquals(2, map.segments.size)
        }

        @Test
        fun `labels compress runs of hop numbers`() {
            assertEquals("7", RouteStop.labelOf(listOf(7)))
            assertEquals("3-5", RouteStop.labelOf(listOf(3, 4, 5)))
            assertEquals("3-4, 6", RouteStop.labelOf(listOf(3, 4, 6)))
        }

        @Test
        fun `one located stop has no arcs`() {
            val map = TraceRouteMap.from(listOf(hop(1), hop(2, frankfurt)))

            assertFalse(map.isEmpty)
            assertEquals(1, map.stops.size)
            assertTrue(map.segments.isEmpty())
        }

        @Test
        fun `the stop holding the destination reply is marked`() {
            val map = TraceRouteMap.from(listOf(hop(3, london), hop(4, paris, destination = true)))

            assertFalse(map.stops[0].isDestination)
            assertTrue(map.stops[1].isDestination)
        }
    }

    @Nested
    @DisplayName("segments")
    inner class Segments {
        @Test
        fun `a segment carries the arriving hop's median RTT, probe results and distance`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london, listOf(10L, 11L, 12L)), hop(4, newYork, listOf(80L, null, 85L))),
                )

            val segment = map.segments.single()
            assertEquals(0, segment.fromStop)
            assertEquals(1, segment.toStop)
            assertEquals(SegmentKey(fromHop = 3, toHop = 4), segment.key)
            // Median of 80 and 85, rounded half up.
            assertEquals(83L, segment.rttMs)
            assertEquals(ProbeStats(sent = 3, answered = 2, medianMs = 83L, jitterMs = 5L), segment.probes)
            assertEquals(GeoPlausibility.distanceKm(london, newYork), segment.distanceKm!!, 1e-6)
            assertEquals(0, segment.skippedHops)
            // Median 11 at London to median 83 at New York.
            assertEquals(72L, segment.addedRttMs)
            assertFalse(segment.isUnknown)
        }

        @Test
        fun `jitter needs two answered probes`() {
            assertEquals(
                ProbeStats(sent = 2, answered = 1, medianMs = 40L, jitterMs = null),
                ProbeStats.of(hop(1, probes = listOf(40L, null))),
            )
            assertEquals(
                ProbeStats(sent = 1, answered = 1, medianMs = 40L, jitterMs = null),
                ProbeStats.of(hop(1, probes = listOf(40L))),
            )
            assertEquals(0.5, ProbeStats(sent = 2, answered = 1, medianMs = 40L, jitterMs = null).lossFraction, 1e-9)
        }

        @Test
        fun `a hop without per-probe results counts as one probe`() {
            val bare = HopResult(1, "198.51.100.1", null, 12L, HopStatus.SUCCESS)

            assertEquals(ProbeStats(sent = 1, answered = 1, medianMs = 12L, jitterMs = null), ProbeStats.of(bare))
        }

        @Test
        fun `crossing the Pacific takes the short way over the antimeridian`() {
            val map = TraceRouteMap.from(listOf(hop(5, tokyo, listOf(5L)), hop(6, losAngeles, listOf(110L))))

            val from = map.stops[0]
            val to = map.stops[1]
            assertEquals(139.6503, from.lon, 1e-9)
            assertEquals(-118.2437 + 360.0, to.lon, 1e-9)
            assertTrue(abs(to.lon - from.lon) <= 180.0)
            assertEquals(-118.2437, to.location.lon, 1e-9)
        }

        @Test
        fun `going west over the antimeridian also takes the short way`() {
            val map = TraceRouteMap.from(listOf(hop(5, losAngeles, listOf(5L)), hop(6, tokyo, listOf(110L))))

            assertEquals(139.6503 - 360.0, map.stops[1].lon, 1e-9)
        }
    }

    @Nested
    @DisplayName("disputed locations")
    inner class Disputed {
        // Sydney cannot be reached 2 ms after London, so GeoPlausibility flags hop 4.
        private val hops = listOf(hop(3, london, listOf(10L)), hop(4, sydney, listOf(12L)), hop(5, paris, listOf(15L)))

        @Test
        fun `a hop whose location contradicts its RTT is a hollow stop of its own`() {
            assertEquals(setOf(4), GeoPlausibility.implausibleHops(hops))

            val map = TraceRouteMap.from(hops)

            assertEquals(listOf(listOf(3), listOf(5)), map.stops.map { it.hopNumbers })
            assertEquals(listOf(listOf(4)), map.disputedStops.map { it.hopNumbers })
            assertTrue(map.disputedStops.single().disputed)
            assertFalse(map.stops.any { it.disputed })
        }

        @Test
        fun `a disputed cluster in the middle is joined in hop order by order-only arcs`() {
            val map = TraceRouteMap.from(hops)

            // allStops: London (0), Paris (1), Sydney (2); the route runs London, Sydney, Paris.
            assertEquals(listOf(0 to 2, 2 to 1), map.segments.map { it.fromStop to it.toStop })
            map.segments.forEach { segment ->
                assertTrue(segment.joinsDisputed)
                assertEquals(listOf(4), segment.disputedHopNumbers)
                assertEquals(Stability.UNKNOWN, segment.stability)
                assertNull(segment.distanceKm)
                assertNull(segment.rttMs)
                assertNull(segment.addedRttMs)
                assertNull(segment.speedFraction)
                assertEquals(SpeedBand.UNKNOWN, segment.speed)
                assertTrue(segment.skippedHopNumbers.isEmpty())
            }
            assertTrue(map.hasDisputedJoins)
            assertTrue(map.routeLinePatterns.isEmpty())
        }

        @Test
        fun `a trailing disputed cluster is one stop joined to the last route stop`() {
            val trailing =
                listOf(
                    hop(3, london, listOf(10L)),
                    hop(4, sydney, listOf(12L)),
                    timeout(5),
                    hop(6, sydney, listOf(13L)),
                )
            assertEquals(setOf(4, 6), GeoPlausibility.implausibleHops(trailing))

            val map = TraceRouteMap.from(trailing)

            assertEquals(listOf(listOf(4, 6)), map.disputedStops.map { it.hopNumbers })
            val join = map.segments.single()
            assertEquals(0 to 1, join.fromStop to join.toStop)
            assertEquals(listOf(4, 6), join.disputedHopNumbers)
            assertNull(join.distanceKm)
            assertEquals(1, map.headStop)
            assertEquals(listOf(4, 6), map.head?.hopNumbers)
            assertEquals(join, map.arrivingSegment(1))
            assertNull(map.arrivingSegment(0))
        }

        @Test
        fun `a trusted arc after a disputed cluster is still measured from the last route stop`() {
            val route =
                listOf(
                    hop(3, london, listOf(10L)),
                    hop(4, sydney, listOf(12L)),
                    hop(5, paris, listOf(20L)),
                    hop(6, frankfurt, listOf(30L)),
                )
            val map = TraceRouteMap.from(route)

            val measured = map.segments.single { !it.joinsDisputed }
            assertEquals(listOf(listOf(3), listOf(5), listOf(6)), map.stops.map { it.hopNumbers })
            assertEquals(1 to 2, measured.fromStop to measured.toStop)
            assertEquals(10L, measured.addedRttMs)
            assertTrue(measured.distanceKm!! > 0.0)
            assertEquals(3, map.segments.size)
        }

        @Test
        fun `returning to the same place around a disputed hop keeps the visit order`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london, listOf(10L)), hop(4, sydney, listOf(12L)), hop(5, london, listOf(14L))),
                )

            assertEquals(listOf(listOf(3), listOf(5)), map.stops.map { it.hopNumbers })
            assertEquals(1, map.disputedStops.size)
            assertEquals(listOf(0 to 2, 2 to 1), map.segments.map { it.fromStop to it.toStop })
            assertTrue(map.segments.none { it.fromStop == it.toStop })
        }

        @Test
        fun `when every hop after the first is disputed they are still joined in order`() {
            val onlyDisputed =
                listOf(hop(3, london, listOf(10L)), hop(4, sydney, listOf(12L)), hop(5, tokyo, listOf(13L)))
            assertEquals(setOf(4, 5), GeoPlausibility.implausibleHops(onlyDisputed))

            val map = TraceRouteMap.from(onlyDisputed)

            assertFalse(map.isEmpty)
            assertEquals(1, map.stops.size)
            assertEquals(listOf(listOf(4), listOf(5)), map.disputedStops.map { it.hopNumbers })
            assertEquals(listOf(0 to 1, 1 to 2), map.segments.map { it.fromStop to it.toStop })
            assertTrue(map.segments.all { it.joinsDisputed && it.distanceKm == null })
            assertEquals(listOf(4, 5), map.segments.last().disputedHopNumbers)
        }

        @Test
        fun `a single route stop has no arcs and is the head`() {
            val map = TraceRouteMap.from(listOf(timeout(1), hop(2, london, listOf(10L))))

            assertEquals(1, map.allStops.size)
            assertTrue(map.segments.isEmpty())
            assertFalse(map.hasDisputedJoins)
            assertEquals(0, map.headStop)
            assertNull(map.arrivingSegment(0))
        }

        @Test
        fun `a disputed stop across the antimeridian is placed the short way from the stop before`() {
            val pacific =
                listOf(hop(3, tokyo, listOf(10L)), hop(4, losAngeles, listOf(12L)))
            assertEquals(setOf(4), GeoPlausibility.implausibleHops(pacific))

            val disputed = TraceRouteMap.from(pacific).disputedStops.single()

            assertEquals(-118.2437 + 360.0, disputed.lon, 1e-9)
        }
    }

    @Nested
    @DisplayName("stability (dash pattern)")
    inner class StabilityClass {
        private fun stabilityOf(
            probes: List<Long?>,
            skipped: Int = 0,
        ) = RouteClassification.stability(ProbeStats.of(hop(9, probes = probes)), skipped)

        @Test
        fun `every probe answered with low jitter is stable`() {
            assertEquals(Stability.STABLE, stabilityOf(listOf(40L, 42L, 45L)))
        }

        @Test
        fun `a lost probe is unstable, even when jitter cannot be measured`() {
            assertEquals(Stability.UNSTABLE, stabilityOf(listOf(40L, null, 41L)))
            assertEquals(Stability.UNSTABLE, stabilityOf(listOf(40L, null)))
        }

        @Test
        fun `high jitter is unstable only when large in absolute terms and against the RTT`() {
            // 30 ms spread on a 40 ms hop: high.
            assertEquals(Stability.UNSTABLE, stabilityOf(listOf(40L, 70L)))
            // 25 ms spread on a 200 ms hop: under a quarter of the RTT.
            assertEquals(Stability.STABLE, stabilityOf(listOf(200L, 225L)))
            // 15 ms spread on a 20 ms hop: under the absolute floor.
            assertEquals(Stability.STABLE, stabilityOf(listOf(20L, 35L)))
            // Exactly at both thresholds counts as high: 20 ms is a quarter of the 80 ms median.
            assertEquals(Stability.UNSTABLE, stabilityOf(listOf(70L, 90L)))
            // The floor is a quarter of the median, not of the fastest probe: 20 ms on a 90 ms median is low.
            assertEquals(Stability.STABLE, stabilityOf(listOf(80L, 100L)))
        }

        @Test
        fun `one probe per hop cannot show stability`() {
            assertEquals(Stability.NOT_MEASURED, stabilityOf(listOf(40L)))
        }

        @Test
        fun `skipped hops make the path unknown, whatever the probes say`() {
            assertEquals(Stability.UNKNOWN, stabilityOf(listOf(40L, 41L), skipped = 2))
            assertEquals(Stability.UNKNOWN, stabilityOf(listOf(40L), skipped = 1))
        }

        @Test
        fun `a single-probe hop on a route probed more than once is not drawn as stable`() {
            val map =
                TraceRouteMap.from(
                    listOf(
                        hop(3, london, listOf(10L, 11L)),
                        hop(4, paris, listOf(14L, 15L)),
                        hop(5, frankfurt, listOf(20L)),
                    ),
                )

            assertTrue(map.stabilityMeasurable)
            assertEquals(listOf(Stability.STABLE, Stability.NOT_MEASURED), map.segments.map { it.stability })
            assertEquals(setOf(Stability.STABLE, Stability.NOT_MEASURED), map.linePatterns)
        }

        @Test
        fun `the map knows when no hop on the route was probed more than once`() {
            assertFalse(TraceRouteMap.from(listOf(hop(3, london), hop(4, paris))).stabilityMeasurable)
            val map = TraceRouteMap.from(listOf(hop(3, london, listOf(1L, 2L)), hop(4, paris, listOf(5L, 6L))))
            assertTrue(map.stabilityMeasurable)
        }
    }

    @Nested
    @DisplayName("speed (line width)")
    inner class Speed {
        @Test
        fun `speed is the distance covered per ms of added RTT, against light in fibre`() {
            // 1000 km in 10 ms of round trip is 1000 km in 5 ms one way: 200 km/ms, all of light in fibre.
            assertEquals(1.0, RouteClassification.speedFraction(distanceKm = 1_000.0, deltaRttMs = 10.0)!!, 1e-9)
            assertEquals(0.25, RouteClassification.speedFraction(distanceKm = 500.0, deltaRttMs = 20.0)!!, 1e-9)
        }

        @Test
        fun `short hops and non-increasing RTTs have no speed`() {
            assertNull(RouteClassification.speedFraction(distanceKm = 299.0, deltaRttMs = 2.0))
            assertNull(RouteClassification.speedFraction(distanceKm = 5_000.0, deltaRttMs = 0.0))
            assertNull(RouteClassification.speedFraction(distanceKm = 5_000.0, deltaRttMs = -4.0))
            assertNull(RouteClassification.speedFraction(distanceKm = 5_000.0, deltaRttMs = 0.5))
        }

        @Test
        fun `an RTT increase within millisecond rounding has no speed`() {
            // Two whole-millisecond medians one apart may really be almost equal.
            assertNull(RouteClassification.speedFraction(distanceKm = 400.0, deltaRttMs = 1.0))
            assertNull(RouteClassification.speedFraction(distanceKm = 400.0, deltaRttMs = 4.0))
            assertEquals(0.8, RouteClassification.speedFraction(distanceKm = 400.0, deltaRttMs = 5.0)!!, 1e-9)
        }

        @Test
        fun `a figure faster than light in fibre is not a speed`() {
            assertNull(RouteClassification.speedFraction(distanceKm = 640.0, deltaRttMs = 1.0))
            assertNull(RouteClassification.speedFraction(distanceKm = 1_001.0, deltaRttMs = 10.0))
            assertEquals(1.0, RouteClassification.speedFraction(distanceKm = 1_000.0, deltaRttMs = 10.0)!!, 1e-9)
        }

        @Test
        fun `no distance and RTT increase gives more than light in fibre`() {
            val deltas = (-20..2_000).map { it / 4.0 }
            val distances = (0..400).map { it * 50.0 }
            deltas.forEach { delta ->
                distances.forEach { km ->
                    val fraction = RouteClassification.speedFraction(distanceKm = km, deltaRttMs = delta)
                    if (fraction != null) assertTrue(fraction in 0.0..1.0, "$km km over $delta ms gave $fraction")
                    if (delta < RouteClassification.MIN_SPEED_DELTA_RTT_MS) assertNull(fraction, "$km km, $delta ms")
                }
            }
        }

        @Test
        fun `an arc whose medians differ by a millisecond claims no speed and is not drawn thick`() {
            // About 640 km: 1 ms of added RTT would be over six times light in fibre.
            val hops = listOf(hop(3, london, listOf(40L)), hop(4, frankfurt, listOf(41L)))

            val map = TraceRouteMap.from(hops)

            assertTrue(map.disputedStops.isEmpty())
            val segment = map.segments.single()
            assertEquals(1L, segment.addedRttMs)
            assertNull(segment.speedFraction)
            assertEquals(SpeedBand.UNKNOWN, segment.speed)
        }

        @Test
        fun `bands split at half and a fifth of light in fibre`() {
            assertEquals(SpeedBand.FAST, RouteClassification.speedBand(1.4))
            assertEquals(SpeedBand.FAST, RouteClassification.speedBand(0.5))
            assertEquals(SpeedBand.MEDIUM, RouteClassification.speedBand(0.49))
            assertEquals(SpeedBand.MEDIUM, RouteClassification.speedBand(0.2))
            assertEquals(SpeedBand.SLOW, RouteClassification.speedBand(0.19))
            assertEquals(SpeedBand.UNKNOWN, RouteClassification.speedBand(null))
        }

        @Test
        fun `a segment's speed uses the RTT added since the previous stop`() {
            val map = TraceRouteMap.from(listOf(hop(3, london, listOf(10L)), hop(5, newYork, listOf(80L))))

            val segment = map.segments.single()
            val expected = GeoPlausibility.distanceKm(london, newYork) / 35.0 / GeoPlausibility.FIBRE_KM_PER_MS
            assertEquals(expected, segment.speedFraction!!, 1e-9)
            assertEquals(SpeedBand.FAST, segment.speed)
        }

        @Test
        fun `the previous stop's last hop is the anchor, as in the plausibility check`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london, listOf(10L)), hop(4, london, listOf(30L)), hop(5, newYork, listOf(100L))),
                )

            val expected = GeoPlausibility.distanceKm(london, newYork) / 35.0 / GeoPlausibility.FIBRE_KM_PER_MS
            assertEquals(expected, map.segments.single().speedFraction!!, 1e-9)
        }

        @Test
        fun `the median is used on both ends, the same figure as colour and the hop list`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london, listOf(14L, 10L, 12L)), hop(5, newYork, listOf(90L, 70L, 85L))),
                )

            val segment = map.segments.single()
            // Medians 12 and 85: the first probe (14, 90) and the fastest (10, 70) are both ignored.
            assertEquals(73L, segment.addedRttMs)
            // Speed uses the one-way time, half the added RTT.
            val expected = GeoPlausibility.distanceKm(london, newYork) / 36.5 / GeoPlausibility.FIBRE_KM_PER_MS
            assertEquals(expected, segment.speedFraction!!, 1e-9)
            assertEquals(85L, segment.rttMs)
            assertEquals(85L, map.stops.last().rttMs)
            assertEquals(12L, map.stops.first().rttMs)
        }

        @Test
        fun `a short metro hop has no speed claim`() {
            // About 262 km.
            val hops = listOf(hop(3, london, listOf(10L)), hop(4, manchester, listOf(30L)))
            val segment = TraceRouteMap.from(hops).segments.single()

            assertNull(segment.speedFraction)
            assertEquals(SpeedBand.UNKNOWN, segment.speed)
        }

        @Test
        fun `arcs to and from a disputed hop give no speed claim`() {
            val hops = listOf(hop(3, london, listOf(10L)), hop(4, sydney, listOf(12L)), hop(5, newYork, listOf(90L)))

            val segments = TraceRouteMap.from(hops).segments

            assertEquals(2, segments.size)
            assertTrue(segments.all { it.speed == SpeedBand.UNKNOWN && it.speedFraction == null })
        }
    }

    @Nested
    @DisplayName("hops with unknown information")
    inner class UnknownHops {
        @Test
        fun `unknown hops at the start are left out, the route starts at the first located hop`() {
            val map = TraceRouteMap.from(listOf(timeout(1), hop(2), hop(3, london), hop(4, paris)))

            assertEquals(3, map.firstLocatedHop)
            assertFalse(map.segments.single().isUnknown)
            assertTrue(map.offRoute.isEmpty)
        }

        @Test
        fun `unknown hops in the middle are bridged by a dashed path with no latency, speed or stability claim`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london, listOf(10L, 11L)), timeout(4), hop(5), hop(6, newYork, listOf(80L, 81L))),
                )

            val bridge = map.segments.single()
            assertTrue(bridge.isUnknown)
            assertEquals(Stability.UNKNOWN, bridge.stability)
            assertEquals(2, bridge.skippedHops)
            assertEquals(listOf(4, 5), bridge.skippedHopNumbers)
            assertNull(bridge.rttMs)
            assertNull(bridge.addedRttMs)
            assertNull(bridge.speedFraction)
            assertEquals(SpeedBand.UNKNOWN, bridge.speed)
            assertEquals(SegmentKey(fromHop = 3, toHop = 6), bridge.key)
            // The stop itself still carries its measured median RTT, 80.5 rounded half up.
            assertEquals(81L, map.stops.last().rttMs)
            val expected = OffRouteHops(noReply = listOf(4), notLocated = listOf(5), disputed = emptyList())
            assertEquals(expected, map.offRoute)
        }

        @Test
        fun `unknown hops at the end are listed by reason, not drawn`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london), hop(4, paris), hop(5), hop(6), timeout(7), timeout(8)),
                )

            assertEquals(1, map.segments.size)
            val expected = OffRouteHops(noReply = listOf(7, 8), notLocated = listOf(5, 6), disputed = emptyList())
            assertEquals(expected, map.offRoute)
        }

        @Test
        fun `a disputed hop is listed as disputed, never as located or unlocated`() {
            val map = TraceRouteMap.from(listOf(hop(3, london, listOf(10L)), hop(4, sydney, listOf(12L)), timeout(5)))

            val expected = OffRouteHops(noReply = listOf(5), notLocated = emptyList(), disputed = listOf(4))
            assertEquals(expected, map.offRoute)
        }

        @Test
        fun `the off-route facts of a long silent tail count every hop after a disputed one`() {
            // Hops 13 and 14 are disputed, and 15 to 30 sent no reply: none of them follow the last route stop.
            val hops =
                listOf(hop(11, london, listOf(10L)), hop(12, paris, listOf(14L))) +
                    listOf(hop(13, sydney, listOf(15L)), hop(14, tokyo, listOf(16L))) +
                    (15..30).map { timeout(it) }

            val map = TraceRouteMap.from(hops)

            assertEquals((15..30).toList(), map.offRoute.noReply)
            assertEquals(listOf(13, 14), map.offRoute.disputed)
            assertTrue(map.offRoute.notLocated.isEmpty())
        }

        @Test
        fun `when no hop is located the map is empty and draws nothing`() {
            val map = TraceRouteMap.from(listOf(hop(1), timeout(2), hop(3)))

            assertTrue(map.isEmpty)
            assertTrue(map.segments.isEmpty())
            assertNull(map.head)
            assertTrue(map.offRoute.isEmpty)
        }

        @Test
        fun `a single located hop is the head of the route, with the hops after it listed`() {
            val map = TraceRouteMap.from(listOf(hop(1), hop(2, frankfurt), hop(3)))

            assertEquals(listOf(2), map.head?.hopNumbers)
            assertTrue(map.segments.isEmpty())
            assertEquals(listOf(3), map.offRoute.notLocated)
        }

        @Test
        fun `the legend patterns list only what the map draws`() {
            val map =
                TraceRouteMap.from(
                    listOf(hop(3, london, listOf(10L, null)), timeout(4), hop(5, newYork, listOf(80L, 81L))),
                )

            assertEquals(setOf(Stability.UNKNOWN), map.linePatterns)
            assertTrue(TraceRouteMap.from(emptyList()).linePatterns.isEmpty())
        }
    }

    @Nested
    @DisplayName("live updates")
    inner class LiveUpdates {
        private val route =
            listOf(
                hop(1),
                hop(2, london, listOf(5L, 6L)),
                hop(3, paris, listOf(9L, 10L)),
                hop(4, frankfurt, listOf(15L, 16L)),
                hop(5, newYork, listOf(85L, 86L), destination = true),
            )

        @Test
        fun `each new hop adds a segment and keeps the keys of the segments already drawn`() {
            val keysPerStep = (1..route.size).map { n -> TraceRouteMap.from(route.take(n)).segments.map { it.key } }

            keysPerStep.zipWithNext().forEach { (before, after) ->
                assertEquals(before, after.take(before.size))
            }
            assertEquals(
                listOf(SegmentKey(2, 3), SegmentKey(3, 4), SegmentKey(4, 5)),
                keysPerStep.last(),
            )
        }

        @Test
        fun `the head is the newest located stop`() {
            assertEquals(listOf(3), TraceRouteMap.from(route.take(3)).head?.hopNumbers)
            assertEquals(listOf(5), TraceRouteMap.from(route).head?.hopNumbers)
        }

        @Test
        fun `a hop whose location has not arrived yet is listed as not located`() {
            val pending = route.take(3) + route[3].copy(geoLocation = null)

            val map = TraceRouteMap.from(pending)

            assertEquals(listOf(SegmentKey(2, 3)), map.segments.map { it.key })
            val expected = OffRouteHops(noReply = emptyList(), notLocated = listOf(4), disputed = emptyList())
            assertEquals(expected, map.offRoute)
        }

        @Test
        fun `a late location replaces the unknown bridge with measured segments`() {
            val pending = route.take(3) + route[3].copy(geoLocation = null) + route[4]
            val before = TraceRouteMap.from(pending)
            assertEquals(listOf(SegmentKey(2, 3), SegmentKey(3, 5)), before.segments.map { it.key })
            assertTrue(before.segments.last().isUnknown)

            val after = TraceRouteMap.from(route)

            assertEquals(listOf(SegmentKey(2, 3), SegmentKey(3, 4), SegmentKey(4, 5)), after.segments.map { it.key })
            assertFalse(after.segments.any { it.isUnknown })
            assertTrue(after.offRoute.isEmpty)
        }

        @Test
        fun `a hop that joins an existing stop keeps that stop's segment key`() {
            val sameCity = listOf(hop(2, london, listOf(5L, 6L)), hop(3, paris, listOf(9L, 10L)))
            val grown = sameCity + hop(4, paris, listOf(11L, 12L))

            assertEquals(
                TraceRouteMap.from(sameCity).segments.map { it.key },
                TraceRouteMap.from(grown).segments.map { it.key },
            )
        }
    }
}
