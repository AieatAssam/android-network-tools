package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import net.aieat.netswissknife.core.network.operation.OperationBudget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TopologySnmpAdapterSaturationTest {

    @Test
    fun `SNMP initialization rejects overflow and recovers queued capacity without opening rejected transport`() =
        runBlocking {
            supervisorScope {
                val executor = createTopologyBlockingExecutor()
                val releaseInitializers = CountDownLatch(1)
                val activeInitializersEntered = CountDownLatch(TopologyBlockingCall.WORKER_COUNT)
                val transportFactoryCalls = AtomicInteger()
                val starterCalls = AtomicInteger()
                val createdTransports = CopyOnWriteArrayList<TrackingTransport>()
                val clients = CopyOnWriteArrayList<Snmp4jClientImpl>()
                val jobs = CopyOnWriteArrayList<kotlinx.coroutines.Deferred<Throwable?>>()

                fun newClient(): Snmp4jClientImpl = Snmp4jClientImpl(
                    sessionParams = TopologyParams(targetIp = "192.0.2.7", timeoutMs = 60_000, retries = 0),
                    operationDeadline = OperationBudget.start(timeoutMillis = 60_000).deadline,
                    transportFactory = TopologyTransportFactory { _, _ ->
                        transportFactoryCalls.incrementAndGet()
                        TrackingTransport().also(createdTransports::add)
                    },
                    transportStarter = TopologyTransportStarter {
                        starterCalls.incrementAndGet()
                        activeInitializersEntered.countDown()
                        // Intentionally do not listen or send packets. Release makes every
                        // accepted initialization fail before the adapter can issue an SNMP GET.
                        releaseInitializers.await()
                        throw IllegalStateException("released saturation fixture")
                    },
                    deferInitialization = true,
                ).also(clients::add)

                fun submit(client: Snmp4jClientImpl): kotlinx.coroutines.Deferred<Throwable?> =
                    async(Dispatchers.Default) {
                        try {
                            withContext(TopologyBlockingExecutorOverride(executor)) {
                                client.get(
                                    SnmpTarget("192.0.2.7", params = clientParams),
                                    "1.3.6.1.2.1.1.1.0",
                                )
                            }
                            null
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Throwable) {
                            failure
                        }
                    }.also(jobs::add)

                try {
                    repeat(TopologyBlockingCall.WORKER_COUNT) { submit(newClient()) }
                    assertTrue(
                        withContext(Dispatchers.IO) {
                            activeInitializersEntered.await(3, TimeUnit.SECONDS)
                        },
                        "all four SNMP initialization workers should block in the adapter starter",
                    )

                    val queuedClients = List(TopologyBlockingCall.QUEUE_CAPACITY) { newClient() }
                    val queuedJobs = queuedClients.map(::submit)
                    withTimeout(3_000) {
                        while (executor.queue.size != TopologyBlockingCall.QUEUE_CAPACITY) yield()
                    }
                    assertEquals(TopologyBlockingCall.WORKER_COUNT, executor.activeCount)
                    assertEquals(TopologyBlockingCall.QUEUE_CAPACITY, executor.queue.size)
                    assertEquals(
                        TopologyBlockingCall.WORKER_COUNT,
                        transportFactoryCalls.get(),
                        "queued SNMP adapter requests must not create transports before a worker is available",
                    )

                    val overflowClient = newClient()
                    val overflow = withTimeout(3_000) { submit(overflowClient).await() }
                    assertTrue(
                        generateSequence(overflow) { it.cause }.any { it is RejectedExecutionException },
                        "overflow should surface bounded-pool rejection through the SNMP adapter; got $overflow",
                    )
                    assertEquals(TopologyBlockingCall.WORKER_COUNT, transportFactoryCalls.get())
                    assertEquals(TopologyBlockingCall.WORKER_COUNT, starterCalls.get())

                    queuedJobs.first().cancelAndJoin()
                    assertEquals(TopologyBlockingCall.QUEUE_CAPACITY - 1, executor.queue.size)
                    assertEquals(
                        TopologyBlockingCall.WORKER_COUNT,
                        transportFactoryCalls.get(),
                        "cancelled queued SNMP initialization must not allocate a transport",
                    )

                    val replacement = submit(newClient())
                    withTimeout(3_000) {
                        while (executor.queue.size != TopologyBlockingCall.QUEUE_CAPACITY) yield()
                    }
                    assertFalse(replacement.isCompleted)
                    assertEquals(TopologyBlockingCall.WORKER_COUNT, transportFactoryCalls.get())

                    releaseInitializers.countDown()
                    val accepted = jobs.filter { it !== queuedJobs.first() }
                    withTimeout(8_000) { accepted.forEach { it.await() } }

                    assertEquals(
                        TopologyBlockingCall.WORKER_COUNT + TopologyBlockingCall.QUEUE_CAPACITY,
                        starterCalls.get(),
                        "every accepted adapter initialization should reach the test starter exactly once",
                    )
                    assertEquals(starterCalls.get(), transportFactoryCalls.get())
                    assertEquals(starterCalls.get(), createdTransports.size)
                    assertTrue(createdTransports.all { it.closeCount.get() == 1 })
                    assertTrue(executor.queue.isEmpty())
                    assertEquals(0, executor.activeCount)
                } finally {
                    releaseInitializers.countDown()
                    clients.forEach { runCatching { it.close() } }
                    jobs.forEach { it.cancel() }
                    withTimeout(3_000) { jobs.forEach { runCatching { it.await() } } }
                    executor.shutdownNow()
                    assertTrue(
                        withContext(Dispatchers.IO) { executor.awaitTermination(3, TimeUnit.SECONDS) },
                        "isolated SNMP test executor should terminate after fixture release",
                    )
                }
            }
        }

    private class TrackingTransport : DefaultUdpTransportMapping() {
        val closeCount = AtomicInteger()

        override fun close() {
            closeCount.incrementAndGet()
            super.close()
        }
    }

    private companion object {
        val clientParams = TopologyParams(targetIp = "192.0.2.7", timeoutMs = 60_000, retries = 0)
    }
}
