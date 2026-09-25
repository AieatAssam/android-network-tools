package net.aieat.netswissknife.app.traceroute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.OperationRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import me.impa.icmpenguin.trace.Response
import me.impa.icmpenguin.trace.HopStatus as NativeHopStatus

class IcmpEnginTracerouteRepositoryImplTest {

    @Test
    fun `native hop mapping keeps all probe RTT slots and the first successful legacy RTT`() {
        val hop = mapNativeHop(
            NativeHopStatus(
                1,
                setOf("192.0.2.1"),
                listOf(Response.Success(1_500, 0), Response.Error, Response.Success(2_500, 0)),
                false,
            ),
        )

        assertEquals(listOf(1L, null, 2L), hop.probeRttsMs)
        assertEquals(1L, hop.rtTimeMs)
        assertEquals(1L, hop.rttMinMs)
        assertEquals(1.5, hop.rttAvgMs)
        assertEquals(2L, hop.rttMaxMs)
    }

    @Test
    fun `native linkage failure becomes a controlled traceroute error`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                flow { throw UnsatisfiedLinkError("missing JNI library") }
            },
        )

        val failure = assertThrows(NativeTracerouteUnavailableException::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 500, 1, TracerouteProbeType.ICMP, 56).toList()
            }
        }

        assertEquals("Native traceroute engine is unavailable on this device.", failure.message)
    }

    @Test
    fun `native tracer concurrency respects request and caller session ceiling`() = runBlocking {
        val capturedConcurrency = mutableListOf<Int>()
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, concurrency ->
                capturedConcurrency += concurrency
                flowOf()
            },
        )
        val cases = listOf(
            1 to 5,
            2 to 5,
            5 to 2,
            5 to 5,
            20 to 20,
        )

        for ((sessionLimit, probesPerHop) in cases) {
            val session = OperationSession(
                OperationBudget.start(timeoutMillis = 5_000, maxConcurrentProbes = sessionLimit),
            )
            repository.trace(
                "192.0.2.7", 3, 100, probesPerHop, TracerouteProbeType.ICMP, 56, session,
            ).toList()
        }

        assertEquals(listOf(1, 1, 2, 4, 5), capturedConcurrency)
        assertEquals(5, TracerouteOperation.MAX_CONCURRENT_PROBES)
    }

    @Test
    fun `hostname is boundedly resolved before native tracer setup`() = runBlocking {
        var nativeHost: String? = null
        var resolverHost: String? = null
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { host, _, _, _, _, _, _ ->
                nativeHost = host
                flowOf()
            },
            hostResolver = TracerouteHostResolver { host, _ ->
                resolverHost = host
                "93.184.216.34"
            },
        )

        repository.trace("example.com", 2, 500, 1, TracerouteProbeType.ICMP, 56).toList()

        assertEquals("example.com", resolverHost)
        assertEquals("93.184.216.34", nativeHost)
    }

    @Test
    fun `hostname resolution shares reverse dns production pool capacity and recovers after queued cancellation`() = runBlocking {
        supervisorScope {
            val executor = TracerouteNameResolutionWorkers.createExecutor()
            val releaseResolvers = CountDownLatch(1)
            val activeResolversEntered = CountDownLatch(REVERSE_DNS_WORKER_COUNT)
            val reverseResolverCalls = AtomicInteger()
            val reverseLookup = BoundedTracerouteReverseDnsLookup(executor) {
                reverseResolverCalls.incrementAndGet()
                activeResolversEntered.countDown()
                var released = false
                while (!released) {
                    try {
                        released = releaseResolvers.await(10, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        // Simulate platform name service work that outlives interruption.
                    }
                }
                "router.example"
            }
            val reverseCount = REVERSE_DNS_WORKER_COUNT + REVERSE_DNS_QUEUE_CAPACITY
            val reverseSessions = List(reverseCount) {
                OperationSession(OperationBudget.startUnbounded(maxConcurrentProbes = 1))
            }
            val reverseJobs = mutableListOf<kotlinx.coroutines.Deferred<String?>>()

            fun startReverseLookup(index: Int) {
                val session = reverseSessions[index]
                reverseJobs += async(Dispatchers.IO) {
                    OperationRunner.run(session) {
                        reverseLookup.lookup("192.0.2.$index", session)
                    }
                }
            }
            val hostnameResolverCalls = AtomicInteger()
            val nativeFactoryCalls = AtomicInteger()
            val repository = IcmpEnginTracerouteRepositoryImpl(
                nativeTraceFactory = { host, _, _, _, _, _, _ ->
                    nativeFactoryCalls.incrementAndGet()
                    assertEquals("198.51.100.8", host)
                    flowOf()
                },
                hostResolver = BoundedTracerouteHostResolver(executor) {
                    hostnameResolverCalls.incrementAndGet()
                    "198.51.100.8"
                },
            )
            val deniedSession = OperationSession(OperationBudget.start(timeoutMillis = 60_000))
            val cancelledQueueIndex = REVERSE_DNS_WORKER_COUNT
            var deniedTrace: kotlinx.coroutines.Deferred<List<HopResult>>? = null
            var recoveredTrace: kotlinx.coroutines.Deferred<List<HopResult>>? = null
            var hostnameSession: OperationSession? = null

            try {
                // Occupy both workers before submitting any queued reverse lookups. This makes
                // the session chosen for cancellation provably queued, independent of dispatcher
                // scheduling order.
                repeat(REVERSE_DNS_WORKER_COUNT) { startReverseLookup(it) }
                assertTrue(activeResolversEntered.await(2, TimeUnit.SECONDS))
                repeat(REVERSE_DNS_QUEUE_CAPACITY) { offset ->
                    startReverseLookup(REVERSE_DNS_WORKER_COUNT + offset)
                }
                withTimeout(2_000) {
                    while (executor.queue.size != REVERSE_DNS_QUEUE_CAPACITY) kotlinx.coroutines.yield()
                }
                assertEquals(REVERSE_DNS_WORKER_COUNT, executor.activeCount)
                assertEquals(REVERSE_DNS_QUEUE_CAPACITY, executor.queue.size)
                assertEquals(REVERSE_DNS_WORKER_COUNT, reverseResolverCalls.get())

                val deniedTraceJob = async(Dispatchers.IO) {
                    repository.trace(
                        "switch.example", 2, 500, 1, TracerouteProbeType.ICMP, 56, deniedSession,
                    ).toList()
                }
                deniedTrace = deniedTraceJob
                val rejection = runCatching {
                    withTimeout(2_000) { deniedTraceJob.await() }
                }.exceptionOrNull()
                assertInstanceOf(RejectedExecutionException::class.java, rejection)
                assertEquals(0, hostnameResolverCalls.get(), "a rejected hostname task must not enter DNS resolution")
                assertEquals(0, nativeFactoryCalls.get(), "native trace construction must wait for hostname resolution")
                assertTrue(deniedSession.resources.isClosed)
                assertEquals(REVERSE_DNS_QUEUE_CAPACITY, executor.queue.size)

                reverseSessions[cancelledQueueIndex].cancel(CancellationReason.USER_STOP)
                val cancelledLookupFailure = runCatching {
                    withTimeout(2_000) { reverseJobs[cancelledQueueIndex].await() }
                }.exceptionOrNull()
                assertInstanceOf(OperationCancellationException::class.java, cancelledLookupFailure)
                withTimeout(2_000) {
                    while (executor.queue.size != REVERSE_DNS_QUEUE_CAPACITY - 1) kotlinx.coroutines.yield()
                }

                val recoveredSession = OperationSession(OperationBudget.start(timeoutMillis = 60_000))
                hostnameSession = recoveredSession
                val recoveredTraceJob = async(Dispatchers.IO) {
                    repository.trace(
                        "switch.example", 2, 500, 1, TracerouteProbeType.ICMP, 56, recoveredSession,
                    ).toList()
                }
                recoveredTrace = recoveredTraceJob
                withTimeout(2_000) {
                    while (executor.queue.size != REVERSE_DNS_QUEUE_CAPACITY) kotlinx.coroutines.yield()
                }
                assertEquals(0, hostnameResolverCalls.get(), "the recovered task should remain queued behind active work")
                assertEquals(0, nativeFactoryCalls.get())

                releaseResolvers.countDown()
                recoveredTraceJob.await()
                reverseJobs.filterIndexed { index, _ -> index != cancelledQueueIndex }.awaitAll()

                assertEquals(1, hostnameResolverCalls.get())
                assertEquals(1, nativeFactoryCalls.get())
                assertEquals(reverseCount - 1, reverseResolverCalls.get())
                assertEquals(CancellationReason.USER_STOP, reverseSessions[cancelledQueueIndex].cancellationReason)
                assertTrue(reverseSessions.all { it.resources.isClosed })
                assertTrue(recoveredSession.resources.isClosed)
                withTimeout(2_000) {
                    while (executor.activeCount != 0 || executor.queue.isNotEmpty()) kotlinx.coroutines.yield()
                }
            } finally {
                releaseResolvers.countDown()
                deniedSession.cancel(CancellationReason.USER_STOP)
                hostnameSession?.cancel(CancellationReason.USER_STOP)
                reverseSessions.forEach { it.cancel(CancellationReason.USER_STOP) }
                reverseJobs.forEach { it.cancel() }
                reverseJobs.forEach { runCatching { it.await() } }
                deniedTrace?.cancel()
                recoveredTrace?.cancel()
                deniedTrace?.let { runCatching { it.await() } }
                recoveredTrace?.let { runCatching { it.await() } }
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `IPv4 and IPv6 literals bypass hostname lookup and preserve address support`() = runBlocking {
        val resolver = BoundedTracerouteHostResolver(resolver = { error("literal must not use DNS") })
        val session = OperationSession(OperationBudget.startUnbounded())

        assertEquals("192.0.2.1", resolver.resolve("192.0.2.1", session))
        assertEquals("2001:db8::1", resolver.resolve("[2001:db8::1]", session))
    }

    @Test
    fun `deadline cancels a blocked hostname setup without starting native tracer`() = runBlocking {
        val resolverStarted = CountDownLatch(1)
        val releaseResolver = CountDownLatch(1)
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { runnable -> Thread(runnable, "test-traceroute-resolver").apply { isDaemon = true } },
        )
        val nativeStarted = AtomicBoolean(false)
        val hostResolver = BoundedTracerouteHostResolver(executor) {
            resolverStarted.countDown()
            var released = false
            while (!released) {
                try {
                    releaseResolver.await()
                    released = true
                } catch (_: InterruptedException) {
                    // Simulate a platform name-service call that ignores cancellation.
                }
            }
            "93.184.216.34"
        }
        val session = OperationSession(OperationBudget.start(timeoutMillis = 250))
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                nativeStarted.set(true)
                flowOf()
            },
            hostResolver = hostResolver,
        )
        supervisorScope {
            val collection = async(Dispatchers.Default) {
                repository.trace(
                    "slow.example", 2, 500, 1, TracerouteProbeType.ICMP, 56, session,
                ).toList()
            }

            try {
                assertTrue(resolverStarted.await(1, TimeUnit.SECONDS))
                val failure = runCatching { withTimeout(2_000) { collection.await() } }.exceptionOrNull()

                assertInstanceOf(net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException::class.java, failure)
                assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
                assertEquals(false, nativeStarted.get())
            } finally {
                releaseResolver.countDown()
                executor.shutdownNow()
                executor.awaitTermination(1, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun `synchronous native factory linkage failure becomes a controlled traceroute error`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                throw UnsatisfiedLinkError("constructor cannot link JNI symbol")
            },
        )

        val failure = assertThrows(NativeTracerouteUnavailableException::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 500, 1, TracerouteProbeType.ICMP, 56).toList()
            }
        }

        assertEquals("Native traceroute engine is unavailable on this device.", failure.message)
    }

    @Test
    fun `traceroute cancellation is preserved`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                flow { throw CancellationException("stop trace") }
            },
        )

        val failure = assertThrows(CancellationException::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 500, 1, TracerouteProbeType.ICMP, 56).toList()
            }
        }

        var cause: Throwable? = failure
        val causes = mutableListOf<Throwable>()
        while (cause != null && causes.none { it === cause }) {
            causes += cause
            cause = cause.cause
        }
        assertTrue(causes.any { it.message.orEmpty().contains("stop trace") })
    }

    @Test
    fun `caller session cancellation stops native collection`() = runBlocking {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ -> flow { awaitCancellation() } },
        )
        val session = TracerouteOperation.newSession(3, 500)
        val collection = async {
            repository.trace(
                "192.0.2.7", 3, 100, 1, TracerouteProbeType.ICMP, 56, session,
            ).toList()
        }

        kotlinx.coroutines.delay(50)
        session.cancel(CancellationReason.USER_STOP)
        val failure = runCatching { withTimeout(1_000) { collection.await() } }.exceptionOrNull()

        assertInstanceOf(OperationCancellationException::class.java, failure)
        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
    }

    @Test
    fun `downstream linkage error propagates without traceroute remapping`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                flowOf(HopResult(1, "192.0.2.1", null, 1, HopStatus.SUCCESS))
            },
        )
        val downstreamFailure = UnsatisfiedLinkError("collector failure")

        val actual = assertThrows(UnsatisfiedLinkError::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 500, 1, TracerouteProbeType.ICMP, 56)
                    .collect { throw downstreamFailure }
            }
        }

        assertEquals("collector failure", actual.message)
    }

    @Test
    fun `native hops stream immediately with numeric hostname unset for later enrichment`() = runBlocking {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                flowOf(HopResult(1, "192.0.2.9", null, 4, HopStatus.SUCCESS))
            },
        )

        val hop = repository.trace(
            "192.0.2.1", 2, 500, 1, TracerouteProbeType.ICMP, 56,
        ).first()

        assertEquals("192.0.2.9", hop.ip)
        assertNull(hop.hostname)
    }

}
