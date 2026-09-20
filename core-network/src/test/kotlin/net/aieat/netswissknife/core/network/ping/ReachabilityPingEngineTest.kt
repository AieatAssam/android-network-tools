package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReachabilityPingEngineTest {

    @Test
    fun `reachable result preserves measured time`() = runTest {
        val engine = ReachabilityPingEngine { _, _ -> ReachabilityResult(true, 12) }
        val packet = engine.ping(PingRequest("host", "192.0.2.1", 1, 1_000)).toList().single()
        assertEquals(PingStatus.SUCCESS, packet.status)
        assertEquals(12, packet.rtTimeMs)
    }

    @Test
    fun `fast negative result is unreachable and late negative result is timeout`() = runTest {
        val fast = ReachabilityPingEngine { _, _ -> ReachabilityResult(false, 10) }
            .ping(PingRequest("host", "192.0.2.1", 1, 1_000)).toList().single()
        val late = ReachabilityPingEngine { _, _ -> ReachabilityResult(false, 950) }
            .ping(PingRequest("host", "192.0.2.1", 1, 1_000)).toList().single()
        assertEquals(PingStatus.UNREACHABLE, fast.status)
        assertEquals(PingStatus.TIMEOUT, late.status)
    }

    @Test
    fun `checker exception becomes error`() = runTest {
        val packet = ReachabilityPingEngine { _, _ -> error("socket failed") }
            .ping(PingRequest("host", "192.0.2.1", 1, 1_000)).toList().single()
        assertEquals(PingStatus.ERROR, packet.status)
        assertEquals("socket failed", packet.errorMessage)
    }

    @Test
    fun `zero count continuously emits sequence numbers`() = runTest {
        val packets = ReachabilityPingEngine { _, _ -> ReachabilityResult(true, 1) }
            .ping(PingRequest("host", "192.0.2.1", 0, 1_000, intervalMs = 100))
            .take(5)
            .toList()
        assertEquals(listOf(1, 2, 3, 4, 5), packets.map { it.sequence })
    }
}
