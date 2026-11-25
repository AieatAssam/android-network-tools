package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Hosts that answer late, such as Wi-Fi clients waking from power save, within the configured timeout. */
class LanScanSlowHostTest {
    @Test
    fun `host whose first echo is lost is found by a later echo within the timeout`() =
        runTest {
            val echoes = ConcurrentHashMap<String, AtomicInteger>()
            val summary =
                scan(
                    request = LanScanRequest("192.168.1.0/30", timeoutMs = 900),
                    icmp = { ip, _ ->
                        val attempt = echoes.getOrPut(ip) { AtomicInteger() }.incrementAndGet()
                        if (ip == "192.168.1.1" && attempt > 1) 840L else null
                    },
                )

            assertEquals(listOf("192.168.1.1"), summary.hosts.map { it.ip })
            assertEquals(840L, summary.hosts.single().pingTimeMs)
            assertEquals(setOf(DiscoveryMethod.ICMP), summary.hosts.single().discoveredVia)
            assertEquals(1, summary.uncertainCount)
            assertEquals(listOf("192.168.1.2"), summary.uncertainHosts.map { it.ip })
        }

    @Test
    fun `every echo waits the configured timeout and silent hosts stay uncertain`() =
        runTest {
            val echoTimeouts = CopyOnWriteArrayList<Int>()
            val summary =
                scan(
                    request = LanScanRequest("192.168.1.0/30", timeoutMs = 2_000),
                    icmp = { _, timeoutMs ->
                        echoTimeouts += timeoutMs
                        null
                    },
                )

            assertTrue(summary.hosts.isEmpty())
            assertEquals(2, summary.uncertainCount)
            assertEquals(List(4) { 2_000 }, echoTimeouts.toList())
        }

    @Test
    fun `this device is marked from the request local address`() =
        runTest {
            val summary =
                scan(
                    request = LanScanRequest("192.168.1.0/30", localIp = "192.168.1.2"),
                    icmp = { _, _ -> 1L },
                )

            assertEquals(listOf(false, true), summary.hosts.map { it.isThisDevice })
        }

    private suspend fun scan(
        request: LanScanRequest,
        icmp: suspend (String, Int) -> Long?,
    ): LanScanSummary =
        LanScanRepositoryImpl(
            icmpProbe = IcmpProbe(icmp),
            tcpProbe = TcpPresenceProbe { _, ports, _ -> TcpPresence.TimedOut(ports.first()) },
            nameProbes = emptyList(),
            macResolver =
                object : MacResolver {
                    override val supported = false

                    override suspend fun resolve(ip: String): String? = null
                },
            portChecker = { _, _, _ -> false },
        ).scan(request)
            .filterIsInstance<LanScanUpdate.ScanComplete>()
            .first()
            .summary
}
