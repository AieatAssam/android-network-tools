package net.aieat.netswissknife.app.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TraceLatencyBand")
class TraceLatencyBandTest {
    @Test
    fun `no RTT is unknown`() {
        assertEquals(TraceLatencyBand.UNKNOWN, TraceLatencyBand.of(null))
    }

    @Test
    fun `thresholds sit at 50, 150 and 300 ms`() {
        assertEquals(TraceLatencyBand.GOOD, TraceLatencyBand.of(0))
        assertEquals(TraceLatencyBand.GOOD, TraceLatencyBand.of(49))
        assertEquals(TraceLatencyBand.FAIR, TraceLatencyBand.of(50))
        assertEquals(TraceLatencyBand.FAIR, TraceLatencyBand.of(149))
        assertEquals(TraceLatencyBand.SLOW, TraceLatencyBand.of(150))
        assertEquals(TraceLatencyBand.SLOW, TraceLatencyBand.of(299))
        assertEquals(TraceLatencyBand.CRITICAL, TraceLatencyBand.of(300))
        assertEquals(TraceLatencyBand.CRITICAL, TraceLatencyBand.of(5_000))
    }

    @Test
    fun `each band's lower bound falls in that band, so legend swatches match the hop list`() {
        TraceLatencyBand.entries.forEach { band ->
            assertEquals(band, TraceLatencyBand.of(band.lowerBoundMs))
        }
    }

    @Test
    fun `measured bands are contiguous`() {
        assertEquals(TraceLatencyBand.FAIR.lowerBoundMs, TraceLatencyBand.GOOD.upperBoundMs)
        assertEquals(TraceLatencyBand.SLOW.lowerBoundMs, TraceLatencyBand.FAIR.upperBoundMs)
        assertEquals(TraceLatencyBand.CRITICAL.lowerBoundMs, TraceLatencyBand.SLOW.upperBoundMs)
        assertEquals(null, TraceLatencyBand.CRITICAL.upperBoundMs)
    }
}
