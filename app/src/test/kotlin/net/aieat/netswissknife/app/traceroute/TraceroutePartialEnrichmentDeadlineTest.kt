package net.aieat.netswissknife.app.traceroute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraceroutePartialEnrichmentDeadlineTest {

    @Test
    fun `deadline during reverse DNS preserves the raw native hop exactly once`() = runTest {
        assertDeadlineDuringEnrichment(blockReverseDns = true)
    }

    @Test
    fun `deadline during GeoIP preserves the raw native hop exactly once`() = runTest {
        assertDeadlineDuringEnrichment(blockReverseDns = false)
    }

    private suspend fun TestScope.assertDeadlineDuringEnrichment(blockReverseDns: Boolean) {
        val rawHop = HopResult(1, "192.0.2.1", null, 4L, HopStatus.SUCCESS)
        val reverseDnsStarted = CompletableDeferred<Unit>()
        val geoIpStarted = CompletableDeferred<Unit>()
        var nowNanos = 0L
        val clock = MonotonicClock { nowNanos }
        val session = OperationSession(
            OperationBudget.start(
                timeoutMillis = DEADLINE_MILLIS,
                // Keep both optional lookups runnable alongside one native worker; this test
                // exercises deadline retention, while aggregate limiting has its own test.
                maxConcurrentProbes = 3,
                clock = clock,
            ),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                kotlinx.coroutines.flow.flow {
                    emit(rawHop)
                    awaitCancellation()
                }
            },
            dispatcher = dispatcher,
        )
        val reverseDns = object : TracerouteReverseDnsRepository {
            override suspend fun lookup(
                ip: String,
                operationSession: OperationSession,
            ): String? {
                reverseDnsStarted.complete(Unit)
                if (blockReverseDns) awaitCancellation()
                return "router.example"
            }
        }
        val geoIp = object : GeoIpRepository {
            override suspend fun lookup(ip: String) = null

            override suspend fun lookup(ip: String, operationSession: OperationSession): HopGeoLocation? {
                geoIpStarted.complete(Unit)
                if (!blockReverseDns) awaitCancellation()
                return null
            }
        }
        val useCase = TracerouteUseCase(repository, geoIp, reverseDns)
        val events = mutableListOf<TracerouteFlowResult>()
        val collection = async {
            runCatching {
                useCase(
                    TracerouteParams(
                        host = "192.0.2.7",
                        maxHops = 2,
                        timeoutMs = 500,
                    ),
                    session,
                ).collect(events::add)
            }
        }

        runCurrent()
        reverseDnsStarted.await()
        geoIpStarted.await()
        assertEquals(listOf(TracerouteFlowResult.Hop(rawHop)), events)

        // Drive the real OperationRunner deadline watcher with a virtual clock.
        nowNanos = DEADLINE_MILLIS * NANOS_PER_MILLISECOND
        advanceTimeBy(DEADLINE_MILLIS)
        runCurrent()
        val failure = collection.await().exceptionOrNull()

        assertTrue(
            failure is OperationDeadlineExceededException ||
                (failure is OperationCancellationException &&
                    failure.reason == CancellationReason.DEADLINE_EXCEEDED) ||
                failure is CancellationException,
            "Expected deadline cancellation, got ${failure?.javaClass?.simpleName}: ${failure?.message}",
        )
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertEquals(listOf(TracerouteFlowResult.Hop(rawHop)), events)
        assertInstanceOf(TracerouteFlowResult.Hop::class.java, events.single())
    }

    private companion object {
        const val DEADLINE_MILLIS = 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
