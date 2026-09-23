package net.aieat.netswissknife.app.traceroute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IcmpEnginTracerouteRepositoryImplTest {

    @Test
    fun `native linkage failure becomes a controlled traceroute error`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _ ->
                flow { throw UnsatisfiedLinkError("missing JNI library") }
            },
        )

        val failure = assertThrows(NativeTracerouteUnavailableException::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 100, 1, TracerouteProbeType.ICMP, 56).toList()
            }
        }

        assertEquals("Native traceroute engine is unavailable on this device.", failure.message)
    }

    @Test
    fun `synchronous native factory linkage failure becomes a controlled traceroute error`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _ ->
                throw UnsatisfiedLinkError("constructor cannot link JNI symbol")
            },
        )

        val failure = assertThrows(NativeTracerouteUnavailableException::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 100, 1, TracerouteProbeType.ICMP, 56).toList()
            }
        }

        assertEquals("Native traceroute engine is unavailable on this device.", failure.message)
    }

    @Test
    fun `traceroute cancellation is preserved`() {
        val repository = IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _ ->
                flow { throw CancellationException("stop trace") }
            },
        )

        val failure = assertThrows(CancellationException::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 100, 1, TracerouteProbeType.ICMP, 56).toList()
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
            nativeTraceFactory = { _, _, _, _, _, _ -> flow { awaitCancellation() } },
        )
        val session = TracerouteOperation.newSession()
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
            nativeTraceFactory = { _, _, _, _, _, _ ->
                flowOf(HopResult(1, "192.0.2.1", null, 1, HopStatus.SUCCESS))
            },
        )
        val downstreamFailure = UnsatisfiedLinkError("collector failure")

        val actual = assertThrows(UnsatisfiedLinkError::class.java) {
            runBlocking {
                repository.trace("192.0.2.7", 3, 100, 1, TracerouteProbeType.ICMP, 56)
                    .collect { throw downstreamFailure }
            }
        }

        assertEquals("collector failure", actual.message)
    }
}
