package net.aieat.netswissknife.core.network.whois

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Fresh-JVM diagnostic for the observable first Public Suffix lookup.
 *
 * Run alone with:
 * `./gradlew :core-network:test --tests '*PublicSuffixColdStartBenchmarkTest' --no-daemon --no-parallel --max-workers=1 --console=plain`
 * Gradle's test worker is fresh for this isolated test task. The numeric sample
 * is emitted to the test XML's system-out so it can be compared across builds.
 */
class PublicSuffixColdStartBenchmarkTest {
    @Test
    fun `records first ASCII lookup elapsed time`() {
        val workerPid = ProcessHandle.current().pid()
        val startNanos = System.nanoTime()
        val result = PublicSuffix.registrableDomain("foo.bar.unknown-tld")
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0

        assertEquals("bar.unknown-tld", result)
        println("PUBLIC_SUFFIX_TEST_PID=$workerPid")
        println("PUBLIC_SUFFIX_FIRST_CALL_MS=$elapsedMillis")
    }
}
