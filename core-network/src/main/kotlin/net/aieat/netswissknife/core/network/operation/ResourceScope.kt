package net.aieat.netswissknife.core.network.operation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owns operation resources and closes them once, in reverse registration order. */
class ResourceScope : AutoCloseable {
    private companion object {
        const val CONCURRENT_CLOSE_WAIT_TIMEOUT_MILLIS = 1_000L
    }

    private enum class State { OPEN, CLOSING, CLOSED }

    private val lock = Any()
    private val resources = mutableListOf<AutoCloseable>()
    private var state = State.OPEN
    private var closingThread: Thread? = null
    private var closeFinished = CountDownLatch(0)
    private var closeFailure: Throwable? = null

    val isClosed: Boolean
        get() = synchronized(lock) { state == State.CLOSED }

    /** Registers a resource or closes it immediately if the scope has started closing. */
    fun <T : AutoCloseable> register(resource: T): T {
        val registered = synchronized(lock) {
            if (state == State.OPEN) {
                resources += resource
                true
            } else {
                false
            }
        }
        if (!registered) {
            val closeFailure = try {
                resource.close()
                null
            } catch (failure: Throwable) {
                if (failure is Error) throw failure
                failure
            }
            throw ResourceScopeClosedException(closeFailure)
        }
        return resource
    }

    /**
     * Transfers a previously registered resource back to its caller while the scope is open.
     * Returns false once scope closure owns cleanup; the caller must not close it in that case.
     */
    fun release(resource: AutoCloseable): Boolean = synchronized(lock) {
        if (state != State.OPEN) return@synchronized false
        val index = resources.indexOfFirst { it === resource }
        if (index < 0) false else {
            resources.removeAt(index)
            true
        }
    }

    /**
     * Closes every owned resource in LIFO order. Concurrent callers wait up to one second for
     * the owner; this bounds cross-thread re-entry cycles. If a close action fails, all other
     * actions still run and the failures are reported together.
     */
    override fun close() {
        var ownedResources: List<AutoCloseable>? = null
        var waitForClose: CountDownLatch? = null

        synchronized(lock) {
            when (state) {
                State.OPEN -> {
                    state = State.CLOSING
                    closingThread = Thread.currentThread()
                    closeFinished = CountDownLatch(1)
                    ownedResources = resources.asReversed().toList()
                    resources.clear()
                }
                State.CLOSING -> {
                    // A resource may re-enter close while being closed on this thread.
                    if (closingThread === Thread.currentThread()) return
                    waitForClose = closeFinished
                }
                State.CLOSED -> {
                    closeFailure?.let { throw it }
                    return
                }
            }
        }

        val toClose = ownedResources
        if (toClose != null) {
            val failures = mutableListOf<Throwable>()
            var fatalFailure: Error? = null
            toClose.forEach { resource ->
                try {
                    resource.close()
                } catch (failure: Throwable) {
                    if (failure is Error) {
                        val firstFatal = fatalFailure
                        if (firstFatal == null) {
                            fatalFailure = failure
                        } else if (firstFatal !== failure) {
                            firstFatal.addSuppressed(failure)
                        }
                    } else {
                        failures += failure
                    }
                }
            }
            val aggregate = fatalFailure?.also { fatal ->
                failures.forEach(fatal::addSuppressed)
            } ?: failures.takeIf { it.isNotEmpty() }?.let(::ResourceScopeCloseException)
            synchronized(lock) {
                closeFailure = aggregate
                closingThread = null
                state = State.CLOSED
                closeFinished.countDown()
            }
            aggregate?.let { throw it }
        } else {
            if (!awaitUninterruptibly(
                    checkNotNull(waitForClose),
                    CONCURRENT_CLOSE_WAIT_TIMEOUT_MILLIS,
                )
            ) {
                throw ResourceScopeCloseWaitTimeoutException(CONCURRENT_CLOSE_WAIT_TIMEOUT_MILLIS)
            }
            synchronized(lock) { closeFailure }?.let { throw it }
        }
    }

    private fun awaitUninterruptibly(
        latch: CountDownLatch,
        timeoutMillis: Long,
    ): Boolean {
        var interrupted = false
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var completed = false
        while (!completed) {
            val remainingNanos = (deadlineNanos - System.nanoTime()).coerceAtLeast(0L)
            if (remainingNanos == 0L) break
            try {
                completed = latch.await(remainingNanos, TimeUnit.NANOSECONDS)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
        return completed
    }
}

class ResourceScopeClosedException(cause: Throwable? = null) :
    IllegalStateException("Cannot register a resource after its scope has started closing", cause)

class ResourceScopeCloseWaitTimeoutException(timeoutMillis: Long) :
    IllegalStateException("Timed out after ${timeoutMillis}ms waiting for resource-scope closure")

class ResourceScopeCloseException internal constructor(failures: List<Throwable>) :
    Exception("Failed to close ${failures.size} operation resource(s)", failures.firstOrNull()) {
    val failures: List<Throwable> = failures.toList()

    init {
        failures.drop(1).forEach(::addSuppressed)
    }
}
