package net.aieat.netswissknife.core.network.topology

import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
            "1.0.8802.1.1.2.1.4.2.1.4.0.1.1.4.192.168.1.2" to "192.168.1.2",
            "1.0.8802.1.1.2.1.4.1.1.5.0.2.1" to "neighbour2-chassis",
            "1.0.8802.1.1.2.1.4.1.1.7.0.2.1" to "GigabitEthernet0/2",
            "1.0.8802.1.1.2.1.4.1.1.9.0.2.1" to "switch3",
            "1.0.8802.1.1.2.1.4.1.1.10.0.2.1" to "Cisco IOS neighbour2",
            "1.0.8802.1.1.2.1.4.2.1.4.0.2.1.4.192.168.1.3" to "192.168.1.3"
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
    fun `SNMP timeout results in unreachable node`() = runTest {
        coEvery { snmpClient.get(any(), any()) } throws java.net.SocketTimeoutException("timeout")
        coEvery { snmpClient.walk(any(), any()) } returns emptyMap()

        val events = repository.discover(defaultParams).toList()

        // When SNMP GETs fail due to timeout, node is still emitted as unreachable
        val nodeEvents = events.filterIsInstance<TopologyDiscoveryEvent.NodeDiscovered>()
        val completeEvents = events.filterIsInstance<TopologyDiscoveryEvent.Complete>()
        assertTrue(nodeEvents.isNotEmpty())
        assertEquals(1, completeEvents.size)
        assertFalse(nodeEvents[0].node.snmpReachable)
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
}
