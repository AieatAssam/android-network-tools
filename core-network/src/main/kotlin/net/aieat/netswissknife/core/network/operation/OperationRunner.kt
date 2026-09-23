package net.aieat.netswissknife.core.network.operation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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

/** Runs one operation with structured children, a monotonic deadline, and prompt resource closure. */
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
        val callerJob = checkNotNull(currentCoroutineContext()[Job])
        return coroutineScope {
            val parentJob = checkNotNull(currentCoroutineContext()[Job])
            val operationJob = Job(parentJob)
            try {
                session.attach(operationJob, callerJob)
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
                    // Job cancellation handlers run synchronously on the thread that requested
                    // cancellation. Resource close methods may block (for example, while a
                    // native transport is shutting down), so start eager cleanup on an
                    // independent process-lifetime IO scope instead of blocking that caller.
                    OperationCleanupScope.scope.launch {
                        val closeFailure = runCatching { session.resources.close() }.exceptionOrNull()
                        if (closeFailure != null) {
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
            try {
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
                    else -> failure
                }
                primaryFailure = surfacedFailure
                throw surfacedFailure
            } finally {
                deadlineWatcher.cancel()
                cancellationCloseHandle.dispose()
                val closeFailure = withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        session.resources.close()
                        null
                    } catch (failure: Throwable) {
                        failure
                    }
                }
                val observedCloseFailure = eagerCloseFailure.get() ?: closeFailure
                if (operationJob.isActive) operationJob.complete()
                session.finish(operationJob)

                if (observedCloseFailure != null) {
                    val primary = primaryFailure
                    if (observedCloseFailure is Error) {
                        if (primary != null && primary !== observedCloseFailure &&
                            observedCloseFailure.suppressed.none { it === primary }
                        ) observedCloseFailure.addSuppressed(primary)
                        throw observedCloseFailure
                    }
                    if (primary == null) throw observedCloseFailure
                    preserveCleanupFailure(primary, observedCloseFailure)
                }
            }
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
        try {
            session.resources.close()
        } catch (closeFailure: Throwable) {
            if (closeFailure is Error) {
                closeFailure.addSuppressed(deadlineFailure)
                throw closeFailure
            }
            deadlineFailure.addSuppressed(closeFailure)
        }
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

/** Eager cancellation cleanup must outlive the cancelling operation and its caller. */
private object OperationCleanupScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
