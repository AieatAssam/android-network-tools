package net.aieat.netswissknife.app.ping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.HostResolver
import net.aieat.netswissknife.core.network.ping.PingRepositoryImpl
import net.aieat.netswissknife.core.network.ping.PingRequest
import net.aieat.netswissknife.core.network.ping.PingStatus
import net.aieat.netswissknife.core.network.ping.ReachabilityPingEngine
import net.aieat.netswissknife.core.network.ping.ReachabilityResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IcmpenguinNativeFailureTest {

    @Test
    fun `native linkage failure falls back to reachability and disables native engine`() = runTest {
        val native = IcmpenguinPingEngine {
            flow { throw UnsatisfiedLinkError("missing libicmpenguin") }
        }
        val repository = PingRepositoryImpl(
            engines = listOf(
                native,
                ReachabilityPingEngine { _, _ -> ReachabilityResult(reachable = true, rtTimeMs = 4) },
            ),
            resolver = HostResolver { "192.0.2.7" },
        )

        val packet = repository.ping(PingRequest("example.test", count = 1, timeoutMs = 100)).toList().single()

        assertEquals(PingStatus.SUCCESS, packet.status)
        assertEquals("192.0.2.7", packet.fromIp)
        assertEquals("REACHABILITY", packet.engine?.name)
        assertEquals(false, native.isAvailable)
    }

    @Test
    fun `synchronous native factory linkage failure falls back to reachability`() = runTest {
        val native = IcmpenguinPingEngine {
            throw UnsatisfiedLinkError("constructor cannot link JNI symbol")
        }
        val repository = PingRepositoryImpl(
            engines = listOf(
                native,
                ReachabilityPingEngine { _, _ -> ReachabilityResult(reachable = true, rtTimeMs = 4) },
            ),
            resolver = HostResolver { "192.0.2.7" },
        )

        val packet = repository.ping(PingRequest("example.test", count = 1, timeoutMs = 100)).toList().single()

        assertEquals(PingStatus.SUCCESS, packet.status)
        assertEquals("REACHABILITY", packet.engine?.name)
        assertEquals(false, native.isAvailable)
    }

    @Test
    fun `native adapter preserves cancellation`() {
        val native = IcmpenguinPingEngine {
            flow { throw CancellationException("stop") }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { native.ping(PingRequest("192.0.2.7", count = 1, timeoutMs = 100)).toList() }
        }
        assertTrue(native.isAvailable)
    }

    @Test
    fun `downstream linkage error propagates without disabling native engine`() {
        val native = IcmpenguinPingEngine {
            flowOf(IcmpProbe.Timeout(sequence = 1, remote = "192.0.2.7", probeSize = 56))
        }
        val downstreamFailure = UnsatisfiedLinkError("collector failure")

        val actual = assertThrows(UnsatisfiedLinkError::class.java) {
            runBlocking {
                native.ping(PingRequest("192.0.2.7", count = 1, timeoutMs = 100))
                    .collect { throw downstreamFailure }
            }
        }

        assertEquals("collector failure", actual.message)
        assertTrue(native.isAvailable)
    }
}
