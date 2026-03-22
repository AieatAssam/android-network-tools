package com.example.netswissknife.core.network.lan

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("LanScanRepositoryImpl")
class LanScanRepositoryImplTest {

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
