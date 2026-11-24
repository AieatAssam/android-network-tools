package net.aieat.netswissknife.app.ui.screens.speedtest

import net.aieat.netswissknife.core.network.speedtest.LatencySample
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedTestShareTextTest {
    private val throughput = ThroughputResult.from(1000, 100, listOf(ThroughputSample(100, 1000, 8.0)))

    private fun result(loadedDown: LatencyStats, loadedUp: LatencyStats) = SpeedTestResult(
        latency = LatencyStats.compute(listOf(LatencySample(1, 20))),
        download = throughput,
        upload = throughput,
        loadedLatencyDown = loadedDown,
        loadedLatencyUp = loadedUp,
    )

    @Test
    fun `loaded latency rows are left out when no samples were taken`() {
        val share = buildSpeedTestShareText(result(LatencyStats.EMPTY, LatencyStats.EMPTY))

        assertFalse(share.contains("Latency under download load"))
        assertFalse(share.contains("Latency under upload load"))
    }

    @Test
    fun `loaded latency rows are listed when samples exist`() {
        val share = buildSpeedTestShareText(
            result(
                LatencyStats.compute(listOf(LatencySample(1, 28))),
                LatencyStats.compute(listOf(LatencySample(1, 31))),
            ),
        )

        assertTrue(share.contains("Latency under download load: 28.0 ms avg / 1 samples"))
        assertTrue(share.contains("Latency under upload load: 31.0 ms avg / 1 samples"))
    }

    @Test
    fun `the header separates the product name from the title with a colon`() {
        val share = buildSpeedTestShareText(result(LatencyStats.EMPTY, LatencyStats.EMPTY))

        assertTrue(share.startsWith("Net Swiss Knife: Speed Test Results\n"))
    }
}
