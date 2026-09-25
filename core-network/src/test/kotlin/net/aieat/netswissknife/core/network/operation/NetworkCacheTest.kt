package net.aieat.netswissknife.core.network.operation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.testkit.FakeClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkCacheTest {

    @Test
    fun `fresh is default and bypasses existing entries without replacing them`() = runTest {
        val cache = newCache()
        val key = key("host")
        cache.getOrLoad(key, NetworkCacheMode.UseCached) { "cached" }

        val fresh = cache.getOrLoad(key) { "current" }
        val cached = cache.getOrLoad(key, NetworkCacheMode.UseCached) { "unexpected" }

        assertEquals(NetworkCacheResult.Found("current", NetworkCacheSource.FRESH_LOAD), fresh)
        assertEquals(NetworkCacheResult.Found("cached", NetworkCacheSource.CACHE_HIT, 0L), cached)
    }

    @Test
    fun `fresh load propagates cancellation observed after a non suspending loader returns`() = runTest {
        val cache = newCache()
        val outcome = CompletableDeferred<NetworkCacheResult<String>>()
        val job = launch {
            try {
                outcome.complete(cache.getOrLoad(key("host")) {
                    currentCoroutineContext()[Job]?.cancel()
                    "late-result"
                })
            } catch (cancelled: CancellationException) {
                outcome.completeExceptionally(cancelled)
            }
        }

        job.join()

        assertTrue(job.isCancelled)
        assertTrue(outcome.isCancelled)
        assertEquals(0, cache.size)
    }

    @Test
    fun `route and scope are part of cache identity`() = runTest {
        val cache = newCache()
        val base = key("host")
        val alternateRoute = base.copy(route = "wifi-2")
        val alternateScope = base.copy(scope = "geoip-v2")

        assertEquals("one", (cache.getOrLoad(base, NetworkCacheMode.UseCached) { "one" } as NetworkCacheResult.Found).value)
        assertEquals("two", (cache.getOrLoad(alternateRoute, NetworkCacheMode.UseCached) { "two" } as NetworkCacheResult.Found).value)
        assertEquals("three", (cache.getOrLoad(alternateScope, NetworkCacheMode.UseCached) { "three" } as NetworkCacheResult.Found).value)
        assertEquals(3, cache.size)
    }

    @Test
    fun `positive and negative values expire at their separate TTL boundaries`() = runTest {
        val clock = FakeClock()
        val cache = newCache(clock = clock)
        val positiveKey = key("positive")
        val negativeKey = key("negative")
        var positiveLoads = 0
        var negativeLoads = 0

        cache.getOrLoad(positiveKey, NetworkCacheMode.UseCached) { "answer-${++positiveLoads}" }
        cache.getOrLoad(negativeKey, NetworkCacheMode.UseCached) { ++negativeLoads; null }
        clock.advanceBy(30_000_000_000L)
        val positiveHit = cache.getOrLoad(positiveKey, NetworkCacheMode.UseCached) { "answer-${++positiveLoads}" }
        val negativeExpired = cache.getOrLoad(negativeKey, NetworkCacheMode.UseCached) { ++negativeLoads; null }

        assertEquals(NetworkCacheSource.CACHE_HIT, positiveHit.source)
        assertEquals(30_000L, positiveHit.ageMillis)
        assertInstanceOf(NetworkCacheResult.Missing::class.java, negativeExpired)
        assertEquals(2, negativeLoads)

        clock.advanceBy(270_000_000_000L)
        val positiveExpired = cache.getOrLoad(positiveKey, NetworkCacheMode.UseCached) { "answer-${++positiveLoads}" }
        assertEquals(NetworkCacheSource.FRESH_LOAD, positiveExpired.source)
        assertEquals(2, positiveLoads)
    }

    @Test
    fun `least recently used entry is evicted and size stays bounded`() = runTest {
        val cache = NetworkCache<String, String>(maxEntries = 2)
        val a = key("a")
        val b = key("b")
        val c = key("c")
        cache.getOrLoad(a, NetworkCacheMode.UseCached) { "A" }
        cache.getOrLoad(b, NetworkCacheMode.UseCached) { "B" }
        cache.getOrLoad(a, NetworkCacheMode.UseCached) { "wrong" } // Touch A; B is now eldest.
        cache.getOrLoad(c, NetworkCacheMode.UseCached) { "C" }

        assertEquals(2, cache.size)
        var bLoaded = false
        cache.getOrLoad(b, NetworkCacheMode.UseCached) { bLoaded = true; "B2" }
        assertTrue(bLoaded)
        assertEquals(2, cache.size)
    }

    @Test
    fun `clear drops entries and prevents a running load from repopulating`() = runTest {
        val cache = newCache()
        val key = key("host")
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<String>()
        val loading = async {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) {
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()
        cache.clear()
        finish.complete("old")
        loading.await()

        assertEquals(0, cache.size)
        val afterClear = cache.getOrLoad(key, NetworkCacheMode.UseCached) { "new" }
        assertEquals("new", (afterClear as NetworkCacheResult.Found).value)
    }

    @Test
    fun `concurrent opted in loads share one provider call`() = runTest {
        val cache = newCache()
        val key = key("host")
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<String>()
        var calls = 0
        val first = async {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) {
                calls += 1
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) { calls += 1; "wrong" }
        }
        finish.complete("answer")

        assertEquals("answer", (first.await() as NetworkCacheResult.Found).value)
        val joined = second.await()
        assertEquals("answer", (joined as NetworkCacheResult.Found).value)
        assertEquals(NetworkCacheSource.IN_FLIGHT, joined.source)
        assertEquals(1, calls)
    }

    @Test
    fun `provider errors are not cached and a later call retries`() = runTest {
        val cache = newCache()
        val key = key("host")
        val failure = IllegalStateException("provider failed")
        var caught: Throwable? = null
        try {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) { throw failure }
        } catch (error: Throwable) {
            caught = error
        }
        assertTrue(caught === failure)
        assertEquals(0, cache.size)
        assertEquals("recovered", (cache.getOrLoad(key, NetworkCacheMode.UseCached) { "recovered" } as NetworkCacheResult.Found).value)
    }

    @Test
    fun `leader cancellation releases flight and is not cached`() = runTest {
        val cache = newCache()
        val key = key("host")
        val started = CompletableDeferred<Unit>()
        val leader = async {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) { "wrong" }
        }

        leader.cancelAndJoin()
        var waiterCancelled = false
        try {
            waiter.await()
        } catch (_: CancellationException) {
            waiterCancelled = true
        }
        assertTrue(waiterCancelled)
        assertEquals(0, cache.size)
        assertEquals("retry", (cache.getOrLoad(key, NetworkCacheMode.UseCached) { "retry" } as NetworkCacheResult.Found).value)
    }

    @Test
    fun `cancelling a waiter does not cancel the shared provider call`() = runTest {
        val cache = newCache()
        val key = key("host")
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<String>()
        var calls = 0
        val leader = async {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) {
                calls += 1
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad(key, NetworkCacheMode.UseCached) { calls += 1; "wrong" }
        }
        waiter.cancelAndJoin()
        finish.complete("answer")

        assertEquals("answer", (leader.await() as NetworkCacheResult.Found).value)
        assertEquals(1, calls)
        assertEquals("answer", (cache.getOrLoad(key, NetworkCacheMode.UseCached) { "wrong" } as NetworkCacheResult.Found).value)
    }

    @Test
    fun `cache capacity cannot exceed the documented default maximum`() = runTest {
        assertEquals(256, NetworkCache.DEFAULT_MAX_ENTRIES)
        assertThrows(IllegalArgumentException::class.java) { NetworkCache<String, String>(maxEntries = 257) }
        assertThrows(IllegalArgumentException::class.java) { NetworkCache<String, String>(maxEntries = 0) }

        val cache = newCache()
        repeat(300) { index ->
            cache.getOrLoad(key("host-$index"), NetworkCacheMode.UseCached) { "value-$index" }
        }
        assertEquals(256, cache.size)
    }

    private fun key(value: String) = NetworkCacheKey(value, route = "wifi-1", scope = "geoip")

    private fun newCache(clock: FakeClock = FakeClock()) = NetworkCache<String, String>(
        positiveTtlMillis = 300_000,
        negativeTtlMillis = 30_000,
        clock = clock,
    )
}
