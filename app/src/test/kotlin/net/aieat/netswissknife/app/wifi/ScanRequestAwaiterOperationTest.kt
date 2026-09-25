package net.aieat.netswissknife.app.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ScanRequestAwaiterOperationTest {

    @Test
    fun `synchronous results broadcast unregisters receiver after registration before trigger`() = runTest {
        val unregisterCount = AtomicInteger()
        var registeredReceiver: BroadcastReceiver? = null
        var receiverRegisteredBeforeTrigger = false
        val session = session()
        val intent = mockk<Intent>()
        every { intent.action } returns WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
        every {
            intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
        } returns true
        val awaiter = awaiter(
            register = { receiver -> registeredReceiver = receiver },
            unregister = { unregisterCount.incrementAndGet() },
            startScanResult = {
                receiverRegisteredBeforeTrigger = registeredReceiver != null
                checkNotNull(registeredReceiver).onReceive(null, intent)
                true
            },
        )

        val outcome = OperationRunner.run(session) {
            awaiter.requestAndAwait(timeoutMs = 1_000L, operationSession = session)
        }

        assertTrue(receiverRegisteredBeforeTrigger, "the receiver must be registered before startScan")
        assertEquals(WifiScanRefreshStatus.UPDATED, outcome.status)
        assertEquals(1, unregisterCount.get())
    }

    @Test
    fun `rejected scan request unregisters the scoped receiver exactly once`() = runTest {
        val unregisterCount = AtomicInteger()
        var registeredReceiver: BroadcastReceiver? = null
        val session = session()
        val awaiter = awaiter(
            register = { receiver -> registeredReceiver = receiver },
            unregister = { unregisterCount.incrementAndGet() },
            startScanResult = { false },
        )

        val outcome = OperationRunner.run(session) {
            awaiter.requestAndAwait(timeoutMs = 1_000L, operationSession = session)
        }

        assertNotNull(registeredReceiver)
        assertEquals(WifiScanRefreshStatus.REJECTED, outcome.status)
        assertEquals(1, unregisterCount.get())
    }

    @Test
    fun `unscoped cancellation unregisters the scan receiver exactly once`() = runTest {
        val unregisterCount = AtomicInteger()
        var registeredReceiver: BroadcastReceiver? = null
        val awaiter = awaiter(
            register = { receiver -> registeredReceiver = receiver },
            unregister = { unregisterCount.incrementAndGet() },
        )
        val operation = launch {
            awaiter.requestAndAwait(timeoutMs = 60_000L)
        }

        runCurrent()
        assertNotNull(registeredReceiver)
        operation.cancelAndJoin()

        assertEquals(1, unregisterCount.get())
    }

    @Test
    fun `user cancellation unregisters the scoped scan receiver`() = runTest {
        val unregistered = CompletableDeferred<Unit>()
        val unregisterCount = AtomicInteger()
        var registeredReceiver: BroadcastReceiver? = null
        val session = session()
        val awaiter = awaiter(
            register = { receiver -> registeredReceiver = receiver },
            unregister = { unregisterCount.incrementAndGet(); unregistered.complete(Unit) },
        )
        val operation = launch {
            try {
                OperationRunner.run(session) {
                    awaiter.requestAndAwait(timeoutMs = 60_000L, operationSession = session)
                }
            } catch (_: CancellationException) {
                // USER_STOP is expected to cancel the operation owner.
            }
        }

        runCurrent()
        assertNotNull(registeredReceiver)
        session.cancel(CancellationReason.USER_STOP)
        operation.join()
        unregistered.await()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertEquals(1, unregisterCount.get())
    }

    @Test
    fun `deadline unregisters the scoped scan receiver`() = runTest {
        val unregistered = CompletableDeferred<Unit>()
        val unregisterCount = AtomicInteger()
        var registeredReceiver: BroadcastReceiver? = null
        val deadlineClock = MonotonicClock { testScheduler.currentTime * 1_000_000L }
        val session = session(timeoutMillis = 100L, clock = deadlineClock)
        val awaiter = awaiter(
            register = { receiver -> registeredReceiver = receiver },
            unregister = { unregisterCount.incrementAndGet(); unregistered.complete(Unit) },
        )
        val operation = launch {
            try {
                OperationRunner.run(session) {
                    awaiter.requestAndAwait(timeoutMs = 60_000L, operationSession = session)
                }
            } catch (_: OperationDeadlineExceededException) {
                // The operation deadline is expected to end the wait.
            } catch (_: CancellationException) {
                // OperationRunner may surface the deadline through structured cancellation.
            }
        }

        runCurrent()
        assertNotNull(registeredReceiver)
        advanceTimeBy(100L)
        runCurrent()
        operation.join()
        unregistered.await()

        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertEquals(1, unregisterCount.get())
    }

    private fun awaiter(
        register: (BroadcastReceiver) -> Unit,
        unregister: (BroadcastReceiver) -> Unit,
        startScanResult: () -> Boolean = { true },
    ) = ScanRequestAwaiter(
        context = mockk<Context>(relaxed = true),
        wifiManager = mockk<WifiManager> {
            every { startScan() } answers { startScanResult() }
        },
        registerReceiver = { receiver, _ -> register(receiver) },
        unregisterReceiver = unregister,
        createIntentFilter = { mockk<IntentFilter>() },
    )

    private fun session(
        timeoutMillis: Long = 10_000L,
        clock: MonotonicClock = MonotonicClock { System.nanoTime() },
    ) = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.LOCAL_NETWORK,
            timeoutMillis = timeoutMillis,
            maxConcurrentProbes = 1,
            clock = clock,
        ),
    )
}
