package net.aieat.netswissknife.core.network.operation

import java.util.concurrent.atomic.AtomicLong
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock

/** Immutable limits and one monotonic deadline shared by an operation and its children. */
class OperationBudget private constructor(
    val operationId: OperationId,
    val requirement: OperationRequirement,
    val deadline: OperationDeadline,
    val hasDeadline: Boolean,
    val maxConcurrentProbes: Int,
    val maxResponseBytes: Long,
    val maxExportBytes: Long,
) {
    fun remainingNanos(): Long = if (hasDeadline) deadline.remainingNanos() else Long.MAX_VALUE

    /** Returns a ceiling so a positive sub-millisecond budget never becomes a zero timeout. */
    fun remainingTimeoutMillis(): Long = if (hasDeadline) deadline.remainingTimeoutMillis() else Long.MAX_VALUE

    /** Throws when the shared deadline has elapsed; callers should check around blocking work. */
    fun throwIfExpired() {
        if (hasDeadline) deadline.throwIfExpired()
    }

    companion object {
        const val DEFAULT_INTERACTIVE_TIMEOUT_MILLIS = 120_000L
        const val DEFAULT_MAX_CONCURRENT_PROBES = 64
        const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L
        const val DEFAULT_MAX_EXPORT_BYTES = 5_242_880L

        fun start(
            operationId: OperationId = OperationId.create(),
            requirement: OperationRequirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis: Long = DEFAULT_INTERACTIVE_TIMEOUT_MILLIS,
            maxConcurrentProbes: Int = DEFAULT_MAX_CONCURRENT_PROBES,
            maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
            maxExportBytes: Long = DEFAULT_MAX_EXPORT_BYTES,
            clock: MonotonicClock = SystemMonotonicClock,
        ): OperationBudget {
            require(timeoutMillis > 0) { "Operation timeout must be positive" }
            require(maxConcurrentProbes > 0) { "Probe concurrency must be positive" }
            require(maxResponseBytes > 0) { "Maximum response bytes must be positive" }
            require(maxExportBytes > 0) { "Maximum export bytes must be positive" }

            val timeoutNanos = if (timeoutMillis > Long.MAX_VALUE / NANOS_PER_MILLISECOND) {
                Long.MAX_VALUE
            } else {
                timeoutMillis * NANOS_PER_MILLISECOND
            }
            return OperationBudget(
                operationId = operationId,
                requirement = requirement,
                deadline = OperationDeadline(
                    startedAtNanos = clock.nowNanos(),
                    timeoutNanos = timeoutNanos,
                    clock = clock,
                ),
                hasDeadline = true,
                maxConcurrentProbes = maxConcurrentProbes,
                maxResponseBytes = maxResponseBytes,
                maxExportBytes = maxExportBytes,
            )
        }

        /**
         * Creates an operation with bounded resources but no time deadline. This is reserved
         * for user-owned continuous sessions whose documented stop condition is explicit
         * cancellation, not an arbitrary duration.
         */
        fun startUnbounded(
            operationId: OperationId = OperationId.create(),
            requirement: OperationRequirement = OperationRequirement.ANY_NETWORK,
            maxConcurrentProbes: Int = DEFAULT_MAX_CONCURRENT_PROBES,
            maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
            maxExportBytes: Long = DEFAULT_MAX_EXPORT_BYTES,
            clock: MonotonicClock = SystemMonotonicClock,
        ): OperationBudget {
            require(maxConcurrentProbes > 0) { "Probe concurrency must be positive" }
            require(maxResponseBytes > 0) { "Maximum response bytes must be positive" }
            require(maxExportBytes > 0) { "Maximum export bytes must be positive" }

            return OperationBudget(
                operationId = operationId,
                requirement = requirement,
                deadline = OperationDeadline(
                    startedAtNanos = clock.nowNanos(),
                    timeoutNanos = Long.MAX_VALUE,
                    clock = clock,
                ),
                hasDeadline = false,
                maxConcurrentProbes = maxConcurrentProbes,
                maxResponseBytes = maxResponseBytes,
                maxExportBytes = maxExportBytes,
            )
        }
    }
}

/** Deadline represented as a start instant and duration to avoid absolute-time overflow. */
class OperationDeadline internal constructor(
    private val startedAtNanos: Long,
    val timeoutNanos: Long,
    private val clock: MonotonicClock,
) {
    private val greatestElapsedNanos = AtomicLong(0)

    fun remainingNanos(): Long {
        // Subtraction is intentional: it remains correct across nanoTime's signed wrap for
        // intervals shorter than 2^63 ns. A regressing fake/platform clock cannot restore time.
        val rawElapsed = clock.nowNanos() - startedAtNanos
        val candidateElapsed = rawElapsed.coerceAtLeast(0L)
        val elapsed = recordGreatestElapsed(candidateElapsed)
        return (timeoutNanos - elapsed).coerceAtLeast(0L)
    }

    fun remainingTimeoutMillis(): Long {
        val remaining = remainingNanos()
        val wholeMillis = remaining / NANOS_PER_MILLISECOND
        return if (remaining % NANOS_PER_MILLISECOND == 0L) wholeMillis else wholeMillis + 1
    }

    fun throwIfExpired() {
        if (remainingNanos() == 0L) throw OperationDeadlineExceededException()
    }

    private fun recordGreatestElapsed(candidate: Long): Long {
        while (true) {
            val previous = greatestElapsedNanos.get()
            if (candidate <= previous) return previous
            if (greatestElapsedNanos.compareAndSet(previous, candidate)) return candidate
        }
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

class OperationDeadlineExceededException : Exception("Operation deadline exceeded")

private const val NANOS_PER_MILLISECOND = 1_000_000L
