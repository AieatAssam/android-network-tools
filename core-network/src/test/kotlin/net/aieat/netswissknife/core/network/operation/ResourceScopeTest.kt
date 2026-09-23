package net.aieat.netswissknife.core.network.operation

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourceScopeTest {
    @Test
    fun `closes closeable and autocloseable resources once in reverse registration order`() {
        val scope = ResourceScope()
        val closed = mutableListOf<String>()

        scope.register(Closeable { closed += "first" })
        scope.register(AutoCloseable { closed += "second" })
        scope.register(Closeable { closed += "third" })

        scope.close()
        scope.close()

        assertEquals(listOf("third", "second", "first"), closed)
        assertTrue(scope.isClosed)
    }

    @Test
    fun `close attempts every resource and reports all close failures`() {
        val scope = ResourceScope()
        val closed = mutableListOf<String>()
        val firstFailure = IllegalStateException("first close failed")
        val secondFailure = IllegalArgumentException("second close failed")
        scope.register(Closeable { closed += "first"; throw firstFailure })
        scope.register(AutoCloseable { closed += "second"; throw secondFailure })
        scope.register(Closeable { closed += "third" })

        val failure = assertThrows(ResourceScopeCloseException::class.java) { scope.close() }
        val repeatedFailure = assertThrows(ResourceScopeCloseException::class.java) { scope.close() }

        assertEquals(listOf("third", "second", "first"), closed)
        assertEquals(listOf(secondFailure, firstFailure), failure.failures)
        assertEquals(failure, repeatedFailure)
        assertTrue(scope.isClosed)
    }

    @Test
    fun `registering after close immediately closes the resource and fails`() {
        val scope = ResourceScope()
        scope.close()
        var closeCount = 0

        assertThrows(ResourceScopeClosedException::class.java) {
            scope.register(Closeable { closeCount++ })
        }

        assertEquals(1, closeCount)
    }

    @Test
    fun `release transfers ownership out of an open scope`() {
        val scope = ResourceScope()
        var closeCount = 0
        val resource = AutoCloseable { closeCount++ }
        scope.register(resource)

        assertTrue(scope.release(resource))
        resource.close()
        scope.close()

        assertEquals(1, closeCount)
    }

    @Test
    fun `release during closing leaves cleanup with the scope`() {
        val scope = ResourceScope()
        val closeStarted = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var closeCount = 0
        val resource = AutoCloseable {
            closeStarted.countDown()
            check(allowClose.await(5, TimeUnit.SECONDS))
            closeCount++
        }
        scope.register(resource)

        try {
            val closeTask = executor.submit { scope.close() }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertFalse(scope.release(resource))
            allowClose.countDown()
            closeTask.get(5, TimeUnit.SECONDS)

            assertEquals(1, closeCount)
        } finally {
            allowClose.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `registration during closing closes late resource immediately`() {
        val scope = ResourceScope()
        val closeStarted = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val lateCloseCount = AtomicInteger()
        scope.register(Closeable {
            closeStarted.countDown()
            check(allowClose.await(5, TimeUnit.SECONDS))
        })

        try {
            val closeTask = executor.submit { scope.close() }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))

            assertThrows(ResourceScopeClosedException::class.java) {
                scope.register(AutoCloseable { lateCloseCount.incrementAndGet() })
            }
            assertEquals(1, lateCloseCount.get())

            allowClose.countDown()
            closeTask.get(5, TimeUnit.SECONDS)
            assertEquals(1, lateCloseCount.get())
        } finally {
            allowClose.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `registration racing with close never leaks or double closes a resource`() {
        val scope = ResourceScope()
        val workers = 12
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val closed = AtomicInteger()
        val executor = Executors.newFixedThreadPool(workers)

        try {
            val tasks = (0 until workers).map {
                executor.submit {
                    val resource = Closeable { closed.incrementAndGet() }
                    ready.countDown()
                    start.await()
                    try {
                        scope.register(resource)
                    } catch (_: ResourceScopeClosedException) {
                        // A racing close owns and closes late registrations immediately.
                    }
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            scope.close()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            scope.close()

            assertEquals(workers, closed.get())
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `concurrent close callers wait for owned resources to finish closing`() {
        val scope = ResourceScope()
        val closeStarted = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondReturned = CountDownLatch(1)
        val secondThread = AtomicReference<Thread>()
        val executor = Executors.newFixedThreadPool(2)
        scope.register(Closeable {
            closeStarted.countDown()
            check(allowClose.await(5, TimeUnit.SECONDS))
        })

        try {
            val first = executor.submit { scope.close() }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            val second = executor.submit {
                secondThread.set(Thread.currentThread())
                secondStarted.countDown()
                scope.close()
                secondReturned.countDown()
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            awaitThreadState(secondThread, Thread.State.WAITING)
            assertEquals(1L, secondReturned.count)

            allowClose.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertTrue(secondReturned.count == 0L)
        } finally {
            allowClose.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `use preserves operation cancellation and suppresses close failure`() {
        val scope = ResourceScope()
        val cancellation = java.util.concurrent.CancellationException("user stopped")
        scope.register(Closeable { throw IllegalStateException("close failed") })

        val thrown = assertThrows(java.util.concurrent.CancellationException::class.java) {
            scope.use { throw cancellation }
        }

        assertEquals(cancellation, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertTrue(thrown.suppressed.single() is ResourceScopeCloseException)
    }

    @Test
    fun `fatal close error is rethrown after remaining resources are closed`() {
        val scope = ResourceScope()
        val closed = mutableListOf<String>()
        val fatal = UnsatisfiedLinkError("fatal close error")
        scope.register(Closeable { closed += "fatal"; throw fatal })
        scope.register(Closeable { closed += "last" })

        val thrown = assertThrows(UnsatisfiedLinkError::class.java) { scope.close() }

        assertEquals(listOf("last", "fatal"), closed)
        assertEquals(fatal, thrown)
        assertTrue(scope.isClosed)
    }

    private fun awaitThreadState(thread: AtomicReference<Thread>, expected: Thread.State) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            when (thread.get()?.state) {
                expected -> return
                Thread.State.TERMINATED -> error("Close returned before the active cleanup finished")
                else -> Thread.yield()
            }
        }
        error("Close caller did not reach $expected")
    }
}
