package net.aieat.netswissknife.core.network.operation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationConcurrencyLimiterTest {

    @Test
    fun `cancellation during multi-permit acquisition returns partial reservation`() = runTest {
        val limiter = OperationConcurrencyLimiter(maxConcurrent = 2)
        val firstPermitHeld = CompletableDeferred<Unit>()
        val releaseFirstPermit = CompletableDeferred<Unit>()
        val holder = backgroundScope.launch {
            limiter.withPermit {
                firstPermitHeld.complete(Unit)
                releaseFirstPermit.await()
            }
        }
        firstPermitHeld.await()

        val waitingForBothPermits = backgroundScope.launch {
            limiter.withPermits(2) { error("cancelled acquisition must not enter its block") }
        }
        runCurrent()
        waitingForBothPermits.cancelAndJoin()

        releaseFirstPermit.complete(Unit)
        holder.join()
        var acquiredBothPermits = false
        withTimeout(1_000) {
            limiter.withPermits(2) { acquiredBothPermits = true }
        }

        assertTrue(acquiredBothPermits)
    }
}
