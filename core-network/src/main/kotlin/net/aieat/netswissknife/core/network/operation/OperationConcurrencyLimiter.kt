package net.aieat.netswissknife.core.network.operation

import kotlinx.coroutines.sync.Semaphore

/** Cancellation-safe permits shared by concurrent work within one [OperationSession]. */
class OperationConcurrencyLimiter(maxConcurrent: Int) {
    private val maxConcurrent = maxConcurrent
    private val semaphore: Semaphore

    init {
        require(maxConcurrent > 0) { "Concurrency must be positive" }
        semaphore = Semaphore(maxConcurrent)
    }

    /** Holds [permits] until [block] completes, returning partial acquisition on cancellation. */
    suspend fun <T> withPermits(permits: Int, block: suspend () -> T): T {
        require(permits > 0) { "At least one permit must be acquired" }
        require(permits <= maxConcurrent) { "Cannot acquire more permits than the concurrency limit" }

        var acquired = 0
        try {
            repeat(permits) {
                semaphore.acquire()
                acquired++
            }
            return block()
        } finally {
            repeat(acquired) { semaphore.release() }
        }
    }

    suspend fun <T> withPermit(block: suspend () -> T): T = withPermits(1, block)
}
