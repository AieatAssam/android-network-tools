package net.aieat.netswissknife.core.network.operation

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.Collections
import java.util.IdentityHashMap
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
    fun `deadline closes resources cancels and joins workers without success emission`() = runTest {
        val clock = FakeClock()
        val session = OperationSession(
            OperationBudget.start(timeoutMillis = 100, clock = clock)
        )
        val sockets = List(3) { ScriptedSocket() }
        val closeFailure = IllegalStateException("scripted close failure")
        val workersFinished = java.util.concurrent.CountDownLatch(2)
        val collectedEvents = CopyOnWriteArrayList<String>()
        val deadlineFailures = CopyOnWriteArrayList<Throwable>()

        val operation = backgroundScope.launch {
            flow {
                emit("started")
                OperationRunner.run(session) {
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

        runCurrent()
        sockets.take(2).forEach { socket ->
            assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        }

        clock.advanceBy(100_000_000L)
        advanceTimeBy(100)
        runCurrent()

        withTimeout(1_000) { operation.join() }

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
        val events = CopyOnWriteArrayList<String>()
        val operation = backgroundScope.launch {
            flow {
                emit("started")
                OperationRunner.run(session) {
                    resources.register(socket)
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

        runCurrent()
        assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        session.cancel(CancellationReason.USER_STOP)
        session.cancel(CancellationReason.NETWORK_LOST)
        runCurrent()
        try {
            withTimeout(1_000) { operation.join() }
        } finally {
            if (!socket.isClosed) socket.close()
        }

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(socket.isClosed)
        assertEquals(1, socket.closeCallCount)
        assertEquals(listOf("started"), events)
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
        withTimeout(1_000) { operation.join() }

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
        val operation = backgroundScope.launch {
            OperationRunner.run(session) {
                resources.register(socket)
                launch(Dispatchers.IO) { socket.getInputStream().read() }
                awaitCancellation()
            }
        }

        runCurrent()
        assertTrue(socket.awaitBlockingRead(5, TimeUnit.SECONDS), "worker did not block in read")
        operation.cancel(CancellationException("owner stopped"))
        clock.advanceBy(100_000_000L)
        advanceTimeBy(100)
        runCurrent()
        operation.join()

        assertEquals(CancellationReason.PARENT_CANCELLED, session.cancellationReason)
        assertTrue(socket.isClosed)
        assertEquals(1, socket.closeCallCount)
        assertTrue(operation.isCancelled)
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
        var closeCount = 0

        val surfacedFailure = try {
            OperationRunner.run(session) {
                resources.register(AutoCloseable { closeCount++ })
                throw operationFailure
            }
            null
        } catch (failure: Throwable) {
            failure
        }

        assertTrue(surfacedFailure is IOException)
        assertTrue(checkNotNull(surfacedFailure).failureGraph().any { it === operationFailure })
        assertEquals(1, closeCount)
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
