package net.aieat.netswissknife.core.network.ping

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import net.aieat.netswissknife.core.network.operation.CancellationReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PingBlockingCallExecutorTest {
    @Test
    fun `active workers and queued calls stay bounded and excess work is rejected`() = runBlocking {
        val gate = CountDownLatch(1)
        val activeStarted = CountDownLatch(PingBlockingCallExecutor.WORKER_COUNT)
        val sessions = List(PingBlockingCallExecutor.WORKER_COUNT + PingBlockingCallExecutor.QUEUE_CAPACITY) {
            PingOperation.newSession()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tasks = sessions.map { session ->
            scope.async {
                PingBlockingCallExecutor.run(session) {
                    activeStarted.countDown()
                    gate.await()
                }
            }
        }

        try {
            assertTrue(activeStarted.await(5, TimeUnit.SECONDS), "fixed workers did not start")
            withTimeout(5_000) {
                while (PingBlockingCallExecutor.queuedCallCount != PingBlockingCallExecutor.QUEUE_CAPACITY) {
                    delay(1)
                }
            }
            assertEquals(PingBlockingCallExecutor.WORKER_COUNT, PingBlockingCallExecutor.activeCallCount)

            val rejectedSession = PingOperation.newSession()
            val rejection = runCatching {
                PingBlockingCallExecutor.run(rejectedSession) { "must not run" }
            }.exceptionOrNull()
            assertInstanceOf(RejectedExecutionException::class.java, rejection)
            assertEquals(PingBlockingCallExecutor.QUEUE_CAPACITY, PingBlockingCallExecutor.queuedCallCount)

            sessions.forEach { it.cancel(CancellationReason.USER_STOP) }
        } finally {
            sessions.forEach { it.cancel(CancellationReason.USER_STOP) }
            gate.countDown()
            tasks.joinAll()
            scope.coroutineContext[Job]?.cancelAndJoin()
        }

        withTimeout(5_000) {
            while (PingBlockingCallExecutor.queuedCallCount != 0 || PingBlockingCallExecutor.activeCallCount != 0) {
                delay(1)
            }
        }
    }
}
