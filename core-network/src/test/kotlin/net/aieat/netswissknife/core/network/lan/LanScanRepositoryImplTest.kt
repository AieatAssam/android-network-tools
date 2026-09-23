package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.Socket
import java.net.SocketAddress

@DisplayName("LanScanRepositoryImpl")
class LanScanRepositoryImplTest {

    @Test
    fun `default TCP presence and enrichment probes bind local sockets before connect`() = runTest {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        val connectedAfterBind = mutableListOf<Boolean>()
        val repo = LanScanRepositoryImpl(
            hostChecker = null,
            arpTableReader = emptyArpReader,
            portChecker = null,
            icmpProbe = IcmpProbe { _, _ -> null },
            macResolver = object : MacResolver {
                override val supported = false
                override suspend fun resolve(ip: String): String? = null
            },
            binder = binder,
            socketFactory = {
                object : Socket() {
                    override fun connect(endpoint: SocketAddress?, timeout: Int) {
                        connectedAfterBind += binder.boundTcpSockets.lastOrNull() === this
                    }
                }
            }
        )

        val complete = repo.scan(
            LanScanRequest(
                subnet = subnet24,
                timeoutMs = 100,
                concurrency = 1,
                presencePorts = listOf(80),
                enableNameProbes = false
            )
        ).filterIsInstance<LanScanUpdate.ScanComplete>().first()

        assertEquals(2, complete.summary.aliveHosts)
        assertTrue(connectedAfterBind.isNotEmpty())
        assertTrue(connectedAfterBind.all { it })
        assertEquals(connectedAfterBind.size, binder.boundTcpSockets.size)
        assertTrue(binder.tcpSocketBoundStatesAtBind.all { !it })
    }

    /** Only 192.168.1.1 is "alive". */
    private val aliveIp = "192.168.1.1"
    private val subnet24 = "192.168.1.0/30" // 2 hosts: .1 and .2

    private val singleAliveChecker: HostChecker = { ip, _ ->
        if (ip == aliveIp) 5L else null
    }

    private val allDeadChecker: HostChecker = { _, _ -> null }
    private val allAliveChecker: HostChecker = { _, _ -> 10L }

    private val emptyArpReader: ArpTableReader = { "" }
    private val noOpenPortsChecker: PortChecker = { _, _, _ -> false }

    private fun makeRepo(
        hostChecker: HostChecker = singleAliveChecker,
        arpReader: ArpTableReader = emptyArpReader,
        portChecker: PortChecker = noOpenPortsChecker
    ) = LanScanRepositoryImpl(
        hostChecker = hostChecker,
        arpTableReader = arpReader,
        portChecker = portChecker
    )

    @Nested
    @DisplayName("host discovery")
    inner class HostDiscovery {

        @Test
        fun `alive host emits HostFound`() = runTest {
            val results = makeRepo().scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .toList()
            assertEquals(1, results.size)
            assertEquals(aliveIp, results.first().host.ip)
        }

        @Test
        fun `dead host does not emit HostFound`() = runTest {
            val results = makeRepo().scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .toList()
            assertTrue(results.none { it.host.ip == "192.168.1.2" })
        }

        @Test
        fun `ScanComplete is always emitted last`() = runTest {
            val results = makeRepo().scan(subnet24, 1000, 10).toList()
            assertTrue(results.last() is LanScanUpdate.ScanComplete)
        }

        @Test
        fun `summary alive count matches discovered hosts`() = runTest {
            val summary = makeRepo().scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary
            assertEquals(1, summary.aliveHosts)
        }

        @Test
        fun `summary total scanned equals ip count`() = runTest {
            val summary = makeRepo().scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary
            // /30 has 2 host IPs
            assertEquals(2, summary.totalScanned)
        }

        @Test
        fun `all-dead subnet emits ScanComplete with zero alive`() = runTest {
            val summary = makeRepo(hostChecker = allDeadChecker)
                .scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary
            assertEquals(0, summary.aliveHosts)
        }

        @Test
        fun `all-alive subnet reports correct count`() = runTest {
            val summary = makeRepo(hostChecker = allAliveChecker)
                .scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary
            assertEquals(2, summary.aliveHosts)
        }

        @Test
        fun `summary subnet field matches input`() = runTest {
            val summary = makeRepo().scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary
            assertEquals(subnet24, summary.subnet)
        }

        @Test
        fun `host ping time is populated from checker`() = runTest {
            val repo = makeRepo(hostChecker = { ip, _ -> if (ip == aliveIp) 42L else null })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertEquals(42L, host.pingTimeMs)
        }
    }

