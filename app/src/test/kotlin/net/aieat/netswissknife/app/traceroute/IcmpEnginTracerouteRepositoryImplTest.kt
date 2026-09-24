package net.aieat.netswissknife.app.traceroute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IcmpEnginTracerouteRepositoryImplTest {

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
            5 to 2,
            5 to 20,
        )

        for ((sessionLimit, probesPerHop) in cases) {
            val session = OperationSession(
                OperationBudget.start(timeoutMillis = 5_000, maxConcurrentProbes = sessionLimit),
            )
            repository.trace(
                "192.0.2.7", 3, 100, probesPerHop, TracerouteProbeType.ICMP, 56, session,
            ).toList()
        }

        assertEquals(listOf(1, 2, 5), capturedConcurrency)
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
