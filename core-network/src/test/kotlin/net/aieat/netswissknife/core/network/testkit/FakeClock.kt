package net.aieat.netswissknife.core.network.testkit

import net.aieat.netswissknife.core.network.MonotonicClock
import java.util.concurrent.atomic.AtomicLong

/** A controllable monotonic clock for deterministic network-operation tests. */
class FakeClock(initialNanos: Long = 0L) : MonotonicClock {
    private val currentNanos = AtomicLong(initialNanos)

    override fun nowNanos(): Long = currentNanos.get()

    /** Advances by a non-negative amount, with the same wrapping behavior as `System.nanoTime()`. */
    fun advanceBy(deltaNanos: Long): Long {
        require(deltaNanos >= 0L) { "Clock cannot advance by a negative duration" }
        return currentNanos.addAndGet(deltaNanos)
    }

    /** Sets the clock directly, including backwards movement for defensive-clock tests. */
    fun setNow(valueNanos: Long) {
        currentNanos.set(valueNanos)
    }
}
