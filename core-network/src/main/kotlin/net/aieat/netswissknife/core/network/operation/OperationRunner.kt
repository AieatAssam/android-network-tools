package net.aieat.netswissknife.core.network.operation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

/** Coroutine scope and shared operation contract made available to one operation body. */
class OperationContext internal constructor(
    private val delegate: CoroutineScope,
    val session: OperationSession,
) : CoroutineScope by delegate {
    val budget: OperationBudget get() = session.budget
    val resources: ResourceScope get() = session.resources
    val cancellationReason: CancellationReason? get() = session.cancellationReason

    /** Checks coroutine cancellation, the shared cancellation reason, and the monotonic deadline. */
    suspend fun ensureOperationActive() = ensureCurrentOperationActive()
}

/**
 * Runs one operation with structured children, a monotonic deadline, and prompt resource closure.
 * Flow-producing blocks must use `channelFlow`/`send`; the operation-owned context cannot emit
 * directly through a `flow {}` SafeCollector.
 */
object OperationRunner {
    /**
     * Runs a nested adapter in the current scope when it shares [session], otherwise starts
     * the usual one-shot operation. This lets a composite use case share one budget and
     * resource owner across several repository calls without reattaching the session.
     */
    suspend fun <T> runOrJoin(
        session: OperationSession,
        block: suspend OperationContext.() -> T,
    ): T {
        val context = currentCoroutineContext()
        if (context[OperationResourcesContext]?.session !== session) return run(session, block)

        context.ensureActive()
        session.throwIfCancelled()
        session.budget.throwIfExpired()
        val result = OperationContext(CoroutineScope(context), session).block()
        ensureCurrentOperationActive()
        return result
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun <T> run(
        session: OperationSession,
        block: suspend OperationContext.() -> T,
    ): T {
        checkNotNull(currentCoroutineContext()[Job])
        return coroutineScope {
            val parentJob = checkNotNull(currentCoroutineContext()[Job])
            val operationJob = Job(parentJob)
            try {
                session.attach(operationJob)
            } catch (failure: Throwable) {
                operationJob.cancel()
                throw failure
            }
            val eagerCloseFailure = AtomicReference<Throwable?>()
            val cancellationCloseHandle = operationJob.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true,
            ) { cause ->
                if (cause != null) {
                    when (cause) {
                        is OperationCancellationException -> session.recordCancellationReason(cause.reason)
                        is OperationDeadlineExceededException -> {
                            session.recordCancellationReason(CancellationReason.DEADLINE_EXCEEDED)
                        }
                        is kotlinx.coroutines.CancellationException -> {
                            session.recordCancellationReason(CancellationReason.PARENT_CANCELLED)
                        }
                    }
                    // Job cancellation handlers run synchronously on the cancellation caller.
                    // Start blocking closes on the fixed bounded cleanup executor instead.
                    session.closeResourcesAsync().whenComplete { _, closeFailure ->
                        if (closeFailure != null) {
                            // Keep ResourceScopeCloseException intact; its cause is the first
                            // close failure and generic unwrapping would discard the aggregate.
                            eagerCloseFailure.compareAndSet(null, closeFailure)
                            preserveCleanupFailure(cause, closeFailure)
                        }
                    }
                }
            }
            val deadlineWatcher = launch {
                awaitDeadline(session.budget)
                val deadlineFailure = completeDeadline(session, operationJob) ?: return@launch
                throw deadlineFailure
            }

            var primaryFailure: Throwable? = null
            var surfacedCleanupFailure: Throwable? = null
            var result: T? = null
            try {
                result =
                    withContext(operationJob + OperationResourcesContext(session)) {
                        currentCoroutineContext().ensureActive()
                        session.budget.throwIfExpired()
                        OperationContext(this, session).block()
                    }.also {
                        session.budget.throwIfExpired()
                    }
            } catch (failure: Throwable) {
                val surfacedFailure = when (failure) {
                    is OperationDeadlineExceededException -> {
                        val winningReason = session.recordCancellationReason(CancellationReason.DEADLINE_EXCEEDED)
                        if (winningReason == CancellationReason.DEADLINE_EXCEEDED) failure else {
                            OperationCancellationException(winningReason, failure)
                        }
                    }
                    is OperationCancellationException -> {
                        val winningReason = session.recordCancellationReason(failure.reason)
                        winningReason.toTerminalFailure(failure)
                    }
                    is kotlinx.coroutines.CancellationException -> {
                        val winningReason = session.recordCancellationReason(CancellationReason.PARENT_CANCELLED)
                        winningReason.toTerminalFailure(failure)
                    }
                    else -> {
                        val winningReason = session.cancellationReason
                        if (winningReason == null || failure is Error) {
                            failure
                        } else {
                            winningReason.toTerminalFailure(failure)
                        }
                    }
                }
                primaryFailure = surfacedFailure
            } finally {
                deadlineWatcher.cancel()
                cancellationCloseHandle.dispose()
                val closeFailure = withContext(NonCancellable + Dispatchers.IO) {
                    OperationCleanupExecutor.await(session.closeResourcesAsync())
                }
                val observedCloseFailure = eagerCloseFailure.get() ?: closeFailure
                if (operationJob.isActive) operationJob.complete()
                session.finish(operationJob)

                surfacedCleanupFailure = cleanupFailureToSurface(primaryFailure, observedCloseFailure)
            }
            // Rethrow after cleanup rather than from finally, so the surfaced failure is explicit.
            (surfacedCleanupFailure ?: primaryFailure)?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            result as T
        }
    }

