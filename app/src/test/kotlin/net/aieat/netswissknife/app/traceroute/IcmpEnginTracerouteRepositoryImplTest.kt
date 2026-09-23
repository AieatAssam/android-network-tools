package net.aieat.netswissknife.app.traceroute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import org.junit.jupiter.api.Assertions.assertEquals
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

        assertTrue(failure.message!!.contains("stop trace"))
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
