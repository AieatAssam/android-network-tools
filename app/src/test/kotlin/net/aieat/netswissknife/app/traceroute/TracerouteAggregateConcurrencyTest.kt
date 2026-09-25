package net.aieat.netswissknife.app.traceroute

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TracerouteAggregateConcurrencyTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `native probes and enrichment share the caller concurrency budget through cancellation`() = runTest {
        var nativeConcurrency = 0
        var activeNativeProbes = 0
        var activeGeoLookups = 0
        var activeReverseLookups = 0
        var maximumCombinedWork = 0
        val events = mutableListOf<TracerouteFlowResult>()
        val session = OperationSession(
            OperationBudget.startUnbounded(maxConcurrentProbes = SESSION_CONCURRENCY),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val nativeRepository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, concurrency ->
                nativeConcurrency = concurrency
                flow {
                    activeNativeProbes += concurrency
                    maximumCombinedWork = maxOf(
                        maximumCombinedWork,
                        activeNativeProbes + activeGeoLookups + activeReverseLookups,
                    )
                    try {
                        emit(HOP)
                        awaitCancellation()
                    } finally {
                        activeNativeProbes -= concurrency
                    }
                }
            },
            dispatcher = dispatcher,
        )
        val geoIpRepository = object : GeoIpRepository {
            override suspend fun lookup(ip: String): HopGeoLocation? {
                activeGeoLookups++
                maximumCombinedWork = maxOf(
                    maximumCombinedWork,
                    activeNativeProbes + activeGeoLookups + activeReverseLookups,
                )
                try {
                    awaitCancellation()
                } finally {
                    activeGeoLookups--
                }
            }
        }
        val reverseDnsRepository = TracerouteReverseDnsRepository { _, _ ->
            activeReverseLookups++
            maximumCombinedWork = maxOf(
                maximumCombinedWork,
                activeNativeProbes + activeGeoLookups + activeReverseLookups,
            )
            try {
                awaitCancellation()
            } finally {
                activeReverseLookups--
            }
        }
        val collector = backgroundScope.launch {
            TracerouteUseCase(nativeRepository, geoIpRepository, reverseDnsRepository)(
                TracerouteParams(
                    host = "192.0.2.7",
                    maxHops = 3,
                    timeoutMs = 500,
                    probesPerHop = 5,
                ),
                session,
            ).collect(events::add)
        }

        runCurrent()
        val enrichmentWasActiveAlongsideNative =
            activeGeoLookups + activeReverseLookups == 1 && activeNativeProbes > 0
        session.cancel(CancellationReason.USER_STOP)
        runCurrent()
        collector.join()

        assertEquals(1, nativeConcurrency, "one session slot must remain available for enrichment")
        assertTrue(enrichmentWasActiveAlongsideNative, "an enrichment lookup should overlap native probing")
        assertTrue(events.any { it == TracerouteFlowResult.Hop(HOP) }, "the raw hop must survive cancellation")
        assertTrue(events.none { it is TracerouteFlowResult.HopEnriched })
        assertEquals(0, activeNativeProbes)
        assertEquals(0, activeGeoLookups)
        assertEquals(0, activeReverseLookups)
        var allPermitsReturned = false
        withTimeout(1_000) {
            session.concurrencyLimiter.withPermits(SESSION_CONCURRENCY) {
                allPermitsReturned = true
            }
        }
        assertTrue(allPermitsReturned, "cancellation must return every shared permit")
        assertTrue(
            maximumCombinedWork <= SESSION_CONCURRENCY,
            "native probes plus both enrichment lookups exceeded the session budget: $maximumCombinedWork",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `single-slot session releases native permit before awaiting enrichment`() = runTest {
        val session = OperationSession(
            OperationBudget.startUnbounded(maxConcurrentProbes = 1),
        )
        var nativeConcurrency = 0
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, concurrency ->
                nativeConcurrency = concurrency
                flowOf(HOP)
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val geo = object : GeoIpRepository {
            override suspend fun lookup(ip: String): HopGeoLocation =
                HopGeoLocation(ip, "United States", "US", "Mountain View", 37.39, -122.08)
        }

        val results = TracerouteUseCase(repository, geo)(
            TracerouteParams("192.0.2.7", maxHops = 3, timeoutMs = 500, probesPerHop = 5),
            session,
        ).toList()

        assertEquals(1, nativeConcurrency)
        assertEquals(TracerouteFlowResult.Hop(HOP), results.first())
        assertTrue(results.any { it is TracerouteFlowResult.HopEnriched })
    }

    private companion object {
        const val SESSION_CONCURRENCY = 2
        val HOP = HopResult(1, "8.8.8.8", null, 7L, HopStatus.SUCCESS)
    }
}