    @Nested
    @DisplayName("ARP / MAC address")
    inner class ArpIntegration {

        @Test
        fun `MAC address is populated from ARP table`() = runTest {
            val arpContent = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   aa:bb:cc:dd:ee:ff     *        wlan0
            """.trimIndent()
            val repo = makeRepo(arpReader = { arpContent })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertEquals("AA:BB:CC:DD:EE:FF", host.macAddress)
        }

        @Test
        fun `zero MAC is ignored`() = runTest {
            val arpContent = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   00:00:00:00:00:00     *        wlan0
            """.trimIndent()
            val repo = makeRepo(arpReader = { arpContent })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertTrue(host.macAddress == null)
        }

        @Test
        fun `vendor is resolved from known OUI`() = runTest {
            val arpContent = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   B8:27:EB:12:34:56     *        wlan0
            """.trimIndent()
            val repo = makeRepo(arpReader = { arpContent })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertEquals("Raspberry Pi Foundation", host.vendor)
        }

        @Test
        fun `unknown OUI yields null vendor`() = runTest {
            val arpContent = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   ZZ:ZZ:ZZ:12:34:56     *        wlan0
            """.trimIndent()
            val repo = makeRepo(arpReader = { arpContent })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertTrue(host.vendor == null)
        }

        @Test
        fun `final ARP snapshot enriches staggered hosts with bounded reads`() = runTest {
            val initialTable = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   aa:bb:cc:dd:ee:01     *        wlan0
            """.trimIndent()
            val completedTable = initialTable + """

                192.168.1.2      0x1     0x2   aa:bb:cc:dd:ee:02     *        wlan0
            """.trimIndent()
            val firstProbeRead = CountDownLatch(1)
            var arpReads = 0
            val repo = LanScanRepositoryImpl(
                hostChecker = { ip, _ ->
                    if (ip == "192.168.1.2") {
                        assertTrue(firstProbeRead.await(10, TimeUnit.SECONDS))
                    }
                    5L
                },
                arpTableReader = {
                    arpReads++
                    if (arpReads == 1) {
                        firstProbeRead.countDown()
                        initialTable
                    } else {
                        completedTable
                    }
                },
                portChecker = noOpenPortsChecker,
            )

            val summary = repo.scan(subnet24, 1000, concurrency = 2)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary

            assertEquals(
                mapOf(
                    "192.168.1.1" to "AA:BB:CC:DD:EE:01",
                    "192.168.1.2" to "AA:BB:CC:DD:EE:02",
                ),
                summary.hosts.associate { it.ip to it.macAddress },
            )
            assertTrue(summary.macResolutionSupported)
            assertEquals(2, arpReads)
        }

        @Test
        fun `fresh snapshot replaces a MAC retained from an earlier scan`() = runTest {
            fun table(mac: String) = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   $mac     *        wlan0
            """.trimIndent()
            val snapshots = listOf(
                table("B8:27:EB:12:34:56"), // resolver's long-lived worker cache
                table("B8:27:EB:12:34:56"), // first scan's final snapshot
                table("3C:5A:B4:12:34:56"), // second scan observes a changed mapping
            )
            var reads = 0
            val repo = makeRepo(
                hostChecker = allAliveChecker,
                arpReader = { snapshots[reads++] },
            )
            val request = LanScanRequest(subnet24)

            suspend fun scanOnce() = repo.scan(request)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary

            assertEquals("B8:27:EB:12:34:56", scanOnce().hosts.first().macAddress)
            val second = scanOnce()

            assertEquals("3C:5A:B4:12:34:56", second.hosts.first().macAddress)
            assertEquals(3, reads)
            assertTrue(second.macResolutionSupported)
        }

        @Test
        fun `failed fresh read retains worker MAC and source support`() = runTest {
            val initialTable = """
                IP address       HW type Flags HW address            Mask     Device
                192.168.1.1      0x1     0x2   B8:27:EB:12:34:56     *        wlan0
            """.trimIndent()
            var reads = 0
            val repo = makeRepo(
                hostChecker = singleAliveChecker,
                arpReader = {
                    if (reads++ == 0) initialTable else error("/proc/net/arp unavailable")
                },
            )

            val summary = repo.scan(subnet24, 1000, concurrency = 2)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary

            assertEquals("B8:27:EB:12:34:56", summary.hosts.single().macAddress)
            assertTrue(summary.macResolutionSupported)
            assertEquals(2, reads)
        }

        @Test
        fun `final lookup failure retains worker MAC and completes scan`() = runTest {
            var calls = 0
            val resolver = object : MacResolver {
                override val supported = true
                override suspend fun resolve(ip: String): String? {
                    calls++
                    return if (calls == 1) "AA:BB:CC:DD:EE:FF" else error("lookup failed")
                }
            }
            val repo = LanScanRepositoryImpl(
                hostChecker = singleAliveChecker,
                macResolver = resolver,
                portChecker = noOpenPortsChecker,
            )

            val summary = repo.scan(subnet24, 1000, concurrency = 2)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary

            assertEquals("AA:BB:CC:DD:EE:FF", summary.hosts.single().macAddress)
            assertTrue(summary.macResolutionSupported)
        }

        @Test
        fun `snapshot creation failure retains worker MAC and completes scan`() = runTest {
            val resolver = object : MacResolver {
                override val supported = true
                override suspend fun resolve(ip: String): String? = "AA:BB:CC:DD:EE:FF"
                override fun snapshot(): MacResolver = error("snapshot failed")
            }
            val repo = LanScanRepositoryImpl(
                hostChecker = singleAliveChecker,
                macResolver = resolver,
                portChecker = noOpenPortsChecker,
            )

            val summary = repo.scan(subnet24, 1000, concurrency = 2)
                .filterIsInstance<LanScanUpdate.ScanComplete>()
                .first()
                .summary

            assertEquals("AA:BB:CC:DD:EE:FF", summary.hosts.single().macAddress)
            assertTrue(summary.macResolutionSupported)
        }
    }

