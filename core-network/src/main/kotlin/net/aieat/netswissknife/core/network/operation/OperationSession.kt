package net.aieat.netswissknife.core.network.operation

import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job

/**
 * Per-collection owner of one budget, resource scope, and first-wins cancellation reason.
 * The coroutine passed to [OperationRunner.run] must be dedicated to this operation because
 * [cancel] cancels that caller coroutine so a Flow collector cannot re-emit Stop as an error.
 */
class OperationSession(
    val budget: OperationBudget,
    val resources: ResourceScope = ResourceScope(),
) {
    /** Shared cap for native probes and optional work such as traceroute enrichment. */
    val concurrencyLimiter = OperationConcurrencyLimiter(budget.maxConcurrentProbes)

    private val lock = Any()
    private var operationJob: Job? = null
    private var callerJob: Job? = null
    private var started = false
    private var finished = false
    private var recordedCancellationReason: CancellationReason? = null

    val cancellationReason: CancellationReason?
        get() = synchronized(lock) { recordedCancellationReason }

    /**
     * Cancels this operation once started. Cancellation before start is retained and prevents
     * work. Only the first cancellation request acts; later requests cannot interrupt deadline
     * cleanup after the deadline has already won.
     */
    fun cancel(reason: CancellationReason) {
        val attachedJob = synchronized(lock) {
            if (finished) return
            if (recordedCancellationReason != null) return
            recordedCancellationReason = reason
            callerJob ?: operationJob
        }
        if (attachedJob != null) {
            attachedJob.cancel(OperationCancellationException(reason))
        } else if (!synchronized(lock) { started }) {
            resources.close()
        }
    }

    internal fun attach(job: Job, ownerJob: Job) {
        val pendingCancellation = synchronized(lock) {
            check(!started) { "An OperationSession can only be run once" }
            started = true
            operationJob = job
            callerJob = ownerJob
            recordedCancellationReason
        }
        pendingCancellation?.let { ownerJob.cancel(OperationCancellationException(it)) }
    }

    internal fun recordCancellationReason(reason: CancellationReason): CancellationReason =
        synchronized(lock) {
            if (recordedCancellationReason == null) recordedCancellationReason = reason
            checkNotNull(recordedCancellationReason)
        }

    internal fun throwIfCancelled() {
        cancellationReason?.let { reason ->
            if (reason == CancellationReason.DEADLINE_EXCEEDED) {
                throw OperationDeadlineExceededException()
            }
            throw OperationCancellationException(reason)
        }
    }

    internal fun finish(job: Job) {
        synchronized(lock) {
            if (operationJob === job) operationJob = null
            callerJob = null
            finished = true
        }
    }
}

class OperationCancellationException(
    val reason: CancellationReason,
    cause: Throwable? = null,
) : CancellationException("Operation cancelled: ${reason.name}") {
    init {
        if (cause != null) initCause(cause)
    }
}
