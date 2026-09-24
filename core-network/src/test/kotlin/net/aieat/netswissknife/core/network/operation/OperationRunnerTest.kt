package net.aieat.netswissknife.core.network.operation

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.testkit.FakeClock
import net.aieat.netswissknife.core.network.testkit.ScriptedSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationRunnerTest {
    @Test
    fun `nested adapter joins the active session without closing its resources early`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        var closeCount = 0

        val result = OperationRunner.run(session) {
            val resource = resources.register(AutoCloseable { closeCount++ })
            val nestedResult = OperationRunner.runOrJoin(session) {
                assertEquals(session, this.session)
                assertFalse(resources.isClosed)
                "nested"
            }
            assertEquals("nested", nestedResult)
            assertEquals(0, closeCount)
            assertFalse(resources.isClosed)
            nestedResult
        }

        assertEquals(1, closeCount)
        assertTrue(session.resources.isClosed)
        assertEquals("nested", result)
    }

    @Test
    fun `deadline closes resources cancels and joins workers without success emission`() = runTest {
        val clock = FakeClock()
        val session = OperationSession(
            OperationBudget.start(timeoutMillis = 100, clock = clock)
        )
        val sockets = List(3) { ScriptedSocket() }
        val closeFailure = IllegalStateException("scripted close failure")
        val workersFinished = java.util.concurrent.CountDownLatch(2)
        val resourcesClosed = CountDownLatch(1)
        val operationCompleted = CountDownLatch(1)
        val collectedEvents = CopyOnWriteArrayList<String>()
        val deadlineFailures = CopyOnWriteArrayList<Throwable>()

        val operation = backgroundScope.launch {
            flow {
                emit("started")
                OperationRunner.run(session) {
                    resources.register(AutoCloseable { resourcesClosed.countDown() })
                    resources.register(AutoCloseable { throw closeFailure })
                    resources.register(AutoCloseable { session.cancel(CancellationReason.USER_STOP) })
                    sockets.forEach(resources::register)
                    val operationContext = this
                    sockets.take(2).forEach { socket ->
                        launch(Dispatchers.IO) {
                            try {
                                socket.getInputStream().read()
                                operationContext.ensureOperationActive()
                                collectedEvents += "late-success"
                            } finally {
                                workersFinished.countDown()
                            }
                        }
                    }
                    awaitCancellation()
                }
                emit("success")
            }.catch { failure ->
                deadlineFailures += failure
                collectedEvents += if (
                    failure is OperationDeadlineExceededException && session.resources.isClosed
                ) "deadline" else "unexpected"
            }.collect(collectedEvents::add)
        }
        operation.invokeOnCompletion { operationCompleted.countDown() }

        runCurrent()
        sockets.take(2).forEach { socket ->
            assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        }

        clock.advanceBy(100_000_000L)
        advanceTimeBy(100)
        assertTrue(withContext(Dispatchers.IO) { resourcesClosed.await(5, TimeUnit.SECONDS) })
        runCatching { withContext(Dispatchers.IO) { session.resources.close() } }
        runCurrent()
        assertTrue(
            withContext(Dispatchers.IO) { operationCompleted.await(5, TimeUnit.SECONDS) },
            "operation did not finish after deadline cleanup",
        )
        runCurrent()

        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertEquals(0L, workersFinished.count)
        assertTrue(sockets.all { it.isClosed })
        assertTrue(sockets.all { it.closeCallCount == 1 })
        assertFalse(collectedEvents.contains("late-success"))
        assertEquals(listOf("started", "deadline"), collectedEvents)
        val deadlineFailure = deadlineFailures.single()
        assertTrue(deadlineFailure is OperationDeadlineExceededException)
        val cleanupFailure = deadlineFailure.failureGraph()
            .filterIsInstance<ResourceScopeCloseException>()
            .single()
        assertEquals(closeFailure, cleanupFailure.failures.single())
    }

    @Test
    fun `explicit cancellation closes blocking resources and preserves first reason`() = runTest {
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000, clock = FakeClock()))
        val socket = ScriptedSocket()
        val callerThread = Thread.currentThread()
        val closeStarted = CountDownLatch(1)
        val allowCloseToFinish = CountDownLatch(1)
        val operationCompleted = CountDownLatch(1)
        var closeThread: Thread? = null
        val events = CopyOnWriteArrayList<String>()
        val operation = backgroundScope.launch {
            flow {
                emit("started")
                OperationRunner.run(session) {
                    resources.register(AutoCloseable {
                        closeThread = Thread.currentThread()
                        closeStarted.countDown()
                        allowCloseToFinish.await(5, TimeUnit.SECONDS)
                        socket.close()
                    })
                    val operationContext = this
                    launch(Dispatchers.IO) {
                        socket.getInputStream().read()
                        operationContext.ensureOperationActive()
                        events += "late-success"
                    }
                    awaitCancellation()
                }
                emit("success")
            }.catch { events += "caught:${it::class.simpleName}" }
                .collect(events::add)
        }
        operation.invokeOnCompletion { operationCompleted.countDown() }

        runCurrent()
        assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        session.cancel(CancellationReason.USER_STOP)
        session.cancel(CancellationReason.NETWORK_LOST)
        assertTrue(closeStarted.await(5, TimeUnit.SECONDS), "cleanup did not start")
        assertTrue(closeThread !== callerThread, "cancellation closed the resource on its caller")
        allowCloseToFinish.countDown()
        withContext(Dispatchers.IO) { session.resources.close() }
        runCurrent()
        assertTrue(
            withContext(Dispatchers.IO) { operationCompleted.await(5, TimeUnit.SECONDS) },
            "operation did not finish after cleanup completed",
        )
        runCurrent()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(socket.isClosed)
        assertEquals(1, socket.closeCallCount)
        assertEquals(listOf("started"), events)
        assertTrue(operation.isCancelled)
    }

    @Test
    fun `user stop closes every registered socket joins both workers and emits no terminal outcome`() = runTest {
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000, clock = FakeClock()))
        val sockets = List(3) { ScriptedSocket() }
        val workersFinished = CountDownLatch(2)
        val operationCompleted = CountDownLatch(1)
        val events = CopyOnWriteArrayList<String>()
        val operation = backgroundScope.launch {
            flow {
                emit("started")
                OperationRunner.run(session) {
                    sockets.forEach(resources::register)
                    val operationContext = this
                    sockets.take(2).forEach { socket ->
                        launch(Dispatchers.IO) {
                            try {
                                socket.getInputStream().read()
                                operationContext.ensureOperationActive()
                                events += "late-success"
                            } finally {
                                workersFinished.countDown()
                            }
                        }
                    }
                    awaitCancellation()
                }
                emit("success")
            }.catch { failure ->
                events += "caught:${failure::class.simpleName}"
            }.collect(events::add)
        }
        operation.invokeOnCompletion { operationCompleted.countDown() }

        runCurrent()
        sockets.take(2).forEach { socket ->
            assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        }

        session.cancel(CancellationReason.USER_STOP)
        session.cancel(CancellationReason.NETWORK_LOST)
        assertTrue(
            withContext(Dispatchers.IO) { operationCompleted.await(5, TimeUnit.SECONDS) },
            "operation did not finish after stop cleanup",
        )
        runCurrent()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertEquals(0L, workersFinished.count, "operation completed before both workers finished")
        assertTrue(sockets.all { it.isClosed })
        assertTrue(sockets.all { it.closeCallCount == 1 })
        assertEquals(listOf("started"), events, "stop must not emit success or another terminal outcome")
        assertTrue(operation.isCancelled)
    }

    @Test
    fun `user stop selected before deadline remains the terminal reason`() = runTest {
        val clock = FakeClock()
        val session = OperationSession(OperationBudget.start(timeoutMillis = 100, clock = clock))
        val socket = ScriptedSocket()
        val events = CopyOnWriteArrayList<String>()
        val operation = backgroundScope.launch {
            flow {
                emit("started")
                OperationRunner.run(session) {
                    resources.register(socket)
                    launch(Dispatchers.IO) { socket.getInputStream().read() }
                    awaitCancellation()
                }
                emit("success")
            }.catch { events += "caught:${it::class.simpleName}" }
                .collect(events::add)
        }

        runCurrent()
        assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        session.cancel(CancellationReason.USER_STOP)
        clock.advanceBy(100_000_000L)
        advanceTimeBy(100)
        runCurrent()
        withContext(Dispatchers.IO) { session.resources.close() }
        runCurrent()
        operation.join()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertEquals(listOf("started"), events)
        assertEquals(1, socket.closeCallCount)
    }

    @Test
    fun `deadline completion does not throw when an earlier stop already won`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        session.recordCancellationReason(CancellationReason.USER_STOP)
        val operationJob = Job()

        val deadlineFailure = OperationRunner.completeDeadline(session, operationJob)

        assertNull(deadlineFailure)
        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(operationJob.isActive)
    }

    @Test
    fun `parent cancellation closes blocked resources before the operation unwinds`() = runTest {
        val clock = FakeClock()
        val session = OperationSession(OperationBudget.start(timeoutMillis = 100, clock = clock))
        val socket = ScriptedSocket()
        val callerThread = Thread.currentThread()
        val closeStarted = CountDownLatch(1)
        val allowCloseToFinish = CountDownLatch(1)
        var closeThread: Thread? = null
        val operation = backgroundScope.launch {
            OperationRunner.run(session) {
                resources.register(AutoCloseable {
                    closeThread = Thread.currentThread()
                    closeStarted.countDown()
                    allowCloseToFinish.await(5, TimeUnit.SECONDS)
                    socket.close()
                })
                launch(Dispatchers.IO) { socket.getInputStream().read() }
                awaitCancellation()
            }
        }

        runCurrent()
        assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        operation.cancel(CancellationException("owner stopped"))
        assertTrue(closeStarted.await(5, TimeUnit.SECONDS), "cleanup did not start")
        assertTrue(closeThread !== callerThread, "parent cancellation closed the resource on its caller")
        allowCloseToFinish.countDown()
        clock.advanceBy(100_000_000L)
        advanceTimeBy(100)
        withContext(Dispatchers.IO) { session.resources.close() }
        runCurrent()
        operation.join()

        assertEquals(CancellationReason.PARENT_CANCELLED, session.cancellationReason)
        assertTrue(socket.isClosed)
        assertEquals(1, socket.closeCallCount)
        assertTrue(operation.isCancelled)
    }

    @Test
    fun `unbounded operation waits for cancellation without a deadline timer`() = runTest {
        val session = OperationSession(OperationBudget.startUnbounded(clock = FakeClock()))
        val operation = backgroundScope.launch {
            OperationRunner.run(session) { awaitCancellation() }
        }

        runCurrent()
        assertTrue(operation.isActive)
        session.cancel(CancellationReason.USER_STOP)
        runCurrent()
        withTimeout(1_000) { operation.join() }

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(session.resources.isClosed)
    }

    @Test
    fun `user stop preserves resource close failure on cancellation`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        val closeFailure = IllegalStateException("stop cleanup failed")
        val completionFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val operation = backgroundScope.launch {
            OperationRunner.run(session) {
                resources.register(AutoCloseable { throw closeFailure })
                awaitCancellation()
            }
        }
        operation.invokeOnCompletion(completionFailure::set)
        runCurrent()

        session.cancel(CancellationReason.USER_STOP)
        operation.join()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        val surfacedFailure = checkNotNull(completionFailure.get())
        assertTrue(surfacedFailure is OperationCancellationException)
        val cleanupFailure = surfacedFailure.failureGraph()
            .filterIsInstance<ResourceScopeCloseException>()
            .single()
        assertEquals(closeFailure, cleanupFailure.failures.single())
    }

    @Test
    fun `recorded stop reason wins over a later IO failure and retains its cause`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        val ioFailure = IOException("socket closed during stop")
        var closeCount = 0

        val surfacedFailure = try {
            OperationRunner.run(session) {
                resources.register(AutoCloseable { closeCount++ })
                session.recordCancellationReason(CancellationReason.USER_STOP)
                throw ioFailure
            }
            null
        } catch (failure: Throwable) {
            failure
        }

        assertTrue(surfacedFailure is OperationCancellationException)
        assertEquals(CancellationReason.USER_STOP, (surfacedFailure as OperationCancellationException).reason)
        assertTrue(surfacedFailure.cause is IOException)
        assertEquals(ioFailure.message, surfacedFailure.cause?.message)
        assertEquals(1, closeCount)
    }

    @Test
    fun `already elapsed budget performs no operation work and closes scope`() = runTest {
        val clock = FakeClock()
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10, clock = clock))
        val events = CopyOnWriteArrayList<String>()
        clock.advanceBy(10_000_000L)

        val operation = backgroundScope.async<Throwable?> {
            try {
                OperationRunner.run(session) { events += "work" }
                null
            } catch (thrown: Throwable) {
                thrown
            }
        }
        runCurrent()
        val failure = operation.await()

        assertTrue(failure is OperationDeadlineExceededException)
        assertTrue(events.isEmpty())
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertTrue(session.resources.isClosed)
    }

    @Test
    fun `ordinary operation failure closes resources without inventing a cancellation reason`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        val operationFailure = IOException("scripted network failure")
        val closeFailure = IOException("scripted resource cleanup failure")
        var closeCount = 0

        val surfacedFailure = try {
            OperationRunner.run(session) {
                resources.register(AutoCloseable {
                    closeCount++
                    throw closeFailure
                })
                throw operationFailure
            }
            null
        } catch (failure: Throwable) {
            failure
        }

        assertTrue(surfacedFailure is IOException)
        val failureGraph = checkNotNull(surfacedFailure).failureGraph().toList()
        assertTrue(failureGraph.any { it === operationFailure })
        assertEquals(1, closeCount)
        assertTrue(session.resources.isClosed)
        assertTrue(
            failureGraph.filterIsInstance<ResourceScopeCloseException>().isNotEmpty(),
            "Failure graph: ${failureGraph.joinToString { failure ->
                "${failure.javaClass.simpleName}:${failure.message}; suppressed=${failure.suppressed.map { it.message }}"
            }}; scopeClosed=${session.resources.isClosed}; closeCount=$closeCount",
        )
        val cleanupFailure = failureGraph.filterIsInstance<ResourceScopeCloseException>().single()
        assertEquals(listOf(closeFailure), cleanupFailure.failures)
        assertEquals(
            1,
            failureGraph.count { node -> node.suppressed.any { it === cleanupFailure } },
            "cleanup failure should attach to one canonical cause only",
        )
        assertNull(session.cancellationReason)
        assertTrue(session.resources.isClosed)
    }

    @Test
    fun `session cancellation before start prevents later work`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        val events = CopyOnWriteArrayList<String>()
        session.cancel(CancellationReason.LIFECYCLE_PAUSE)
        session.cancel(CancellationReason.USER_STOP)

        val operation = backgroundScope.async {
            OperationRunner.run(session) { events += "work" }
        }
        runCurrent()
        val failure = try {
            operation.await()
            null
        } catch (thrown: Throwable) {
            thrown
        }

        assertTrue(failure is CancellationException)
        assertEquals(CancellationReason.LIFECYCLE_PAUSE, session.cancellationReason)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `session cannot be reused and rejected run does not retain a child job`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        assertEquals("completed", OperationRunner.run(session) { "completed" })

        val failure = try {
            OperationRunner.run(session) { "must not run" }
            null
        } catch (thrown: Throwable) {
            thrown
        }

        assertTrue(failure is IllegalStateException)
        assertTrue(session.resources.isClosed)
    }

    @Test
    fun `runner exposes its resource scope through child coroutine context`() = runTest {
        val session = OperationSession(OperationBudget.start(clock = FakeClock()))
        var workerScope: ResourceScope? = null

        OperationRunner.run(session) {
            launch {
                workerScope = checkNotNull(currentCoroutineContext()[OperationResourcesContext]).resources
            }.join()
        }

        assertEquals(session.resources, workerScope)
        assertTrue(session.resources.isClosed)
    }

    private fun Throwable.failureGraph(): Sequence<Throwable> = sequence {
        val pending = ArrayDeque<Throwable>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        pending.add(this@failureGraph)
        while (pending.isNotEmpty()) {
            val failure = pending.removeFirst()
            if (!seen.add(failure)) continue
            yield(failure)
            failure.cause?.let(pending::addLast)
            failure.suppressed.forEach(pending::addLast)
        }
    }
}
