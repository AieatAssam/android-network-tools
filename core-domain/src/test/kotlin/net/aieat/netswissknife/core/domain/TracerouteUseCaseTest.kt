package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any()) } returns flowOf()
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
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any()) } returns flowOf(hop1, hop2)
            coEvery { geoRepo.lookup("192.168.1.1") } returns null
            coEvery { geoRepo.lookup("8.8.8.8")     } returns geo

            val results = useCase(TracerouteParams("google.com")).toList()
            assertEquals(2, results.size)
            assert(results.all { it is TracerouteFlowResult.Hop })
        }

        @Test
        fun `geo location is attached to hop with public IP`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any()) } returns flowOf(hop2)
            coEvery { geoRepo.lookup("8.8.8.8") } returns geo

            val results = useCase(TracerouteParams("google.com")).toList()
            val hopResult = (results[0] as TracerouteFlowResult.Hop).hop
            assertEquals(geo, hopResult.geoLocation)
        }

        @Test
        fun `timeout hop with null IP gets no geo lookup`() = runTest {
            val timeoutHop = HopResult(1, null, null, null, HopStatus.TIMEOUT)
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any()) } returns flowOf(timeoutHop)

            val results = useCase(TracerouteParams("google.com")).toList()
            val hopResult = (results[0] as TracerouteFlowResult.Hop).hop
            assertEquals(null, hopResult.geoLocation)
        }

        @Test
        fun `host is trimmed before passing to repository`() = runTest {
            var capturedHost: String? = null
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any()) } answers {
                capturedHost = firstArg()
                flow {}
            }
            coEvery { geoRepo.lookup(any()) } returns null

            useCase(TracerouteParams("  google.com  ")).toList()
            assertEquals("google.com", capturedHost)
        }

        @Test
        fun `IPv4 address is accepted as valid host`() = runTest {
            every { tracerouteRepo.trace(any(), any(), any(), any(), any(), any()) } returns flowOf(hop1)
            coEvery { geoRepo.lookup(any()) } returns null

            val results = useCase(TracerouteParams("8.8.8.8")).toList()
            assert(results[0] is TracerouteFlowResult.Hop)
        }
    }
}