    @Nested
    @DisplayName("open port detection")
    inner class PortDetection {

        @Test
        fun `open ports are listed in host`() = runTest {
            val repo = makeRepo(portChecker = { _, port, _ -> port == 80 || port == 443 })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertTrue(host.openPorts.contains(80))
            assertTrue(host.openPorts.contains(443))
        }

        @Test
        fun `closed ports are not listed`() = runTest {
            val repo = makeRepo(portChecker = { _, _, _ -> false })
            val host = repo.scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .first()
                .host
            assertTrue(host.openPorts.isEmpty())
        }
    }

    @Nested
    @DisplayName("progress tracking")
    inner class ProgressTracking {

        @Test
        fun `emits hosts in completion order`() = runTest {
            // .1 is enumerated first but is held until .2 has actually been
            // emitted. A latch rather than a sleep keeps this deterministic on a
            // loaded CI runner, while still blocking the way a real probe does.
            val fastHostEmitted = CountDownLatch(1)
            val checker: HostChecker = { ip, _ ->
                if (ip == "192.168.1.1") {
                    assertTrue(
                        fastHostEmitted.await(10, TimeUnit.SECONDS),
                        "192.168.1.2 was never emitted"
                    )
                }
                5L
            }

            val events = makeRepo(hostChecker = checker)
                .scan(subnet24, 1000, concurrency = 2)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .onEach { if (it.host.ip == "192.168.1.2") fastHostEmitted.countDown() }
                .toList()

            assertEquals(listOf("192.168.1.2", "192.168.1.1"), events.map { it.host.ip })
            assertEquals(listOf(1, 2), events.map { it.scannedCount })
        }

        @Test
        fun `scannedCount in HostFound equals number of IPs scanned so far`() = runTest {
            val events = makeRepo(hostChecker = allAliveChecker)
                .scan(subnet24, 1000, 10)
                .filterIsInstance<LanScanUpdate.HostFound>()
                .toList()
            // Both IPs are alive; each HostFound should have increasing scannedCount
            assertTrue(events.all { it.scannedCount > 0 })
            assertTrue(events.all { it.totalCount == 2 })
        }
    }
}
