package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.HostResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

class PingRepositoryEngineFallbackTest {

    @Test
    fun `resolver runs once and resolved address is passed to engine`() = runTest {
        var resolves = 0
        var seenIp: String? = null
        val engine = fakeEngine(PingEngineKind.ICMP) { request ->
            seenIp = request.resolvedIp
            flowOf(PingPacketResult(1, request.host, 3, PingStatus.SUCCESS))
        }
        val repo = PingRepositoryImpl(
            engines = listOf(engine),
            resolver = HostResolver { resolves++; "192.0.2.10" }
        )

        repo.ping(PingRequest("example.com", count = 3, timeoutMs = 100)).toList()

        assertEquals(1, resolves)
        assertEquals("192.0.2.10", seenIp)
    }

    @Test
    fun `unknown host emits one error and never calls engine`() = runTest {
        var called = false
        val repo = PingRepositoryImpl(
            engines = listOf(fakeEngine(PingEngineKind.ICMP) { called = true; flowOf() }),
            resolver = HostResolver { throw UnknownHostException("missing") }
        )

        val packets = repo.ping(PingRequest("missing", count = 3, timeoutMs = 100)).toList()

        assertEquals(1, packets.size)
        assertEquals(PingStatus.ERROR, packets.single().status)
        assertTrue(!called)
    }

    @Test
    fun `engine falls back only when the first engine fails before emitting`() = runTest {
        val failing = fakeEngine(PingEngineKind.ICMP) { flow { error("unavailable") } }
        val fallback = fakeEngine(PingEngineKind.REACHABILITY) { request ->
            flowOf(PingPacketResult(1, request.host, 4, PingStatus.SUCCESS))
        }
        val repo = PingRepositoryImpl(
            engines = listOf(failing, fallback),
            resolver = HostResolver { "192.0.2.10" }
        )

        val packets = repo.ping(PingRequest("example.com", count = 1, timeoutMs = 100)).toList()

        assertEquals(PingEngineKind.REACHABILITY, repo.lastEngineUsed.value)
        assertEquals(PingStatus.SUCCESS, packets.single().status)
        assertEquals(PingEngineKind.REACHABILITY, packets.single().engine)
    }

    private fun fakeEngine(
        kind: PingEngineKind,
        block: (PingRequest) -> Flow<PingPacketResult>
    ) = object : PingEngine {
        override val kind = kind
        override val isAvailable = true
        override fun ping(request: PingRequest) = block(request)
    }
}
