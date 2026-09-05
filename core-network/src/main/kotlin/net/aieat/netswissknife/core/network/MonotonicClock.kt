package net.aieat.netswissknife.core.network

/**
 * Source of elapsed time that is not affected by wall-clock corrections.
 *
 * Implementations are deliberately tiny so network repositories can use a
 * deterministic clock in tests without coupling themselves to a platform
 * clock or sleeping in the test suite.
 */
fun interface MonotonicClock {
    fun nowNanos(): Long
}

/** Production clock backed by the JVM's monotonic timer. */
object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

/** Converts a monotonic start instant into a non-negative elapsed duration. */
fun MonotonicClock.elapsedMillisSince(startNanos: Long): Long =
    ((nowNanos() - startNanos).coerceAtLeast(0L)) / NANOS_PER_MILLISECOND

private const val NANOS_PER_MILLISECOND = 1_000_000L