    /**
     * Picks the failure that must replace or stand in for the operation outcome after cleanup.
     * An [Error] always wins; any other close failure surfaces only when the operation itself
     * succeeded, and is otherwise attached to [primary] as a suppressed failure.
     */
    private fun cleanupFailureToSurface(
        primary: Throwable?,
        closeFailure: Throwable?,
    ): Throwable? =
        when {
            closeFailure == null -> null
            closeFailure is Error -> closeFailure.also { attachPrimary(it, primary) }
            primary == null -> closeFailure
            else -> null.also { preserveCleanupFailure(primary, closeFailure) }
        }

    private fun attachPrimary(
        closeFailure: Error,
        primary: Throwable?,
    ) {
        if (primary != null && primary !== closeFailure && closeFailure.suppressed.none { it === primary }) {
            closeFailure.addSuppressed(primary)
        }
    }

    /** Returns a deadline failure only when deadline cancellation wins the session's first reason. */
    internal suspend fun completeDeadline(
        session: OperationSession,
        operationJob: Job,
    ): OperationDeadlineExceededException? = withContext(NonCancellable + Dispatchers.IO) {
        val winningReason = session.recordCancellationReason(CancellationReason.DEADLINE_EXCEEDED)
        if (winningReason != CancellationReason.DEADLINE_EXCEEDED) return@withContext null
        operationJob.cancel(
            OperationCancellationException(CancellationReason.DEADLINE_EXCEEDED)
        )

        val deadlineFailure = OperationDeadlineExceededException()
        OperationCleanupExecutor.await(session.closeResourcesAsync())?.let(deadlineFailure::addSuppressed)
        deadlineFailure
    }

    private suspend fun awaitDeadline(budget: OperationBudget) {
        if (!budget.hasDeadline) {
            awaitCancellation()
        }
        while (true) {
            val remainingNanos = budget.remainingNanos()
            if (remainingNanos == 0L) return
            delay(budget.remainingTimeoutMillis().coerceAtLeast(1L))
        }
    }

    private fun preserveCleanupFailure(primary: Throwable, cleanupFailure: Throwable) {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var deepest = primary
        seen += deepest
        while (true) {
            val cause = deepest.cause ?: break
            if (!seen.add(cause)) break
            deepest = cause
        }
        val attachmentPoint = deepest
        if (attachmentPoint !== cleanupFailure &&
            attachmentPoint.suppressed.none { it === cleanupFailure }
        ) attachmentPoint.addSuppressed(cleanupFailure)
    }

    private fun CancellationReason.toTerminalFailure(cause: Throwable): Throwable =
        if (this == CancellationReason.DEADLINE_EXCEEDED) {
            OperationDeadlineExceededException().also { it.initCause(cause) }
        } else {
            OperationCancellationException(this, cause)
        }
}
