package net.aieat.netswissknife.core.network.topology

import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.containsLocalNetworkPermissionDenied
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

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
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())

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
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())
        coEvery { snmpClient.walk(any(), "1.0.8802.1.1.2.1.4", any()) } returns SnmpWalkResult(lldpData)

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
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())

        val events = repository.discover(defaultParams).toList()

        val errors = events.filterIsInstance<TopologyDiscoveryEvent.Error>()
        assertEquals(1, errors.size)
        assertTrue(errors.single().message.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `permission denial during seed system query is preserved in error event`() = runTest {
        val denial = LocalNetworkPermissionDeniedException(SecurityException("local network restricted"))
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } throws IllegalStateException("SNMP request failed", denial)

        val events = repository.discover(defaultParams).toList()

        val error = events.filterIsInstance<TopologyDiscoveryEvent.Error>().single()
        assertTrue(error.cause.containsLocalNetworkPermissionDenied())
        assertTrue(error.message.contains("permission", ignoreCase = true) || error.message.contains("SNMP", ignoreCase = true))
    }

    @Test
    fun `permission denial during a walk is not swallowed as an empty topology`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        val denial = LocalNetworkPermissionDeniedException(SecurityException("local network restricted"))
        coEvery { snmpClient.walk(any(), any(), any()) } throws denial

        val events = repository.discover(defaultParams).toList()

        val error = events.filterIsInstance<TopologyDiscoveryEvent.Error>().single()
        assertSame(denial, error.cause)
        assertTrue(events.none { it is TopologyDiscoveryEvent.Complete })
    }

    @Test
    fun `failed table walk is typed as incomplete while successful topology is retained`() = runTest {
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco IOS switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed-switch"
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())
        coEvery {
            snmpClient.walk(any(), "1.3.6.1.2.1.2.2.1.2", any())
        } throws java.net.SocketTimeoutException("interface names timed out")
        coEvery {
            snmpClient.walk(any(), "1.3.6.1.2.1.2.2.1.5", any())
        } returns SnmpWalkResult(
            entries = mapOf("1.3.6.1.2.1.2.2.1.5.1" to "10000000"),
            hadError = true
        )
        coEvery {
            snmpClient.walk(any(), "1.3.6.1.4.1.9.9.23.1.2.1", any())
        } returns SnmpWalkResult(mapOf(
            "1.3.6.1.4.1.9.9.23.1.2.1.1.3.1.1" to "1",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.4.1.1" to "c0:a8:01:03",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.6.1.1" to "edge-switch",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.7.1.1" to "Gi1/0/3"
        ))

        val events = repository.discover(defaultParams.copy(maxHops = 0)).toList()
        val graph = events.filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph
        val node = graph.nodes.single()
        val interfaceObservation = node.tableObservations.getValue(TopologyDataTable.INTERFACES)

        assertTrue(node.interfaces.isEmpty())
        assertEquals(TopologyTableCompleteness.PARTIAL, interfaceObservation.completeness)
        assertTrue(TopologyTableFailure.TIMEOUT in interfaceObservation.failures)
        assertTrue(TopologyTableFailure.SNMP_RESPONSE in interfaceObservation.failures)
        assertEquals(1, graph.links.size)
        assertEquals("192.168.1.3", graph.links.single().toIp)
        assertEquals(
            TopologyTableCompleteness.COMPLETE,
            node.tableObservations.getValue(TopologyDataTable.CDP_NEIGHBORS).completeness
        )
        assertTrue(graph.hadSnmpErrors)
    }

    @Test
    fun `BFS stops at maxHops boundary`() = runTest {
        // Set maxHops to 0 - only seed node
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns null
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns "100"
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())

        val events = repository.discover(defaultParams.copy(maxHops = 0)).toList()
        val nodeEvents = events.filterIsInstance<TopologyDiscoveryEvent.NodeDiscovered>()
        // Should only have seed node
        assertEquals(1, nodeEvents.size)
    }

    @Test
    fun `client is closed after discovery completes`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())

        repository.discover(defaultParams).toList()

        verify(exactly = 1) { snmpClient.close() }
    }

    @Test
    fun `caller stop closes in-flight SNMP client once and never emits Complete`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        coEvery { snmpClient.get(any(), any()) } coAnswers {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        val session = OperationSession(
            OperationBudget.start(requirement = OperationRequirement.LOCAL_NETWORK)
        )
        val events = mutableListOf<TopologyDiscoveryEvent>()
        val collector = launch { repository.discover(defaultParams, session).collect(events::add) }
        requestStarted.await()

        session.cancel(CancellationReason.USER_STOP)
        collector.join()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(events.none { it is TopologyDiscoveryEvent.Complete })
        verify(exactly = 1) { snmpClient.close() }
    }

    @Test
    fun `deadline reports timeout and closes client without Complete`() = runTest {
        val nowNanos = java.util.concurrent.atomic.AtomicLong(0L)
        val clock = MonotonicClock { nowNanos.get() }
        val requestStarted = CompletableDeferred<Unit>()
        val clientClosed = CountDownLatch(1)
        coEvery { snmpClient.get(any(), any()) } coAnswers {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        every { snmpClient.close() } answers { clientClosed.countDown() }
        val session = OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                timeoutMillis = 1,
                clock = clock,
            )
        )
        val events = mutableListOf<TopologyDiscoveryEvent>()
        val collector = launch { repository.discover(defaultParams, session).collect(events::add) }
        requestStarted.await()

        nowNanos.set(1_000_000L)
        assertTrue(withContext(Dispatchers.IO) { clientClosed.await(2, TimeUnit.SECONDS) })
        collector.join()

        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertTrue(events.any { it is TopologyDiscoveryEvent.TimeLimit })
        assertTrue(events.none { it is TopologyDiscoveryEvent.Error })
        assertTrue(events.none { it is TopologyDiscoveryEvent.Complete })
        verify(exactly = 1) { snmpClient.close() }
    }

    @Test
    fun `request above topology budget ceiling is rejected before opening probes`() = runTest {
        val events = repository.discover(
            defaultParams.copy(maxHops = 10, timeoutMs = 30_000, retries = 5)
        ).toList()

        val error = events.filterIsInstance<TopologyDiscoveryEvent.Error>().single()
        assertEquals(TopologyOperationBudget.OVER_CEILING_MESSAGE, error.message)
        coVerify(exactly = 0) { snmpClient.get(any(), any()) }
        coVerify(exactly = 0) { snmpClient.walk(any(), any(), any()) }
    }

    @Test
    fun `scope cleanup failure is reported and suppresses Complete`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())
        every { snmpClient.close() } throws IllegalStateException("client close failed")

        val events = repository.discover(defaultParams).toList()

        assertTrue(events.any {
            it is TopologyDiscoveryEvent.Error && it.cause?.cause?.message == "client close failed"
        })
        assertTrue(events.none { it is TopologyDiscoveryEvent.Complete })
        verify(exactly = 1) { snmpClient.close() }
    }

    @Test
    fun `CDP hex address is linked and queued`() = runTest {
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco IOS switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed-switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns null
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns "100"
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())
        coEvery { snmpClient.walk(any(), "1.3.6.1.4.1.9.9.23.1.2.1", any()) } returns SnmpWalkResult(mapOf(
            "1.3.6.1.4.1.9.9.23.1.2.1.1.3.1.1" to "1",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.4.1.1" to "c0:a8:01:03",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.6.1.1" to "edge-switch",
            "1.3.6.1.4.1.9.9.23.1.2.1.1.7.1.1" to "Gi1/0/3"
        ))
        coEvery {
            snmpClient.walk(match { it.ip == "192.168.1.3" }, "1.3.6.1.4.1.9.9.23.1.2.1", any())
        } returns SnmpWalkResult(emptyMap())

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
        coEvery { snmpClient.walk(any(), any(), any()) } coAnswers {
            val active = activeWalks.incrementAndGet()
            maximumConcurrentWalks.updateAndGet { current -> maxOf(current, active) }
            delay(25)
            activeWalks.decrementAndGet()
            SnmpWalkResult(emptyMap())
        }

        repository.discover(defaultParams.copy(maxHops = 0)).toList()

        assertTrue(maximumConcurrentWalks.get() > 1)
        assertTrue(maximumConcurrentWalks.get() <= 4)
    }

    @Test
    fun `caller session caps concurrent walks below repository maximum`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        val activeWalks = AtomicInteger(0)
        val maximumConcurrentWalks = AtomicInteger(0)
        coEvery { snmpClient.walk(any(), any(), any()) } coAnswers {
            val active = activeWalks.incrementAndGet()
            maximumConcurrentWalks.updateAndGet { current -> maxOf(current, active) }
            try {
                delay(25)
            } finally {
                activeWalks.decrementAndGet()
            }
            SnmpWalkResult(emptyMap())
        }
        val session = OperationSession(OperationBudget.start(maxConcurrentProbes = 1))

        repository.discover(defaultParams.copy(maxHops = 0), session).toList()

        assertEquals(1, maximumConcurrentWalks.get())
    }

    @Test
    fun `repository walk cap is shared across sessions and releases queued work after cancellation`() = runTest {
        val targetIps = (101..106).map { "192.168.1.$it" }
        val sessions = targetIps.associateWith {
            OperationSession(OperationBudget.start(maxConcurrentProbes = 1))
        }
        val activeWalks = AtomicInteger(0)
        val maximumConcurrentWalks = AtomicInteger(0)
        val activeTargetIps = ConcurrentHashMap.newKeySet<String>()
        val observedTargetIps = ConcurrentHashMap.newKeySet<String>()
        val clientFactoriesReady = CountDownLatch(targetIps.size)
        val releaseClientFactories = CountDownLatch(1)
        val fourSessionsWalking = CompletableDeferred<Unit>()
        val fifthTargetEntered = CompletableDeferred<String>()
        val releaseWalks = CompletableDeferred<Unit>()
        val clientCloseCounts = ConcurrentHashMap<String, AtomicInteger>()

        val boundedRepository = TopologyDiscoveryRepositoryImpl(SnmpClientFactory { clientParams ->
            clientFactoriesReady.countDown()
            check(releaseClientFactories.await(5, TimeUnit.SECONDS)) {
                "Timed out waiting to release SNMP client factory barrier"
            }
            val client = object : SnmpClient {
                override suspend fun get(target: SnmpTarget, oid: String): String? = null

                override suspend fun walk(
                    target: SnmpTarget,
                    oidPrefix: String,
                    budget: SnmpWalkBudget,
                ): SnmpWalkResult {
                    val active = activeWalks.incrementAndGet()
                    maximumConcurrentWalks.updateAndGet { current -> maxOf(current, active) }
                    activeTargetIps.add(target.ip)
                    observedTargetIps.add(target.ip)
                    if (activeTargetIps.size >= 4) fourSessionsWalking.complete(Unit)
                    if (observedTargetIps.size > 4) fifthTargetEntered.complete(target.ip)
                    try {
                        releaseWalks.await()
                        return SnmpWalkResult(emptyMap())
                    } finally {
                        activeTargetIps.remove(target.ip)
                        activeWalks.decrementAndGet()
                    }
                }

                override fun close() {
                    clientCloseCounts.computeIfAbsent(clientParams.targetIp) { AtomicInteger() }
                        .incrementAndGet()
                }
            }
            client
        })
        val jobs = targetIps.map { targetIp ->
            launch {
                try {
                    boundedRepository.discover(
                        defaultParams.copy(targetIp = targetIp, maxHops = 0),
                        sessions.getValue(targetIp),
                    ).toList()
                } catch (cancelled: CancellationException) {
                    if (sessions.getValue(targetIp).cancellationReason != CancellationReason.USER_STOP) {
                        throw cancelled
                    }
                }
            }
        }

        try {
            withContext(Dispatchers.IO) {
                withTimeout(5_000) {
                    assertTrue(clientFactoriesReady.await(5, TimeUnit.SECONDS))
                }
            }
            releaseClientFactories.countDown()
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { fourSessionsWalking.await() }
            }
            val canceledTarget = activeTargetIps.first()
            assertEquals(4, activeTargetIps.size)
            assertEquals(4, maximumConcurrentWalks.get())

            sessions.getValue(canceledTarget).cancel(CancellationReason.USER_STOP)
            val queuedTarget = withContext(Dispatchers.IO) {
                withTimeout(5_000) { fifthTargetEntered.await() }
            }
            assertNotEquals(canceledTarget, queuedTarget)
            assertTrue(queuedTarget in targetIps)
            assertEquals(4, maximumConcurrentWalks.get())
            assertEquals(CancellationReason.USER_STOP, sessions.getValue(canceledTarget).cancellationReason)

            releaseWalks.complete(Unit)
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { jobs.joinAll() }
            }

            assertEquals(targetIps.size, observedTargetIps.size)
            assertEquals(1, clientCloseCounts.getValue(canceledTarget).get())
            targetIps.filterNot { it == canceledTarget }.forEach { targetIp ->
                assertEquals(1, clientCloseCounts.getValue(targetIp).get())
            }
            assertTrue(sessions.values.all { it.resources.isClosed })
            assertEquals(4, maximumConcurrentWalks.get())
        } finally {
            releaseClientFactories.countDown()
            releaseWalks.complete(Unit)
            sessions.values.forEach { it.cancel(CancellationReason.USER_STOP) }
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { jobs.joinAll() }
            }
        }
    }

    @Test
    fun `repository caps scalar GET requests across sessions and resumes queued request after cancellation`() = runTest {
        val targetIps = (111..116).map { "192.168.1.$it" }
        val sessions = targetIps.associateWith {
            OperationSession(OperationBudget.start(maxConcurrentProbes = 4))
        }
        val activeGets = AtomicInteger(0)
        val maximumConcurrentGets = AtomicInteger(0)
        val activeGetTargets = ConcurrentHashMap.newKeySet<String>()
        val observedGetTargets = ConcurrentHashMap.newKeySet<String>()
        val clientFactoriesReady = CountDownLatch(targetIps.size)
        val releaseClientFactories = CountDownLatch(1)
        val fourTargetsInGet = CompletableDeferred<Unit>()
        val fifthTargetInGet = CompletableDeferred<String>()
        val releaseGets = CompletableDeferred<Unit>()
        val clientCloseCounts = ConcurrentHashMap<String, AtomicInteger>()
        val firstSystemOid = "1.3.6.1.2.1.1.1.0"

        val boundedRepository = TopologyDiscoveryRepositoryImpl(SnmpClientFactory { clientParams ->
            clientFactoriesReady.countDown()
            check(releaseClientFactories.await(5, TimeUnit.SECONDS)) {
                "Timed out waiting to release SNMP client factory barrier"
            }
            val client = object : SnmpClient {
                override suspend fun get(target: SnmpTarget, oid: String): String? {
                    if (oid != firstSystemOid) return null
                    val active = activeGets.incrementAndGet()
                    maximumConcurrentGets.updateAndGet { current -> maxOf(current, active) }
                    activeGetTargets.add(target.ip)
                    observedGetTargets.add(target.ip)
                    if (activeGetTargets.size >= 4) fourTargetsInGet.complete(Unit)
                    if (observedGetTargets.size > 4) fifthTargetInGet.complete(target.ip)
                    try {
                        releaseGets.await()
                        return null
                    } finally {
                        activeGetTargets.remove(target.ip)
                        activeGets.decrementAndGet()
                    }
                }

                override suspend fun walk(
                    target: SnmpTarget,
                    oidPrefix: String,
                    budget: SnmpWalkBudget,
                ): SnmpWalkResult = SnmpWalkResult(emptyMap())

                override fun close() {
                    clientCloseCounts.computeIfAbsent(clientParams.targetIp) { AtomicInteger() }
                        .incrementAndGet()
                }
            }
            client
        })
        val jobs = targetIps.map { targetIp ->
            launch {
                try {
                    boundedRepository.discover(
                        defaultParams.copy(targetIp = targetIp, maxHops = 0),
                        sessions.getValue(targetIp),
                    ).toList()
                } catch (cancelled: CancellationException) {
                    if (sessions.getValue(targetIp).cancellationReason != CancellationReason.USER_STOP) {
                        throw cancelled
                    }
                }
            }
        }

        try {
            withContext(Dispatchers.IO) {
                withTimeout(5_000) {
                    assertTrue(clientFactoriesReady.await(5, TimeUnit.SECONDS))
                }
            }
            releaseClientFactories.countDown()
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { fourTargetsInGet.await() }
            }
            val canceledTarget = activeGetTargets.first()
            assertEquals(4, activeGetTargets.size)
            assertEquals(4, maximumConcurrentGets.get())

            sessions.getValue(canceledTarget).cancel(CancellationReason.USER_STOP)
            val queuedTarget = withContext(Dispatchers.IO) {
                withTimeout(5_000) { fifthTargetInGet.await() }
            }
            assertNotEquals(canceledTarget, queuedTarget)
            assertTrue(queuedTarget in targetIps)
            assertEquals(4, activeGetTargets.size)
            assertEquals(4, maximumConcurrentGets.get())
            assertEquals(CancellationReason.USER_STOP, sessions.getValue(canceledTarget).cancellationReason)

            releaseGets.complete(Unit)
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { jobs.joinAll() }
            }

            assertEquals(targetIps.size, observedGetTargets.size)
            assertEquals(1, clientCloseCounts.getValue(canceledTarget).get())
            targetIps.filterNot { it == canceledTarget }.forEach { targetIp ->
                assertEquals(1, clientCloseCounts.getValue(targetIp).get())
            }
            assertTrue(sessions.values.all { it.resources.isClosed })
            assertEquals(4, maximumConcurrentGets.get())
        } finally {
            releaseClientFactories.countDown()
            releaseGets.complete(Unit)
            sessions.values.forEach { it.cancel(CancellationReason.USER_STOP) }
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { jobs.joinAll() }
            }
        }
    }

    @Test
    fun `SNMP client is created off the collecting thread`() = runTest {
        val collectingThreadId = Thread.currentThread().id
        var factoryThreadId: Long? = null
        val factoryClient = mockk<SnmpClient>(relaxed = true)
        coEvery { factoryClient.get(any(), any()) } returns null
        coEvery { factoryClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())
        val factoryRepository = TopologyDiscoveryRepositoryImpl(SnmpClientFactory {
            factoryThreadId = Thread.currentThread().id
            factoryClient
        })

        factoryRepository.discover(defaultParams.copy(maxHops = 0)).toList()

        assertNotNull(factoryThreadId)
        assertNotEquals(collectingThreadId, factoryThreadId)
    }

    @Test
    fun `high fan out deduplicates pending targets and reports the queue cap`() = runTest {
        val limits = TopologyResourceLimits(maxNodes = 32, maxLinks = 32, maxPendingTargets = 2)
        val boundedRepository = TopologyDiscoveryRepositoryImpl(snmpClient, limits)
        val seedIp = defaultParams.targetIp
        val cdpOid = "1.3.6.1.4.1.9.9.23.1.2.1"
        val neighbours = cdpNeighbours((20..27).toList())

        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco switch"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "seed"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.6.0") } returns null
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.3.0") } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } coAnswers {
            val target = firstArg<SnmpTarget>()
            val oid = secondArg<String>()
            SnmpWalkResult(if (target.ip == seedIp && oid == cdpOid) neighbours else emptyMap())
        }

        val events = boundedRepository.discover(defaultParams).toList()
        val graph = events.filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph

        assertEquals(3, graph.nodes.size)
        assertEquals(8, graph.links.size)
        assertTrue(TopologyTruncationReason.PENDING_TARGET_LIMIT in graph.truncationReasons)
        coVerify(exactly = 1) {
            snmpClient.get(match { it.ip == "192.168.1.20" }, "1.3.6.1.2.1.1.1.0")
        }
    }

    @Test
    fun `interface and vlan lists are capped before attaching them to the node`() = runTest {
        val limits = TopologyResourceLimits(maxInterfacesPerNode = 1, maxVlansPerNode = 1)
        val boundedRepository = TopologyDiscoveryRepositoryImpl(snmpClient, limits)
        val interfacePrefix = "1.3.6.1.2.1.2.2.1.2"
        val vlanPrefix = "1.3.6.1.4.1.9.9.46.1.3.1.1.4"

        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } coAnswers {
            val oid = secondArg<String>()
            val values = when (oid) {
                interfacePrefix -> mapOf("$interfacePrefix.1" to "eth0", "$interfacePrefix.2" to "eth1")
                vlanPrefix -> mapOf("$vlanPrefix.1" to "users", "$vlanPrefix.2" to "voice")
                else -> emptyMap()
            }
            SnmpWalkResult(values)
        }

        val graph = boundedRepository.discover(defaultParams.copy(maxHops = 0))
            .toList().filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph

        val node = graph.nodes.single()
        assertEquals(1, node.interfaces.size)
        assertEquals(1, node.vlans.size)
        assertTrue(TopologyTruncationReason.INTERFACE_LIMIT in graph.truncationReasons)
        assertTrue(TopologyTruncationReason.VLAN_LIMIT in graph.truncationReasons)
        assertEquals(
            TopologyTableCompleteness.PARTIAL,
            node.tableObservations.getValue(TopologyDataTable.INTERFACES).completeness
        )
        assertTrue(TopologyTableFailure.TRUNCATED in node.tableObservations.getValue(TopologyDataTable.INTERFACES).failures)
        assertEquals(
            TopologyTableCompleteness.PARTIAL,
            node.tableObservations.getValue(TopologyDataTable.VLANS).completeness
        )
        assertTrue(TopologyTableFailure.TRUNCATED in node.tableObservations.getValue(TopologyDataTable.VLANS).failures)
    }

    @Test
    fun `walk truncation metadata reaches the completed graph`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(
            entries = emptyMap(),
            truncationReasons = setOf(TopologyTruncationReason.WALK_BYTE_LIMIT)
        )

        val graph = repository.discover(defaultParams.copy(maxHops = 0))
            .toList().filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph

        assertTrue(TopologyTruncationReason.WALK_BYTE_LIMIT in graph.truncationReasons)
    }

    @Test
    fun `thrown walk errors are represented in the completed graph`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } throws java.net.SocketTimeoutException("timeout")

        val graph = repository.discover(defaultParams.copy(maxHops = 0))
            .toList().filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph

        assertTrue(graph.hadSnmpErrors)
        assertTrue(graph.truncationReasons.isEmpty())
    }

    @Test
    fun `graph byte budget accepts exact node estimate and rejects one byte less`() = runTest {
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())
        val node = repository.discover(defaultParams.copy(maxHops = 0))
            .toList().filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph.nodes.single()
        val exactBytes = TopologyDiscoveryRepositoryImpl.GraphByteBudget.estimateBytes(node).toInt()

        assertTrue(TopologyDiscoveryRepositoryImpl.GraphByteBudget(exactBytes).tryReserve(node))
        assertFalse(TopologyDiscoveryRepositoryImpl.GraphByteBudget(exactBytes - 1).tryReserve(node))

        val boundedRepository = TopologyDiscoveryRepositoryImpl(
            snmpClient,
            TopologyResourceLimits(maxBytesPerGraph = exactBytes - 1)
        )
        val partialGraph = boundedRepository.discover(defaultParams.copy(maxHops = 0))
            .toList().filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph
        assertTrue(partialGraph.nodes.isEmpty())
        assertTrue(TopologyTruncationReason.GRAPH_BYTE_LIMIT in partialGraph.truncationReasons)
    }

    @Test
    fun `graph byte rejection marks the affected neighbor table incomplete`() = runTest {
        val firstNeighborLink = TopologyLink(
            fromIp = defaultParams.targetIp,
            fromPort = null,
            toIp = "192.168.1.20",
            toPort = null,
            protocol = LinkProtocol.CDP,
            neighbourSysName = "edge-20"
        )
        val baselineNode = discoverWithCdp(TopologyResourceLimits(), emptyMap()).nodes.single()
        val maxGraphBytes = (
            TopologyDiscoveryRepositoryImpl.GraphByteBudget.estimateBytes(baselineNode) +
                TopologyDiscoveryRepositoryImpl.GraphByteBudget.estimateBytes(firstNeighborLink)
            ).toInt()
        val events = discoverEventsWithCdp(
            TopologyResourceLimits(maxBytesPerGraph = maxGraphBytes),
            cdpNeighbours(listOf(20, 21))
        )
        val graph = events.filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph
        val node = graph.nodes.single()
        val observation = node.tableObservations.getValue(TopologyDataTable.CDP_NEIGHBORS)
        assertEquals(1, events.count { it is TopologyDiscoveryEvent.NodeDiscovered })
        val nodeEventIndex = events.indexOfFirst { it is TopologyDiscoveryEvent.NodeDiscovered }
        val linkEvents = events.withIndex().filter { it.value is TopologyDiscoveryEvent.LinkDiscovered }

        assertEquals(listOf("192.168.1.20"), graph.links.map { it.toIp })
        assertEquals(listOf("192.168.1.20"), linkEvents.map {
            (it.value as TopologyDiscoveryEvent.LinkDiscovered).link.toIp
        })
        assertTrue(linkEvents.all { nodeEventIndex < it.index })
        assertFalse(linkEvents.any {
            (it.value as TopologyDiscoveryEvent.LinkDiscovered).link.toIp == "192.168.1.21"
        })
        assertTrue(TopologyTruncationReason.GRAPH_BYTE_LIMIT in graph.truncationReasons)
        assertEquals(TopologyTableCompleteness.PARTIAL, observation.completeness)
        assertTrue(TopologyTableFailure.TRUNCATED in observation.failures)
    }

    @Test
    fun `oversized system strings are capped before being retained in the graph`() = runTest {
        val boundedRepository = TopologyDiscoveryRepositoryImpl(
            snmpClient,
            TopologyResourceLimits(maxValueChars = 4)
        )
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.1.0") } returns "Cisco switch description"
        coEvery { snmpClient.get(any(), "1.3.6.1.2.1.1.5.0") } returns "core"
        coEvery { snmpClient.walk(any(), any(), any()) } returns SnmpWalkResult(emptyMap())

        val graph = boundedRepository.discover(defaultParams.copy(maxHops = 0))
            .toList().filterIsInstance<TopologyDiscoveryEvent.Complete>().single().graph

        assertEquals("Cisc", graph.nodes.single().sysDescr)
        assertTrue(TopologyTruncationReason.SCALAR_VALUE_LIMIT in graph.truncationReasons)
    }

    @Test
    fun `node cap is quiet at exact capacity and reported only when a target is rejected`() = runTest {
        val limits = TopologyResourceLimits(maxNodes = 1)
        val exactGraph = discoverWithCdp(limits, emptyMap(), maxHops = 1)
        val partialGraph = discoverWithCdp(limits, cdpNeighbours(listOf(20)), maxHops = 1)

        assertEquals(1, exactGraph.nodes.size)
        assertFalse(TopologyTruncationReason.NODE_LIMIT in exactGraph.truncationReasons)
        assertEquals(1, partialGraph.nodes.size)
        assertTrue(TopologyTruncationReason.NODE_LIMIT in partialGraph.truncationReasons)
    }

    @Test
    fun `link cap is quiet at exact capacity and reported when another link is rejected`() = runTest {
        val limits = TopologyResourceLimits(maxLinks = 1)
        val exactGraph = discoverWithCdp(limits, cdpNeighbours(listOf(20)))
        val partialGraph = discoverWithCdp(limits, cdpNeighbours(listOf(20, 21)))

        assertEquals(1, exactGraph.links.size)
        assertFalse(TopologyTruncationReason.LINK_LIMIT in exactGraph.truncationReasons)
        assertEquals(
            TopologyTableCompleteness.COMPLETE,
            exactGraph.nodes.single().tableObservations.getValue(TopologyDataTable.CDP_NEIGHBORS).completeness
        )
        assertEquals(1, partialGraph.links.size)
        assertTrue(TopologyTruncationReason.LINK_LIMIT in partialGraph.truncationReasons)
        val partialObservation = partialGraph.nodes.single().tableObservations.getValue(TopologyDataTable.CDP_NEIGHBORS)
        assertEquals(TopologyTableCompleteness.PARTIAL, partialObservation.completeness)
        assertTrue(TopologyTableFailure.TRUNCATED in partialObservation.failures)
    }

    @Test
    fun `cancelling an active discovery closes its SNMP client without a terminal event`() =
        kotlinx.coroutines.runBlocking {
        val walkStarted = CompletableDeferred<Unit>()
        coEvery { snmpClient.get(any(), any()) } returns null
        coEvery { snmpClient.walk(any(), any(), any()) } coAnswers {
            walkStarted.complete(Unit)
            awaitCancellation()
        }
        val events = java.util.concurrent.ConcurrentLinkedQueue<TopologyDiscoveryEvent>()

        val job = launch {
            repository.discover(defaultParams).collect(events::add)
        }
        withTimeout(2_000) { walkStarted.await() }
        withTimeout(2_000) { job.cancelAndJoin() }

        verify(exactly = 1) { snmpClient.close() }
        assertFalse(events.any { it is TopologyDiscoveryEvent.Complete || it is TopologyDiscoveryEvent.Error })
    }

    @Test
    fun `cancellation closes the client while a blocking GET is in progress`() = kotlinx.coroutines.runBlocking {
        val getStarted = CompletableDeferred<Unit>()
        val releaseGet = java.util.concurrent.CountDownLatch(1)
        val closeCount = AtomicInteger()
        val blockingClient = object : SnmpClient {
            override suspend fun get(target: SnmpTarget, oid: String): String? {
                getStarted.complete(Unit)
                releaseGet.await()
                return null
            }

            override suspend fun walk(
                target: SnmpTarget,
                oidPrefix: String,
                budget: SnmpWalkBudget
            ) = SnmpWalkResult(emptyMap())

            override fun close() {
                closeCount.incrementAndGet()
                releaseGet.countDown()
            }
        }
        val events = java.util.concurrent.ConcurrentLinkedQueue<TopologyDiscoveryEvent>()
        val blockingRepository = TopologyDiscoveryRepositoryImpl(blockingClient)
        val job = launch { blockingRepository.discover(defaultParams).collect(events::add) }

        withTimeout(2_000) { getStarted.await() }
        withTimeout(2_000) { job.cancelAndJoin() }

        assertEquals(1, closeCount.get())
        assertFalse(events.any { it is TopologyDiscoveryEvent.Complete || it is TopologyDiscoveryEvent.Error })
    }

    private fun cdpNeighbours(indices: List<Int>): Map<String, String> = buildMap {
        val prefix = "1.3.6.1.4.1.9.9.23.1.2.1.1"
        indices.forEach { lastOctet ->
            val neighborIndex = lastOctet.toString()
            put("$prefix.4.1.$neighborIndex", "192.168.1.$lastOctet")
            put("$prefix.6.1.$neighborIndex", "edge-$lastOctet")
        }
    }

    private suspend fun discoverWithCdp(
        limits: TopologyResourceLimits,
        neighbours: Map<String, String>,
        maxHops: Int = 0
    ): TopologyGraph = discoverEventsWithCdp(limits, neighbours, maxHops)
        .filterIsInstance<TopologyDiscoveryEvent.Complete>()
        .single()
        .graph

    private suspend fun discoverEventsWithCdp(
        limits: TopologyResourceLimits,
        neighbours: Map<String, String>,
        maxHops: Int = 0
    ): List<TopologyDiscoveryEvent> {
        val client = mockk<SnmpClient>(relaxed = true)
        coEvery { client.get(any(), any()) } returns null
        coEvery { client.walk(any(), any(), any()) } coAnswers {
            if (secondArg<String>() == "1.3.6.1.4.1.9.9.23.1.2.1") {
                SnmpWalkResult(neighbours)
            } else {
                SnmpWalkResult(emptyMap())
            }
        }
        return TopologyDiscoveryRepositoryImpl(client, limits)
            .discover(defaultParams.copy(maxHops = maxHops))
            .toList()
    }
}
