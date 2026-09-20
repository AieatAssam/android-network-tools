package net.aieat.netswissknife.core.network.topology

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class TopologyDiscoveryRepositoryTest {

    private lateinit var snmpClient: SnmpClient
    private lateinit var repository: TopologyDiscoveryRepositoryImpl

    private val defaultParams = TopologyParams(
        targetIp = "192.168.1.1",
        snmpVersion = SnmpVersion.V2C,
        communityString = "public",
        maxHops = 3,
        timeoutMs = 1000,
        retries = 1
    )

    @BeforeEach
    fun setUp() {
        snmpClient = mockk(relaxed = true)
        repository = TopologyDiscoveryRepositoryImpl(snmpClient)
    }

    @Test
    fun `single node no LLDP or CDP neighbours emits NodeDiscovered then Complete`() = runTest {
        // System info
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco IOS Software"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "switch1"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns "Server Room"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns "36000"
        // No LLDP/CDP neighbours (empty walks for everything)
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()

        val events = repository.discover(defaultParams).toList()

        val nodeEvents = events.filterIsInstance<TopologyDiscoveryEvent.NodeDiscovered>()
        val completeEvents = events.filterIsInstance<TopologyDiscoveryEvent.Complete>()

        assertEquals(1, nodeEvents.size)
        assertEquals("192.168.1.1", nodeEvents[0].node.ip)
        assertEquals(1, completeEvents.size)
        assertEquals(1, completeEvents[0].graph.nodes.size)
        assertEquals(0, completeEvents[0].graph.links.size)
    }

    @Test
    fun `node with 2 LLDP neighbours emits NodeDiscovered and LinkDiscovered events`() = runTest {
        // Seed node system info
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco IOS"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed-switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns null
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns "100"

        // LLDP walk returns 2 neighbours for the seed; return empty for everything else
        val lldpData = mapOf(
            "1.0.8802.1.1.2.1.4.1.1.5.0.1.1" to "neighbour1-chassis",
            "1.0.8802.1.1.2.1.4.1.1.7.0.1.1" to "GigabitEthernet0/1",
            "1.0.8802.1.1.2.1.4.1.1.9.0.1.1" to "switch2",
            "1.0.8802.1.1.2.1.4.1.1.10.0.1.1" to "Cisco IOS neighbour",
            // LLDP management address is encoded in the OID; the value is ifId.
            "1.0.8802.1.1.2.1.4.2.1.4.0.1.1.1.4.192.168.1.2" to "1",
            "1.0.8802.1.1.2.1.4.1.1.5.0.2.1" to "neighbour2-chassis",
            "1.0.8802.1.1.2.1.4.1.1.7.0.2.1" to "GigabitEthernet0/2",
            "1.0.8802.1.1.2.1.4.1.1.9.0.2.1" to "switch3",
            "1.0.8802.1.1.2.1.4.1.1.10.0.2.1" to "Cisco IOS neighbour2",
            "1.0.8802.1.1.2.1.4.2.1.4.0.2.1.1.4.192.168.1.3" to "1"
        )
        // Return empty for all walks by default, LLDP data for the specific prefix
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()
        coEvery { snmpClient.walk(any(), "1.0.8802.1.1.2.1.4") } returns lldpData

        val events = repository.discover(defaultParams.copy(maxHops = 1)).toList()

        val nodeEvents = events.filterIsInstance<TopologyDiscoveryEvent.NodeDiscovered>()
        val linkEvents = events.filterIsInstance<TopologyDiscoveryEvent.LinkDiscovered>()
        val completeEvents = events.filterIsInstance<TopologyDiscoveryEvent.Complete>()

        // Seed + 2 neighbours
        assertTrue(nodeEvents.size >= 1)
        assertTrue(linkEvents.size >= 2)
        assertEquals(1, completeEvents.size)
    }

    @Test
    fun `SNMP timeout reports an actionable failure`() = runTest {
        coEvery { snmpClient.get(any(), any()) } throws java.net.SocketTimeoutException("timeout")
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()

        val events = repository.discover(defaultParams).toList()

        val errors = events.filterIsInstance<TopologyDiscoveryEvent.Error>()
        assertEquals(1, errors.size)
        assertTrue(errors.single().message.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `BFS stops at maxHops boundary`() = runTest {
        // Set maxHops to 0 - only seed node
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns null
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns "100"
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()

        val events = repository.discover(defaultParams.copy(maxHops = 0)).toList()
        val nodeEvents = events.filterIsInstance<TopologyDiscoveryEvent.NodeDiscovered>()
        // Should only have seed node
        assertEquals(1, nodeEvents.size)
    }

    @Test
    fun `client is closed after discovery completes`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()

        repository.discover(defaultParams).toList()

        verify(exactly = 1) { snmpClient.close() }
    }

    @Test
    fun `CDP hex address is linked and queued`() = runTest {
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco IOS switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed-switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns null
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns "100"
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()
        coEvery { snmpClient.walk(any(), "1.3.6.1.4.1.9.9.23.1.2.1") } returns mapOf(
            "1.3.6.1.4.1.9.9.23.1.2.1.1.3.1.1" to "1",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.4.1.1" to "c0:a8:01:03",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.6.1.1" to "edge-switch",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.7.1.1" to "Gi1/0/3"
        )
        coEvery {
            snmpClient.walk(match { it.ip == "192.168.1.3" }, "1.3.6.1.4.1.9.9.23.1.2.1")
        } returns emptyMap()

        val events = repository.discover(defaultParams.copy(maxHops = 1)).toList()
        val links = events.filterIsInstance<TopologyDiscoveryEvent.LinkDiscovered>().map { it.link }

        assertEquals(1, links.size)
        assertEquals("192.168.1.3", links.single().toIp)
        assertEquals(LinkProtocol.CDP, links.single().protocol)
        assertTrue(events.filterIsInstance<TopologyDiscoveryEvent.NodeDiscovered>().any { it.node.ip == "192.168.1.3" })
    }

    @Test
    fun `node walks run concurrently`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        val activeWalks = AtomicInteger(0)
        val maximumConcurrentWalks = AtomicInteger(0)
        coEvery { snmpClient.walk(any(), any()) } coAnswers {
            val active = activeWalks.incrementAndGet()
            maximumConcurrentWalks.updateAndGet { current -> maxOf(current, active) }
            delay(25)
            activeWalks.decrementAndGet()
            emptyMap()
        }

        repository.discover(defaultParams.copy(maxHops = 0)).toList()

        assertTrue(maximumConcurrentWalks.get() > 1)
        assertTrue(maximumConcurrentWalks.get() <= 4)
    }
}
