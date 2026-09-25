package net.aieat.netswissknife.core.network.portscan

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.testkit.ScriptedSocket
import net.aieat.netswissknife.core.network.testkit.FakeClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.OperationRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Collections
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PortScanRepositoryImpl")
class PortScanRepositoryImplTest {

    @Test
    fun `direct no-session scan derives its deadline from the requested work`() = runTest {
        val clock = FakeClock()
        val repo = PortScanRepositoryImpl(
            checker = { _, _ ->
                clock.advanceBy(1_000_000_000L)
                PortConnectResult(PortStatus.CLOSED, 1L, null)
            },
            clock = clock,
            hostResolver = { InetAddress.getLoopbackAddress() },
        )

        val updates = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                repo.scan("target", (1..100).toList(), timeoutMs = 1_000, concurrency = 1).toList()
            }
        }

        assertEquals(100, updates.filterIsInstance<PortScanUpdate.PortResult>().size)
        assertEquals(PortScanUpdate.Complete::class, updates.last()::class)
        assertTrue(clock.nowNanos() >= 100_000_000_000L)
    }

    @Test
    fun `request-sized repository session exceeds the legacy 120-second default when required`() {
        val clock = FakeClock()
        val repo = PortScanRepositoryImpl()
        val session = repo.newSession(portCount = 200, timeoutMs = 1_000, concurrency = 1, clock = clock)

        assertEquals(210_000L, session.budget.remainingTimeoutMillis())
    }

    @Test
    fun `direct repository call rejects work above its hard ceiling before resolving the host`() = runTest {
        var resolved = false
        val repo = PortScanRepositoryImpl(
            hostResolver = { resolved = true; InetAddress.getLoopbackAddress() },
        )

        val failure = runCatching {
            repo.scan("target", (1..10_000).toList(), timeoutMs = 30_000, concurrency = 1).toList()
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue((failure as? IllegalArgumentException)?.message.orEmpty().contains("15-minute operation limit"))
        assertTrue(!resolved)
    }

    @Test
    fun `direct repository call rejects nonpositive timeout before resolving the host`() = runTest {
        var resolved = false
        var probed = false
        val repo = PortScanRepositoryImpl(
            checker = { _, _ ->
                probed = true
                PortConnectResult(PortStatus.OPEN, 1L, null)
            },
            hostResolver = { resolved = true; InetAddress.getLoopbackAddress() },
        )

        val failure = runCatching {
            repo.scan("target", listOf(80), timeoutMs = 0, concurrency = 1).toList()
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("timeout must be positive"))
        assertTrue(!resolved)
        assertTrue(!probed)
    }

    @Test
    fun `deadline returns while a non-interruptible hostname resolver remains blocked`() = runTest {
        val executor = PortScanBlockingResolver.createWorkerExecutor()
        val resolverEntered = CountDownLatch(1)
        val releaseResolver = CountDownLatch(1)
        val resolverStillRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        val checkerCalls = AtomicInteger()
        val repo = PortScanRepositoryImpl(
            checker = { _, _ ->
                checkerCalls.incrementAndGet()
                PortConnectResult(PortStatus.OPEN, 1L, null)
            },
            hostResolver = {
                resolverStillRunning.set(true)
                resolverEntered.countDown()
                var released = false
                while (!released) {
                    try {
                        releaseResolver.await()
                        released = true
                    } catch (_: InterruptedException) {
                        // Model platform DNS implementations that do not stop on interruption.
                    }
                }
                resolverStillRunning.set(false)
                InetAddress.getLoopbackAddress()
            },
            resolverExecutor = executor,
        )
        val session = OperationSession(
            OperationBudget.start(timeoutMillis = 60_000, maxConcurrentProbes = 1),
        )

        try {
            val scan = async(Dispatchers.IO) {
                runCatching {
                    repo.scan("slow.example", listOf(80), 1_000, 1, session).toList()
                }.exceptionOrNull()
            }
            assertTrue(
                withContext(Dispatchers.IO) { resolverEntered.await(2, TimeUnit.SECONDS) },
                "host resolution should begin on a worker",
            )
            session.cancel(CancellationReason.DEADLINE_EXCEEDED)

            val failure = withContext(Dispatchers.IO) {
                withTimeout(2_000) { scan.await() }
            }

            assertTrue(failure is OperationDeadlineExceededException)
            assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
            assertTrue(resolverStillRunning.get(), "the resolver itself is allowed to outlive cancellation")
            assertEquals(0, checkerCalls.get(), "no port probes may start before host resolution returns")
            assertEquals(PortScanBlockingResolver.WORKER_COUNT, executor.corePoolSize)
            assertEquals(PortScanBlockingResolver.QUEUE_CAPACITY, executor.queue.remainingCapacity())
        } finally {
            releaseResolver.countDown()
            executor.shutdownNow()
            withContext(Dispatchers.IO) { executor.awaitTermination(2, TimeUnit.SECONDS) }
        }
    }

    @Test
    fun `OperationRunner deadline unwinds resolver wait using virtual time`() = runTest {
        val clock = FakeClock()
        val executor = PortScanBlockingResolver.createWorkerExecutor()
        val resolverEntered = CountDownLatch(1)
        val releaseResolver = CountDownLatch(1)
        val resolverStillRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        val session = OperationSession(OperationBudget.start(timeoutMillis = 100, clock = clock))
        val operation = backgroundScope.async {
            runCatching {
                OperationRunner.run(session) {
                    PortScanBlockingResolver.resolve(
                        session = session,
                        executor = executor,
                    ) {
                        resolverStillRunning.set(true)
                        resolverEntered.countDown()
                        var released = false
                        while (!released) {
                            try {
                                releaseResolver.await()
                                released = true
                            } catch (_: InterruptedException) {
                                // Platform resolver may keep running after the caller is cancelled.
                            }
                        }
                        InetAddress.getLoopbackAddress()
                    }
                }
            }.exceptionOrNull()
        }

        try {
            runCurrent()
            assertTrue(
                withContext(Dispatchers.IO) { resolverEntered.await(2, TimeUnit.SECONDS) },
                "resolver worker should enter the blocking call",
            )
            clock.advanceBy(100_000_000L)
            advanceTimeBy(100)
            runCurrent()

            val failure = withContext(Dispatchers.IO) {
                withTimeout(2_000) { operation.await() }
            }

            assertTrue(failure is OperationDeadlineExceededException)
            assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
            assertTrue(resolverStillRunning.get(), "deadline return must not wait for a stuck resolver")
        } finally {
            releaseResolver.countDown()
            executor.shutdownNow()
            withContext(Dispatchers.IO) { executor.awaitTermination(2, TimeUnit.SECONDS) }
        }
    }

    @Test
    fun `USER_STOP remains typed while a non-interruptible resolver is waiting`() = runTest {
        val executor = PortScanBlockingResolver.createWorkerExecutor()
        val resolverEntered = CountDownLatch(1)
        val releaseResolver = CountDownLatch(1)
        val repo = PortScanRepositoryImpl(
            hostResolver = {
                resolverEntered.countDown()
                var released = false
                while (!released) {
                    try {
                        releaseResolver.await()
                        released = true
                    } catch (_: InterruptedException) {
                        // Model a platform resolver that ignores worker interruption.
                    }
                }
                InetAddress.getLoopbackAddress()
            },
            resolverExecutor = executor,
        )
        val session = OperationSession(OperationBudget.start(timeoutMillis = 60_000))

        try {
            val scan = async(Dispatchers.IO) {
                runCatching { repo.scan("slow.example", listOf(80), 1_000, 1, session).toList() }
                    .exceptionOrNull()
            }
            assertTrue(
                withContext(Dispatchers.IO) { resolverEntered.await(2, TimeUnit.SECONDS) },
                "host resolution should begin on a worker",
            )
            session.cancel(CancellationReason.USER_STOP)
            val failure = withContext(Dispatchers.IO) {
                withTimeout(2_000) { scan.await() }
            }

            assertTrue(failure is OperationCancellationException)
            assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        } finally {
            releaseResolver.countDown()
            executor.shutdownNow()
            withContext(Dispatchers.IO) { executor.awaitTermination(2, TimeUnit.SECONDS) }
        }
    }

    @Test
    fun `resolution phase cap expires within session budget and reports host resolution timeout`() = runTest {
        val executor = PortScanBlockingResolver.createWorkerExecutor()
        val resolverEntered = CompletableDeferred<Unit>()
        val releaseResolver = CountDownLatch(1)
        val session = OperationSession(OperationBudget.start(timeoutMillis = 60_000))
        val operation = async {
            runCatching {
                PortScanBlockingResolver.resolve(
                    session = session,
                    executor = executor,
                    resolutionTimeoutMillis = 25,
                ) {
                    resolverEntered.complete(Unit)
                    var released = false
                    while (!released) {
                        try {
                            releaseResolver.await()
                            released = true
                        } catch (_: InterruptedException) {
                            // The test keeps the worker occupied until cleanup releases it.
                        }
                    }
                    InetAddress.getLoopbackAddress()
                }
            }.exceptionOrNull()
        }

        try {
            runCurrent()
            resolverEntered.await()
            advanceTimeBy(25)
            runCurrent()

            val failure = operation.await()
            assertTrue(failure is PortScanHostResolutionTimeoutException)
            assertEquals(null, session.cancellationReason, "a phase timeout must not become a session cancellation")
            assertTrue(session.budget.remainingTimeoutMillis() > 0L)
        } finally {
            releaseResolver.countDown()
            executor.shutdownNow()
            withContext(Dispatchers.IO) { executor.awaitTermination(2, TimeUnit.SECONDS) }
        }
    }

    @Test
    fun `resolver worker and queue saturation fails fast and remains bounded`() = runTest {
        val executor = PortScanBlockingResolver.createWorkerExecutor()
        val resolverEntered = CountDownLatch(PortScanBlockingResolver.WORKER_COUNT)
        val releaseResolvers = CountDownLatch(1)
        val startedSessions = List(
            PortScanBlockingResolver.WORKER_COUNT + PortScanBlockingResolver.QUEUE_CAPACITY,
        ) { OperationSession(OperationBudget.start(timeoutMillis = 60_000)) }
        val sessions = startedSessions + OperationSession(OperationBudget.start(timeoutMillis = 60_000))
        val calls = startedSessions.map { session ->
            backgroundScope.async(Dispatchers.IO) {
                runCatching {
                    PortScanBlockingResolver.resolve(session, executor) {
                        resolverEntered.countDown()
                        var released = false
                        while (!released) {
                            try {
                                releaseResolvers.await()
                                released = true
                            } catch (_: InterruptedException) {
                                // Occupy the fixed worker until the test releases it.
                            }
                        }
                        InetAddress.getLoopbackAddress()
                    }
                }.exceptionOrNull()
            }
        }

        try {
            assertTrue(
                withContext(Dispatchers.IO) { resolverEntered.await(2, TimeUnit.SECONDS) },
                "both bounded resolver workers should be occupied",
            )
            withContext(Dispatchers.IO) {
                withTimeout(2_000) {
                    while (executor.queue.size < PortScanBlockingResolver.QUEUE_CAPACITY) {
                        kotlinx.coroutines.yield()
                    }
                }
            }
            val overflow = withContext(Dispatchers.IO) {
                runCatching {
                    PortScanBlockingResolver.resolve(sessions.last(), executor) {
                        InetAddress.getLoopbackAddress()
                    }
                }.exceptionOrNull()
            }

            assertTrue(overflow is java.util.concurrent.RejectedExecutionException)
            assertEquals(PortScanBlockingResolver.WORKER_COUNT, executor.corePoolSize)
            assertTrue(executor.largestPoolSize <= PortScanBlockingResolver.WORKER_COUNT)
            assertEquals(PortScanBlockingResolver.QUEUE_CAPACITY, executor.queue.size)
        } finally {
            sessions.forEach { it.cancel(CancellationReason.USER_STOP) }
            releaseResolvers.countDown()
            executor.shutdownNow()
            withContext(Dispatchers.IO) { executor.awaitTermination(2, TimeUnit.SECONDS) }
            calls.forEach { it.cancelAndJoin() }
        }
    }

    @Test
    fun `repository default socket checker binds selected local sockets before connect`() = runTest {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        var boundBeforeConnect = false
        var socketClosed = false
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                boundBeforeConnect = binder.boundTcpSockets.singleOrNull() === this
            }

            override fun getInputStream() = ByteArrayInputStream(ByteArray(0))

            override fun close() {
                socketClosed = true
            }
        }
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 7)) },
            binder = binder,
            socketFactory = { socket }
        )
        val updates = repo.scan("target", listOf(80), timeoutMs = 100, concurrency = 1).toList()

        assertEquals(PortStatus.OPEN, updates.filterIsInstance<PortScanUpdate.PortResult>().single().result.status)
        assertTrue(boundBeforeConnect, "the selected socket must be bound before connect")
        assertEquals(listOf(socket), binder.boundTcpSockets)
        assertTrue(socketClosed, "repository scope must release a completed probe socket")
    }

    @Test
    fun `repository default socket checker skips binding when destination stays on default route`() = runTest {
        val binder = FakeNetworkBinder(shouldBindResult = false)
        var connected = false
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                connected = true
            }

            override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
        }
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 7)) },
            binder = binder,
            socketFactory = { socket },
        )

        val updates = repo.scan("target", listOf(80), timeoutMs = 100, concurrency = 1).toList()

        assertEquals(PortStatus.OPEN, updates.filterIsInstance<PortScanUpdate.PortResult>().single().result.status)
        assertTrue(connected, "the repository must continue probing over the default route")
        assertTrue(binder.boundTcpSockets.isEmpty(), "shouldBind=false must skip NetworkBinder.bind")
    }

    @Test
    fun `default socket checker caps banner read by remaining per-port timeout`() = runTest {
        data class Case(val timeoutMs: Int, val connectElapsedMs: Int, val expectedBannerTimeoutMs: Int)
        val cases = listOf(
            Case(timeoutMs = 100, connectElapsedMs = 40, expectedBannerTimeoutMs = 60),
            Case(timeoutMs = 300, connectElapsedMs = 100, expectedBannerTimeoutMs = 200),
            Case(timeoutMs = 2_000, connectElapsedMs = 50, expectedBannerTimeoutMs = 300),
        )

        cases.forEach { case ->
            val clock = FakeClock()
            var configuredBannerTimeoutMs: Int? = null
            var streamRequested = false
            val socket = object : Socket() {
                private var timeout = 0

                override fun connect(endpoint: SocketAddress?, timeout: Int) {
                    clock.advanceBy(case.connectElapsedMs * 1_000_000L)
                }

                override fun setSoTimeout(timeout: Int) {
                    this.timeout = timeout
                    configuredBannerTimeoutMs = timeout
                }

                override fun getSoTimeout(): Int = timeout

                override fun getInputStream(): InputStream {
                    streamRequested = true
                    return object : InputStream() {
                        override fun read(): Int {
                            clock.advanceBy(timeout * 1_000_000L)
                            throw SocketTimeoutException("scripted banner timeout")
                        }
                    }
                }
            }
            val repo = PortScanRepositoryImpl(
                clock = clock,
                hostResolver = { InetAddress.getLoopbackAddress() },
                socketFactory = { socket },
            )

            val updates = repo.scan("localhost", listOf(22), case.timeoutMs, concurrency = 1).toList()

            assertEquals(case.expectedBannerTimeoutMs, configuredBannerTimeoutMs)
            assertTrue(streamRequested)
            assertTrue(clock.nowNanos() <= case.timeoutMs * 1_000_000L)
            assertEquals(PortStatus.OPEN, updates.filterIsInstance<PortScanUpdate.PortResult>().single().result.status)
        }
    }

    @Test
    fun `default socket checker skips banner read when connect exhausts per-port timeout`() = runTest {
        val timeoutMs = 100
        val clock = FakeClock()
        var streamRequested = false
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                clock.advanceBy(timeoutMs * 1_000_000L)
            }

            override fun getInputStream(): InputStream {
                streamRequested = true
                return ByteArrayInputStream(ByteArray(0))
            }
        }
        val repo = PortScanRepositoryImpl(
            clock = clock,
            hostResolver = { InetAddress.getLoopbackAddress() },
            socketFactory = { socket },
        )

        val updates = repo.scan("localhost", listOf(22), timeoutMs, concurrency = 1).toList()

        assertTrue(!streamRequested, "banner input must not be opened after the timeout budget is exhausted")
        assertEquals(PortStatus.OPEN, updates.filterIsInstance<PortScanUpdate.PortResult>().single().result.status)
        assertTrue(clock.nowNanos() <= timeoutMs * 1_000_000L)
    }

    @Test
    fun `repository default socket checker surfaces local permission denial`() = runTest {
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getLoopbackAddress() },
            binder = FakeNetworkBinder(shouldBindResult = true),
            socketFactory = { throw SecurityException("permission denied") }
        )

        val error = runCatching {
            repo.scan("localhost", listOf(80), timeoutMs = 100, concurrency = 1).toList()
        }.exceptionOrNull()
        val permissionError = error as? LocalNetworkPermissionDeniedException
        assertNotNull(permissionError)
        assertEquals("permission denied", permissionError?.cause?.message)
    }

    @Test
    fun `cancelling a banner read closes its socket and emits no late result`() = runTest {
        val socket = ScriptedSocket()
        val updates = java.util.concurrent.CopyOnWriteArrayList<PortScanUpdate>()
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getLoopbackAddress() },
            socketFactory = { socket },
        )

        val collector = backgroundScope.launch(Dispatchers.IO) {
            repo.scan("localhost", listOf(22), timeoutMs = 1_000, concurrency = 1)
                .onEach(updates::add)
                .toList()
        }

        assertTrue(
            withContext(Dispatchers.IO) { socket.awaitBlockingRead(5, TimeUnit.SECONDS) },
            "default checker did not block in banner read"
        )
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { collector.cancelAndJoin() }
        }

        assertTrue(socket.isClosed, "cancelling the scan must close its active socket")
        assertTrue(updates.any { it is PortScanUpdate.Started })
        assertTrue(updates.none { it is PortScanUpdate.PortResult || it is PortScanUpdate.Complete })
    }

    @Test
    fun `cancelling a blocking connect closes its socket and emits no late result`() = runTest {
        val socket = ScriptedSocket(blockConnectUntilClosed = true)
        val updates = java.util.concurrent.CopyOnWriteArrayList<PortScanUpdate>()
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getLoopbackAddress() },
            socketFactory = { socket },
        )

        val collector = backgroundScope.launch(Dispatchers.IO) {
            repo.scan("localhost", listOf(22), timeoutMs = 1_000, concurrency = 1)
                .onEach(updates::add)
                .toList()
        }

        assertTrue(
            withContext(Dispatchers.IO) { socket.connectStarted.await(5, TimeUnit.SECONDS) },
            "default checker did not reach connect"
        )
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { collector.cancelAndJoin() }
        }

        assertTrue(socket.isClosed, "cancelling the scan must close a connecting socket")
        assertTrue(updates.any { it is PortScanUpdate.Started })
        assertTrue(updates.none { it is PortScanUpdate.PortResult || it is PortScanUpdate.Complete })
    }

    @Test
    fun `caller USER_STOP session closes active socket and emits no late result`() = runTest {
        val socket = ScriptedSocket(blockConnectUntilClosed = true)
        val updates = java.util.concurrent.CopyOnWriteArrayList<PortScanUpdate>()
        val started = CountDownLatch(1)
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getLoopbackAddress() },
            socketFactory = { socket },
        )
        val session = repo.newSession(concurrency = 1)
        val collector = backgroundScope.launch(Dispatchers.IO) {
            repo.scan("localhost", listOf(22), timeoutMs = 1_000, concurrency = 1, operationSession = session)
                .onEach { update ->
                    updates.add(update)
                    if (update is PortScanUpdate.Started) started.countDown()
                }
                .toList()
        }

        assertTrue(
            withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) },
            "scan should publish Started before probing"
        )
        assertTrue(
            withContext(Dispatchers.IO) { socket.connectStarted.await(5, TimeUnit.SECONDS) },
            "default checker did not reach connect"
        )
        session.cancel(CancellationReason.USER_STOP)
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { collector.cancelAndJoin() }
        }

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(socket.isClosed, "caller cancellation must close the active socket")
        assertTrue(updates.any { it is PortScanUpdate.Started })
        assertTrue(updates.none { it is PortScanUpdate.PortResult || it is PortScanUpdate.Complete })
    }

    @Test
    fun `caller LIFECYCLE_PAUSE session closes active socket and emits no late result`() = runTest {
        val socket = ScriptedSocket(blockConnectUntilClosed = true)
        val updates = java.util.concurrent.CopyOnWriteArrayList<PortScanUpdate>()
        val started = CountDownLatch(1)
        val repo = PortScanRepositoryImpl(
            hostResolver = { InetAddress.getLoopbackAddress() },
            socketFactory = { socket },
        )
        val session = repo.newSession(concurrency = 1)
        val collector = backgroundScope.launch(Dispatchers.IO) {
            repo.scan("localhost", listOf(22), timeoutMs = 1_000, concurrency = 1, operationSession = session)
                .onEach { update ->
                    updates.add(update)
                    if (update is PortScanUpdate.Started) started.countDown()
                }
                .toList()
        }

        assertTrue(
            withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) },
            "scan should publish Started before probing"
        )
        assertTrue(
            withContext(Dispatchers.IO) { socket.connectStarted.await(5, TimeUnit.SECONDS) },
            "default checker did not reach connect"
        )
        session.cancel(CancellationReason.LIFECYCLE_PAUSE)
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { collector.cancelAndJoin() }
        }

        assertEquals(CancellationReason.LIFECYCLE_PAUSE, session.cancellationReason)
        assertTrue(socket.isClosed, "lifecycle cancellation must close the active socket")
        assertTrue(updates.any { it is PortScanUpdate.Started })
        assertTrue(updates.none { it is PortScanUpdate.PortResult || it is PortScanUpdate.Complete })
    }

    @Test
    fun `caller session bounds worker concurrency below request`() = runTest {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val firstStarted = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = PortScanRepositoryImpl(
            checker = { _, _ ->
                val current = active.incrementAndGet()
                maximumActive.updateAndGet { maxOf(it, current) }
                if (current == 1) firstStarted.countDown()
                if (current == 2) secondStarted.countDown()
                try {
                    release.await(3, TimeUnit.SECONDS)
                } finally {
                    active.decrementAndGet()
                }
                PortConnectResult(PortStatus.CLOSED, 1L, null)
            },
            hostResolver = { InetAddress.getLoopbackAddress() },
        )
        val session = OperationSession(OperationBudget.start(maxConcurrentProbes = 1))
        val scan = backgroundScope.launch(Dispatchers.IO) {
            repo.scan(
                host = "localhost",
                ports = listOf(22, 23, 80, 443),
                timeoutMs = 1_000,
                concurrency = 4,
                operationSession = session,
            ).toList()
        }

        assertTrue(withContext(Dispatchers.IO) { firstStarted.await(2, TimeUnit.SECONDS) })
        assertTrue(
            !withContext(Dispatchers.IO) { secondStarted.await(300, TimeUnit.MILLISECONDS) },
            "the scan must not start a second probe above its caller's budget",
        )
        release.countDown()
        withContext(Dispatchers.Default) { withTimeout(3_000) { scan.join() } }

        assertEquals(1, maximumActive.get())
    }

    @Test
    fun `checker cancellation while scan remains active fails the operation`() = runTest {
        val repo = PortScanRepositoryImpl(
            checker = { _, _ -> throw kotlinx.coroutines.CancellationException("checker stopped") },
            hostResolver = { InetAddress.getLoopbackAddress() },
        )

        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.scan("localhost", listOf(22, 23), timeoutMs = 1_000, concurrency = 1).toList()
            }
        }

        assertTrue(error.message!!.contains("Port checker cancelled"))
    }

    // ── Helper checkers ────────────────────────────────────────────────────────

    private fun openChecker(rtMs: Long = 5L): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.OPEN, responseTimeMs = rtMs, banner = null)
    }

    private fun closedChecker(): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.CLOSED, responseTimeMs = 1L, banner = null)
    }

    private fun filteredChecker(): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.FILTERED, responseTimeMs = 2000L, banner = null)
    }

    private fun bannerChecker(banner: String): PortConnectChecker = { _, _ ->
        PortConnectResult(status = PortStatus.OPEN, responseTimeMs = 10L, banner = banner)
    }

    private fun testRepository(checker: PortConnectChecker) = PortScanRepositoryImpl(
        checker = checker,
        hostResolver = { java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 10)) }
    )

    // ── Emission count ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("emission count")
    inner class EmissionCount {

        @Test
        fun `emits Started one PortResult per port and one Complete`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val updates = repo.scan("example.com", ports, timeoutMs = 1000, concurrency = 10).toList()
            val portResults = updates.filterIsInstance<PortScanUpdate.PortResult>()
            val started = updates.filterIsInstance<PortScanUpdate.Started>()
            val completes = updates.filterIsInstance<PortScanUpdate.Complete>()
            assertEquals(1, started.size)
            assertEquals("192.0.2.10", started.single().resolvedIp)
            assertEquals(ports.size, started.single().totalCount)
            assertTrue(updates.first() is PortScanUpdate.Started)
            assertEquals(3, portResults.size)
            assertEquals(1, completes.size)
        }

        @Test
        fun `complete is the last event`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(22, 80), timeoutMs = 1000, concurrency = 10).toList()
            assertTrue(updates.last() is PortScanUpdate.Complete)
        }

        @Test
        fun `scanning empty port list emits Started and Complete`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", emptyList(), timeoutMs = 1000, concurrency = 10).toList()
            assertEquals(2, updates.size)
            assertTrue(updates.first() is PortScanUpdate.Started)
            assertTrue(updates.last() is PortScanUpdate.Complete)
        }
    }

    // ── Status mapping ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status mapping")
    inner class StatusMapping {

        @Test
        fun `open checker produces OPEN status`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals(PortStatus.OPEN, result.status)
        }

        @Test
        fun `closed checker produces CLOSED status`() = runTest {
            val repo = testRepository(checker = closedChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals(PortStatus.CLOSED, result.status)
        }

        @Test
        fun `filtered checker produces FILTERED status`() = runTest {
            val repo = testRepository(checker = filteredChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals(PortStatus.FILTERED, result.status)
        }
    }

    // ── Service resolution ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("service resolution")
    inner class ServiceResolution {

        @Test
        fun `port 80 resolves to HTTP`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("HTTP", result.serviceName)
        }

        @Test
        fun `port 443 resolves to HTTPS`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(443), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("HTTPS", result.serviceName)
        }

        @Test
        fun `port 22 resolves to SSH`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(22), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("SSH", result.serviceName)
        }

        @Test
        fun `unknown port has non-null service name fallback`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(12345), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertNotNull(result.serviceName)
        }
    }

    // ── Banner grabbing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("banner grabbing")
    inner class BannerGrabbing {

        @Test
        fun `banner from checker is propagated to result`() = runTest {
            val repo = testRepository(checker = bannerChecker("SSH-2.0-OpenSSH_9.0"))
            val updates = repo.scan("host", listOf(22), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertEquals("SSH-2.0-OpenSSH_9.0", result.banner)
        }

        @Test
        fun `null banner is preserved`() = runTest {
            val repo = testRepository(checker = openChecker())
            val updates = repo.scan("host", listOf(80), timeoutMs = 1000, concurrency = 10).toList()
            val result = updates.filterIsInstance<PortScanUpdate.PortResult>().first().result
            assertTrue(result.banner == null)
        }
    }

    // ── Progress tracking ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("progress tracking")
    inner class ProgressTracking {

        @Test
        fun `emits each port in completion order`() = runTest {
            // Port 80 is supplied first but is held until port 443 has actually
            // been emitted downstream. A latch rather than a sleep keeps this
            // deterministic on a loaded CI runner: probes really do block, since
            // production socket probes run on Dispatchers.IO.
            val fastPortEmitted = CountDownLatch(1)
            val checker: PortConnectChecker = { _, port ->
                if (port == 80) {
                    assertTrue(
                        fastPortEmitted.await(10, TimeUnit.SECONDS),
                        "port 443 was never emitted"
                    )
                }
                PortConnectResult(PortStatus.OPEN, 1L, null)
            }
            val repo = testRepository(checker = checker)

            val results = repo.scan("host", listOf(80, 443), 1000, concurrency = 2)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .onEach { if (it.result.port == 443) fastPortEmitted.countDown() }
                .toList()

            assertEquals(listOf(443, 80), results.map { it.result.port })
            assertEquals(listOf(1, 2), results.map { it.scannedCount })
        }

        @Test
        fun `scannedCount increments per emission`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val portResults = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .toList()
            val counts = portResults.map { it.scannedCount }.sorted()
            assertEquals(listOf(1, 2, 3), counts)
        }

        @Test
        fun `totalCount matches port list size`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(80, 443, 8080)
            val portResults = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.PortResult>()
                .toList()
            assertTrue(portResults.all { it.totalCount == 3 })
        }
    }

    // ── Summary ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolved endpoint")
    inner class ResolvedEndpoint {

        @Test
        fun `resolves hostname once and probes the same IPv4 address in the summary`() = runTest {
            val resolveCount = AtomicInteger()
            val selectedAddress = java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 44))
            val probeAddresses = Collections.synchronizedList(mutableListOf<java.net.InetAddress>())
            val checker: PortConnectChecker = { address, _ ->
                probeAddresses.add(address)
                PortConnectResult(PortStatus.OPEN, 1L, null)
            }
            val repo = PortScanRepositoryImpl(
                checker = checker,
                hostResolver = {
                    if (resolveCount.incrementAndGet() == 1) selectedAddress
                    else java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 45))
                }
            )

            val updates = repo.scan("router.example", listOf(80, 443, 8080), 1000, concurrency = 3).toList()
            val summary = updates.filterIsInstance<PortScanUpdate.Complete>().single().summary

            assertEquals(1, resolveCount.get())
            assertEquals(3, probeAddresses.size)
            assertTrue(probeAddresses.all { it === selectedAddress })
            assertEquals("router.example", summary.host)
            assertEquals(selectedAddress.hostAddress, summary.resolvedIp)
        }

        @Test
        fun `passes a selected IPv6 address unchanged to every probe`() = runTest {
            val selectedAddress = java.net.InetAddress.getByName("2001:db8::42")
            val probeAddresses = Collections.synchronizedList(mutableListOf<java.net.InetAddress>())
            val repo = PortScanRepositoryImpl(
                checker = { address, _ ->
                    probeAddresses.add(address)
                    PortConnectResult(PortStatus.OPEN, 1L, null)
                },
                hostResolver = { selectedAddress }
            )

            val summary = repo.scan("v6.example", listOf(22, 443), 1000, concurrency = 2)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .single()
                .summary

            assertEquals(2, probeAddresses.size)
            assertTrue(probeAddresses.all { it === selectedAddress })
            assertEquals(selectedAddress.hostAddress, summary.resolvedIp)
        }

        @Test
        fun `resolution failure stops before invoking any port checker`() = runTest {
            val probeCount = AtomicInteger()
            val repo = PortScanRepositoryImpl(
                checker = { _, _ ->
                    probeCount.incrementAndGet()
                    PortConnectResult(PortStatus.OPEN, 1L, null)
                },
                hostResolver = { throw java.net.UnknownHostException("no DNS answer") }
            )

            val error = assertThrows(PortScanHostResolutionException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repo.scan("missing.example", listOf(80), 1000, concurrency = 1).toList()
                }
            }

            assertTrue(error.message!!.contains("missing.example"))
            assertEquals(0, probeCount.get())
        }
    }

    @Nested
    @DisplayName("scan summary")
    inner class ScanSummary {

        @Test
        fun `summary open count matches open results`() = runTest {
            // Use port number to decide open/closed so the result is deterministic
            // regardless of concurrent execution order: 80 and 8080 are even → OPEN
            val alternating: PortConnectChecker = { _, port ->
                if (port % 2 == 0) PortConnectResult(PortStatus.OPEN, 5L, null)
                else PortConnectResult(PortStatus.CLOSED, 1L, null)
            }
            val repo = testRepository(checker = alternating)
            val ports = listOf(80, 443, 8080, 8443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(2, complete.summary.openPorts)
            assertEquals(2, complete.summary.closedPorts)
        }

        @Test
        fun `summary host matches input host`() = runTest {
            val repo = testRepository(checker = openChecker())
            val complete = repo.scan("example.com", listOf(80), timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals("example.com", complete.summary.host)
        }

        @Test
        fun `summary scannedPorts matches input ports`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(22, 80, 443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(ports.sorted(), complete.summary.scannedPorts.sorted())
        }

        @Test
        fun `summary results count matches port list size`() = runTest {
            val repo = testRepository(checker = openChecker())
            val ports = listOf(22, 80, 443)
            val complete = repo.scan("host", ports, timeoutMs = 1000, concurrency = 10)
                .filterIsInstance<PortScanUpdate.Complete>()
                .toList()
                .first()
            assertEquals(3, complete.summary.results.size)
        }
    }

    // ── WellKnownPorts ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WellKnownPorts lookup")
    inner class WellKnownPortsLookup {

        @Test
        fun `port 3306 returns MySQL`() {
            assertEquals("MySQL", WellKnownPorts.getInfo(3306)?.serviceName)
        }

        @Test
        fun `port 5432 returns PostgreSQL`() {
            assertEquals("PostgreSQL", WellKnownPorts.getInfo(5432)?.serviceName)
        }

        @Test
        fun `port 27017 returns MongoDB`() {
            assertEquals("MongoDB", WellKnownPorts.getInfo(27017)?.serviceName)
        }

        @Test
        fun `unknown port returns null info`() {
            assertTrue(WellKnownPorts.getInfo(12345) == null)
        }

        @Test
        fun `getServiceName for unknown port returns non-null fallback`() {
            assertTrue(WellKnownPorts.getServiceName(12345).isNotBlank())
        }
    }
}
