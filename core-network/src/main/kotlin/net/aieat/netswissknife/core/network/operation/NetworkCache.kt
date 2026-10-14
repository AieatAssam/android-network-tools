package net.aieat.netswissknife.core.network.operation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import java.util.LinkedHashMap
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import kotlin.coroutines.coroutineContext

/** A value is isolated by both the current route and the feature-specific scope. */
data class NetworkCacheKey<K : Any>(
    val value: K,
    val route: String,
    val scope: String,
)

/** Fresh is the safe default for diagnostic probes; cache use must be requested explicitly. */
enum class NetworkCacheMode {
    Fresh,
    UseCached,
}

enum class NetworkCacheSource {
    FRESH_LOAD,
    CACHE_HIT,
    IN_FLIGHT,
}

/** A null provider result is represented as [Missing] and can use the shorter negative TTL. */
sealed interface NetworkCacheResult<out V : Any> {
    val source: NetworkCacheSource
    val ageMillis: Long?

    data class Found<V : Any>(
        val value: V,
        override val source: NetworkCacheSource,
        override val ageMillis: Long? = null,
    ) : NetworkCacheResult<V>

    data class Missing(
        override val source: NetworkCacheSource,
        override val ageMillis: Long? = null,
    ) : NetworkCacheResult<Nothing>
}

/**
 * Small, process-local cache for explicitly approved read-only network metadata.
 *
 * Fresh loads bypass both cached values and in-flight work. Use [NetworkCacheMode.UseCached]
 * only at call sites that are allowed to reuse a value. In-flight work is shared only for
 * those opted-in calls. A failed or cancelled load is never retained.
 */
class NetworkCache<K : Any, V : Any>(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    positiveTtlMillis: Long = DEFAULT_POSITIVE_TTL_MILLIS,
    negativeTtlMillis: Long = DEFAULT_NEGATIVE_TTL_MILLIS,
    private val clock: MonotonicClock = SystemMonotonicClock,
) {
    private val positiveTtlNanos = ttlNanos(positiveTtlMillis)
    private val negativeTtlNanos = ttlNanos(negativeTtlMillis)
    private val lock = Any()
    private val entries = LinkedHashMap<NetworkCacheKey<K>, CacheEntry<V>>(16, 0.75f, true)
    private val inFlight = mutableMapOf<NetworkCacheKey<K>, CompletableDeferred<CacheValue<V>>>()
    private var clearGeneration = 0L

    init {
        require(maxEntries in 1..DEFAULT_MAX_ENTRIES) {
            "maxEntries must be between 1 and $DEFAULT_MAX_ENTRIES"
        }
    }

    val size: Int
        get() = synchronized(lock) { entries.size }

    /**
     * Returns a fresh provider result by default. In cached mode, positive and negative results
     * are retained with their respective TTLs and concurrent identical lookups share one load.
     */
    suspend fun getOrLoad(
        key: NetworkCacheKey<K>,
        mode: NetworkCacheMode = NetworkCacheMode.Fresh,
        loader: suspend () -> V?,
    ): NetworkCacheResult<V> {
        if (mode == NetworkCacheMode.Fresh) {
            val value = loader()
            coroutineContext.ensureActive()
            return loadedResult(value)
        }

        var leader = false
        var generation = 0L
        val flight: CompletableDeferred<CacheValue<V>>
        synchronized(lock) {
            val now = clock.nowNanos()
            val cached = entries[key]
            if (cached != null) {
                val age = elapsedNanos(now, cached.createdAtNanos)
                if (age < cached.ttlNanos) {
                    return cached.value.toResult(
                        NetworkCacheSource.CACHE_HIT,
                        ageMillis = age / NANOS_PER_MILLISECOND,
                    )
                }
                entries.remove(key)
            }

            val existingFlight = inFlight[key]
            if (existingFlight != null) {
                flight = existingFlight
            } else {
                flight = CompletableDeferred()
                inFlight[key] = flight
                generation = clearGeneration
                leader = true
            }
        }

        if (!leader) {
            return flight.await().toResult(NetworkCacheSource.IN_FLIGHT, ageMillis = 0L)
        }

        try {
            val value = loader()
            coroutineContext.ensureActive()
            val cacheValue = CacheValue(value)
            synchronized(lock) {
                if (inFlight[key] === flight) {
                    if (generation == clearGeneration) {
                        val now = clock.nowNanos()
                        val ttl = if (value == null) negativeTtlNanos else positiveTtlNanos
                        entries[key] = CacheEntry(cacheValue, now, ttl)
                        evictOverflow()
                    }
                    inFlight.remove(key)
                }
            }
            flight.complete(cacheValue)
            return cacheValue.toResult(NetworkCacheSource.FRESH_LOAD, ageMillis = null)
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (inFlight[key] === flight) inFlight.remove(key)
            }
            flight.completeExceptionally(failure)
            throw failure
        }
    }

    /** Clears retained values and detaches current flights so they cannot repopulate the cache. */
    fun clear() {
        synchronized(lock) {
            clearGeneration += 1L
            entries.clear()
            inFlight.clear()
        }
    }

    private fun evictOverflow() {
        while (entries.size > maxEntries) {
            val eldest = entries.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun loadedResult(value: V?): NetworkCacheResult<V> =
        CacheValue(value).toResult(NetworkCacheSource.FRESH_LOAD, ageMillis = null)

    private fun CacheValue<V>.toResult(
        source: NetworkCacheSource,
        ageMillis: Long?,
    ): NetworkCacheResult<V> = value?.let {
        NetworkCacheResult.Found(it, source, ageMillis)
    } ?: NetworkCacheResult.Missing(source, ageMillis)

    private data class CacheValue<V : Any>(val value: V?)
    private data class CacheEntry<V : Any>(
        val value: CacheValue<V>,
        val createdAtNanos: Long,
        val ttlNanos: Long,
    )

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256
        const val DEFAULT_POSITIVE_TTL_MILLIS = 300_000L
        const val DEFAULT_NEGATIVE_TTL_MILLIS = 30_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        private fun ttlNanos(millis: Long): Long {
            require(millis > 0L) { "TTL must be positive" }
            return if (millis > Long.MAX_VALUE / NANOS_PER_MILLISECOND) {
                Long.MAX_VALUE
            } else {
                millis * NANOS_PER_MILLISECOND
            }
        }

        private fun elapsedNanos(now: Long, then: Long): Long = (now - then).coerceAtLeast(0L)
    }
}
