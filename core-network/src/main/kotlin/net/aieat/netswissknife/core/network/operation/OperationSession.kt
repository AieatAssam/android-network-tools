package net.aieat.netswissknife.core.network.operation

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.Job

/**
 * Per-collection owner of one budget, resource scope, and first-wins cancellation reason.
 * [OperationRunner.run] establishes operation ownership. [cancel] only cancels that operation's
 * child job, leaving unrelated work in a shared caller scope active.
 */
class OperationSession(
    val budget: OperationBudget,
    val resources: ResourceScope = ResourceScope(),
) {
    /** Shared cap for native probes and optional work such as traceroute enrichment. */
    val concurrencyLimiter = OperationConcurrencyLimiter(budget.maxConcurrentProbes)

    private val lock = Any()
    private var operationJob: Job? = null
    private var cleanupFuture: CompletableFuture<Unit>? = null
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
            operationJob
        }
        if (attachedJob != null) {
            attachedJob.cancel(OperationCancellationException(reason))
        } else if (!synchronized(lock) { started }) {
            closeResourcesAsync()
        }
    }

    internal fun attach(job: Job) {
        val pendingCancellation = synchronized(lock) {
            check(!started) { "An OperationSession can only be run once" }
            started = true
            operationJob = job
            recordedCancellationReason
        }
        pendingCancellation?.let { job.cancel(OperationCancellationException(it)) }
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

    /** Starts resource closure once and returns the shared completion for cancellation/finally. */
    internal fun closeResourcesAsync(): CompletableFuture<Unit> = synchronized(lock) {
        cleanupFuture ?: OperationCleanupExecutor.submit(resources).also { cleanupFuture = it }
    }

    internal fun finish(job: Job) {
        synchronized(lock) {
            if (operationJob === job) operationJob = null
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
