package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingEngineKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ping display formatting")
class PingDisplayFormatTest {
    @Test
    fun `zero loss is shown as zero percent`() {
        assertEquals("0%", pingLossLabel(0f))
    }

    @Test
    fun `small non-zero loss is never rounded down to zero`() {
        assertEquals("<0.1%", pingLossLabel(0.04f))
        assertEquals("0.4%", pingLossLabel(0.4f))
    }

    @Test
    fun `whole and fractional loss keep their precision`() {
        assertEquals("50%", pingLossLabel(50f))
        assertEquals("12.5%", pingLossLabel(12.5f))
    }

    @Test
    fun `sub-millisecond axis values are labelled as below one millisecond`() {
        assertEquals("<1 ms", pingAxisLabel(0.4f, "ms"))
        assertEquals("0 ms", pingAxisLabel(0f, "ms"))
    }

    @Test
    fun `axis values are whole numbers`() {
        assertEquals("134 ms", pingAxisLabel(133.6f, "ms"))
        assertEquals("1 ms", pingAxisLabel(1f, "ms"))
    }

    @Test
    fun `timeout is labelled in seconds, with one decimal for tenths, otherwise in milliseconds`() {
        assertEquals("1 s", pingTimeoutLabel(1_000))
        assertEquals("30 s", pingTimeoutLabel(30_000))
        assertEquals("1.5 s", pingTimeoutLabel(1_500))
        assertEquals("5.5 s", pingTimeoutLabel(5_500))
        assertEquals("1550 ms", pingTimeoutLabel(1_550))
    }

    @Test
    fun `the start hint shows only for an empty host`() {
        assertEquals(true, pingStartHintVisible(""))
        assertEquals(true, pingStartHintVisible("   "))
        assertEquals(false, pingStartHintVisible("a".repeat(300)))
        assertEquals(false, pingStartHintVisible("http://x"))
    }

    @Test
    fun `timeout options contain the presets`() {
        assertEquals(listOf(1_000, 2_000, 3_000, 5_000, 10_000), pingTimeoutOptions(2_000))
    }

    @Test
    fun `the preset chips stay the same whichever timeout is selected`() {
        val presets = listOf(1_000, 2_000, 3_000, 5_000, 10_000)
        presets.forEach { assertEquals(presets, pingTimeoutOptions(it)) }
    }

    @Test
    fun `a timeout that is not a preset is still offered and kept in order`() {
        assertEquals(listOf(1_000, 1_500, 2_000, 3_000, 5_000, 10_000), pingTimeoutOptions(1_500))
    }

    @Test
    @DisplayName("ICMP raw header reports payload size, TTL and engine")
    fun icmpHeaderReportsPayloadAndTtl() {
        assertEquals(
            "PING example.com (93.184.216.34): 56 data bytes, ttl 64, engine ICMP",
            pingRawHeader("example.com", "93.184.216.34", 56, 64, PingEngineKind.ICMP),
        )
    }

    @Test
    @DisplayName("Reachability raw header omits payload size and TTL")
    fun reachabilityHeaderOmitsPayloadAndTtl() {
        assertEquals(
            "PING 10.0.0.1: engine Reachability (ICMP/TCP fallback), payload size and TTL not applied",
            pingRawHeader("10.0.0.1", null, 56, 64, PingEngineKind.REACHABILITY),
        )
    }

    @Test
    @DisplayName("Bar fraction scales to the list maximum")
    fun barFractionScalesToMaximum() {
        assertEquals(0.5f, pingBarFraction(50L, 100L))
        assertEquals(1f, pingBarFraction(100L, 100L))
        assertEquals(0f, pingBarFraction(0L, 0L))
    }
}
