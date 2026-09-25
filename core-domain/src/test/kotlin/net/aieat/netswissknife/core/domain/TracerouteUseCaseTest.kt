package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
import net.aieat.netswissknife.core.network.operation.OperationSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TracerouteUseCase")
class TracerouteUseCaseTest {

    private val tracerouteRepo = mockk<TracerouteRepository>()
    private val geoRepo        = mockk<GeoIpRepository>()
    private val useCase        = TracerouteUseCase(tracerouteRepo, geoRepo)

    // ── Validation ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validation")
    inner class Validation {

        @Test
        fun `blank host emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "   ")).toList()
            assertEquals(1, results.size)
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `invalid host emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "not a host!!")).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `maxHops below 1 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", maxHops = 0)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `maxHops above 64 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", maxHops = 65)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `timeoutMs below 500 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", timeoutMs = 100)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `timeoutMs above 30000 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", timeoutMs = 31_000)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `request exceeding interactive ceiling is rejected before repository probes`() = runTest {
            val results = useCase(
                TracerouteParams(host = "google.com", maxHops = 64, timeoutMs = 20_000),
            ).toList()

            val error = results.single() as TracerouteFlowResult.ValidationError
            assertEquals(ErrorCode.OPERATION_DEADLINE_EXCEEDED, error.info.code)
            assertEquals("Requested trace exceeds the 20-minute time limit; reduce max hops, probes per hop, or timeout", error.message)
            io.mockk.verify(exactly = 0) {
                tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `probesPerHop below 1 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", probesPerHop = 0)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `probesPerHop above 5 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", probesPerHop = 6)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `packetSize below 28 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", packetSize = 10)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `packetSize above 1472 emits ValidationError`() = runTest {
            val results = useCase(TracerouteParams(host = "google.com", packetSize = 1500)).toList()
            assertInstanceOf(TracerouteFlowResult.ValidationError::class.java, results[0])
        }

        @Test
        fun `packetSize 0 (MTU discovery) is accepted`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf()
            val results = useCase(TracerouteParams(host = "google.com", packetSize = 0)).toList()
            assertEquals(0, results.size)
        }
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        private val hop1 = HopResult(1, "192.168.1.1", null, 1L,  HopStatus.SUCCESS)
        private val hop2 = HopResult(2, "8.8.8.8",     null, 20L, HopStatus.SUCCESS)
        private val geo  = HopGeoLocation("8.8.8.8", "United States", "US", "Mountain View", 37.39, -122.08)

        @Test
        fun `valid params emit Hop results`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop1, hop2)
            coEvery { geoRepo.lookup("192.168.1.1", any()) } returns null
            coEvery { geoRepo.lookup("8.8.8.8", any())     } returns geo

            val results = useCase(TracerouteParams("google.com")).toList()
            assertEquals(4, results.size)
            assertEquals(listOf(1, 2), results.filterIsInstance<TracerouteFlowResult.Hop>().map { it.hop.hopNumber })
            assertEquals(listOf(1, 2), results.filterIsInstance<TracerouteFlowResult.HopEnriched>().map { it.hopNumber })
        }

        @Test
        fun `geo location is returned as a hop enrichment`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop2)
            coEvery { geoRepo.lookup("8.8.8.8", any()) } returns geo

            val results = useCase(TracerouteParams("google.com")).toList()
            assertEquals(TracerouteFlowResult.HopEnriched(2, null, geo), results[1])
        }

        @Test
        fun `raw hop is emitted before reverse DNS and GeoIP enrichment`() = runTest {
            val reverseDns = mockk<TracerouteReverseDnsRepository>()
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop2)
            coEvery { reverseDns.lookup("8.8.8.8", any()) } returns "dns.google"
            coEvery { geoRepo.lookup("8.8.8.8", any()) } returns geo

            val results = TracerouteUseCase(tracerouteRepo, geoRepo, reverseDns)(TracerouteParams("google.com")).toList()

            assertEquals(TracerouteFlowResult.Hop(hop2), results[0])
            assertEquals(TracerouteFlowResult.HopEnriched(2, "dns.google", geo), results[1])
        }

        @Test
        fun `ordinary enrichment lookup failures retain the raw hop and emit empty enrichment`() = runTest {
            val reverseDns = mockk<TracerouteReverseDnsRepository>()
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop2)
            coEvery { reverseDns.lookup("8.8.8.8", any()) } throws java.io.IOException("reverse lookup failed")
            coEvery { geoRepo.lookup("8.8.8.8", any()) } throws java.io.IOException("geo lookup failed")

            val results = TracerouteUseCase(tracerouteRepo, geoRepo, reverseDns)(TracerouteParams("google.com")).toList()

            assertEquals(TracerouteFlowResult.Hop(hop2), results[0])
            assertEquals(TracerouteFlowResult.HopEnriched(2, null, null), results[1])
        }

        @Test
        fun `reverse DNS lookup timeout does not drop the hop or delay GeoIP result`() = runTest {
            val reverseDns = mockk<TracerouteReverseDnsRepository>()
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop2)
            coEvery { reverseDns.lookup("8.8.8.8", any()) } coAnswers {
                delay(TracerouteOperation.MAX_REVERSE_DNS_WAIT_MILLIS + 1L)
                "late.example"
            }
            coEvery { geoRepo.lookup("8.8.8.8", any()) } returns geo

            val results = TracerouteUseCase(tracerouteRepo, geoRepo, reverseDns)(
                TracerouteParams("google.com"),
            ).toList()

            assertEquals(TracerouteFlowResult.Hop(hop2), results.first())
            assertEquals(TracerouteFlowResult.HopEnriched(2, null, geo), results.last())
        }

        @Test
        fun `at most four GeoIP enrichment lookups are in flight`() = runTest {
            val hops = (1..8).map { number ->
                HopResult(number, "192.0.2.$number", null, number.toLong(), HopStatus.SUCCESS)
            }
            val releaseLookups = CompletableDeferred<Unit>()
            var activeLookups = 0
            var maximumActiveLookups = 0
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(*hops.toTypedArray())
            coEvery { geoRepo.lookup(any(), any()) } coAnswers {
                activeLookups++
                maximumActiveLookups = maxOf(maximumActiveLookups, activeLookups)
                try {
                    releaseLookups.await()
                    null
                } finally {
                    activeLookups--
                }
            }

            val collected = mutableListOf<TracerouteFlowResult>()
            val collector = backgroundScope.launch {
                useCase(TracerouteParams("google.com")).toList(collected)
            }
            runCurrent()

            assertEquals(4, activeLookups)
            assertEquals(4, maximumActiveLookups)
            assertEquals(8, collected.filterIsInstance<TracerouteFlowResult.Hop>().size)
            assertEquals(0, collected.filterIsInstance<TracerouteFlowResult.HopEnriched>().size)

            releaseLookups.complete(Unit)
            collector.join()

            assert(maximumActiveLookups <= 4)
            assertEquals(8, collected.filterIsInstance<TracerouteFlowResult.HopEnriched>().size)
        }

        @Test
        fun `caller session concurrency budget further limits enrichment lookups`() = runTest {
            val hops = (1..6).map { number ->
                HopResult(number, "198.51.100.$number", null, number.toLong(), HopStatus.SUCCESS)
            }
            val releaseLookups = CompletableDeferred<Unit>()
            var activeLookups = 0
            var maximumActiveLookups = 0
            val session = TracerouteOperation.newSession(
                maxHops = hops.size,
                timeoutMs = 500,
                maxConcurrentProbes = 1,
            )
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(*hops.toTypedArray())
            coEvery { geoRepo.lookup(any(), any()) } coAnswers {
                activeLookups++
                maximumActiveLookups = maxOf(maximumActiveLookups, activeLookups)
                try {
                    releaseLookups.await()
                    null
                } finally {
                    activeLookups--
                }
            }

            val collected = mutableListOf<TracerouteFlowResult>()
            val collector = backgroundScope.launch {
                useCase(TracerouteParams("google.com", maxHops = hops.size), session).toList(collected)
            }
            runCurrent()

            assertEquals(1, activeLookups)
            assertEquals(1, maximumActiveLookups)
            assertEquals(hops.size, collected.filterIsInstance<TracerouteFlowResult.Hop>().size)
            assertEquals(0, collected.filterIsInstance<TracerouteFlowResult.HopEnriched>().size)

            releaseLookups.complete(Unit)
            collector.join()

            assert(maximumActiveLookups <= 1)
            assertEquals(hops.size, collected.filterIsInstance<TracerouteFlowResult.HopEnriched>().size)
        }

        @Test
        fun `one caller session is shared by repository and every hop enrichment`() = runTest {
            val session = TracerouteOperation.newSession(30, 3_000)
            val observedSessions = mutableListOf<OperationSession>()
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } answers {
                observedSessions += lastArg<OperationSession>()
                flowOf(hop1, hop2)
            }
            coEvery { geoRepo.lookup(any(), any()) } answers {
                observedSessions += secondArg<OperationSession>()
                null
            }

            useCase(TracerouteParams("google.com"), session).toList()

            assertEquals(listOf(session, session, session), observedSessions)
        }

        @Test
        fun `deadline during optional geo enrichment stops the shared trace`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop2)
            coEvery { geoRepo.lookup(any(), any()) } throws
                net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException()

            val failure = runCatching { useCase(TracerouteParams("google.com")).toList() }.exceptionOrNull()

            assertInstanceOf(
                net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException::class.java,
                failure,
            )
        }

        @Test
        fun `timeout hop with null IP gets no geo lookup`() = runTest {
            val timeoutHop = HopResult(1, null, null, null, HopStatus.TIMEOUT)
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(timeoutHop)

            val results = useCase(TracerouteParams("google.com")).toList()
            assertEquals(TracerouteFlowResult.Hop(timeoutHop), results[0])
        }

        @Test
        fun `host is trimmed before passing to repository`() = runTest {
            var capturedHost: String? = null
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } answers {
                capturedHost = firstArg()
                flow {}
            }
            coEvery { geoRepo.lookup(any(), any()) } returns null

            useCase(TracerouteParams("  google.com  ")).toList()
            assertEquals("google.com", capturedHost)
        }

        @Test
        fun `geo lookup failure on one hop does not abort the rest of the trace`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop1, hop2)
            coEvery { geoRepo.lookup("192.168.1.1", any()) } throws java.io.IOException("geo service unreachable")
            coEvery { geoRepo.lookup("8.8.8.8", any()) } returns geo

            val results = useCase(TracerouteParams("google.com")).toList()

            assertEquals(4, results.size)
            assertEquals(listOf(hop1, hop2), results.filterIsInstance<TracerouteFlowResult.Hop>().map { it.hop })
            val enrichmentEvents = results.filterIsInstance<TracerouteFlowResult.HopEnriched>()
            assertEquals(2, enrichmentEvents.size)
            assertEquals(null, enrichmentEvents.single { it.hopNumber == 1 }.geoLocation)
            assertEquals(geo, enrichmentEvents.single { it.hopNumber == 2 }.geoLocation)
            assertTrue(results.indexOf(TracerouteFlowResult.Hop(hop1)) < results.indexOf(enrichmentEvents.single { it.hopNumber == 1 }))
            assertTrue(results.indexOf(TracerouteFlowResult.Hop(hop2)) < results.indexOf(enrichmentEvents.single { it.hopNumber == 2 }))
        }

        @Test
        fun `IPv4 address is accepted as valid host`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(hop1)
            coEvery { geoRepo.lookup(any(), any()) } returns null

            val results = useCase(TracerouteParams("8.8.8.8")).toList()
            assert(results[0] is TracerouteFlowResult.Hop)
        }
    }
}
