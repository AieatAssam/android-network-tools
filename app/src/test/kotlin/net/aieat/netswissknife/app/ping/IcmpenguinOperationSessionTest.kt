package net.aieat.netswissknife.app.ping

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.ping.PingRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IcmpenguinOperationSessionTest {
    @Test
    fun `closing operation resources cancels native collection and runs its cleanup`() = runBlocking {
        val started = CountDownLatch(1)
        val cleanedUp = CountDownLatch(1)
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000))
        val engine = IcmpenguinPingEngine {
            flow {
                started.countDown()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    cleanedUp.countDown()
                }
            }
        }
        val collector = launch(Dispatchers.Default) {
            engine.ping(PingRequest("192.0.2.7", count = 0, timeoutMs = 1_000), session).collect { }
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        session.resources.close()
        assertTrue(cleanedUp.await(2, TimeUnit.SECONDS))
        collector.join()
        assertTrue(collector.isCancelled)
    }

    @Test
    fun `session deadline is checked before emitting a native result`() {
        val now = AtomicLong(0L)
        val clock = MonotonicClock { now.get() }
        val budget = OperationBudget.start(timeoutMillis = 1, clock = clock)
        val session = OperationSession(budget)
        val engine = IcmpenguinPingEngine {
            flow {
                now.set(1_000_000L)
                emit(IcmpProbe.Timeout(1, "192.0.2.7", 56))
            }
        }

        assertThrows(OperationDeadlineExceededException::class.java) {
            runBlocking {
                engine.ping(PingRequest("192.0.2.7", count = 1, timeoutMs = 1_000), session).collect { }
            }
        }
        assertFalse(session.resources.isClosed)
        session.resources.close()
    }

    @Test
    fun `operation runner deadline closes the active native collection`() = runTest {
        val now = AtomicLong(0L)
        val clock = MonotonicClock { now.get() }
        val cleanedUp = CountDownLatch(1)
        val session = OperationSession(OperationBudget.start(timeoutMillis = 1, clock = clock))
        val started = CountDownLatch(1)
        val instrumentedEngine = IcmpenguinPingEngine {
            flow {
                started.countDown()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    cleanedUp.countDown()
                }
            }
        }
        val observedFailure = CompletableDeferred<Throwable?>()
        val operationCompleted = CountDownLatch(1)
        val operation = launch {
            observedFailure.complete(
                runCatching {
                    OperationRunner.run(session) {
                        instrumentedEngine.ping(
                            PingRequest("192.0.2.7", count = 0, timeoutMs = 1_000), session
                        ).collect { }
                    }
                }.exceptionOrNull()
            )
        }
        operation.invokeOnCompletion { operationCompleted.countDown() }

        runCurrent()
        assertTrue(withContext(Dispatchers.IO) { started.await(2, TimeUnit.SECONDS) })
        now.set(1_000_000L)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(withContext(Dispatchers.IO) { cleanedUp.await(2, TimeUnit.SECONDS) })
        runCatching { withContext(Dispatchers.IO) { session.resources.close() } }
        runCurrent()
        assertTrue(withContext(Dispatchers.IO) { operationCompleted.await(2, TimeUnit.SECONDS) })
        runCurrent()

        assertTrue(operation.isCompleted)
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertTrue(session.resources.isClosed)
        assertNotNull(observedFailure.await())
    }

    @Test
    fun `user stop reason wins when closing the native collection lease`() = runBlocking {
        val started = CountDownLatch(1)
        val cleanedUp = CountDownLatch(1)
        val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000))
        val engine = IcmpenguinPingEngine {
            flow {
                started.countDown()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    cleanedUp.countDown()
                }
            }
        }
        val collector = launch(Dispatchers.Default) {
            OperationRunner.run(session) {
                engine.ping(PingRequest("192.0.2.7", count = 0, timeoutMs = 1_000), session).collect { }
            }
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        session.cancel(CancellationReason.USER_STOP)
        collector.join()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertTrue(cleanedUp.await(2, TimeUnit.SECONDS))
        assertTrue(session.resources.isClosed)
    }
}
