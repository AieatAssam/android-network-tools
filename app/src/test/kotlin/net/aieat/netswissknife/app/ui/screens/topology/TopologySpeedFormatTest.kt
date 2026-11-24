package net.aieat.netswissknife.app.ui.screens.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class TopologySpeedFormatTest {
    @Test
    fun `whole megabit and gigabit speeds drop the decimal`() {
        assertEquals("10 Mbps", formatSpeed(10_000_000L, Locale.US))
        assertEquals("100 Mbps", formatSpeed(100_000_000L, Locale.US))
        assertEquals("1 Gbps", formatSpeed(1_000_000_000L, Locale.US))
        assertEquals("10 Gbps", formatSpeed(10_000_000_000L, Locale.US))
    }

    @Test
    fun `fractional speeds keep one decimal instead of truncating`() {
        assertEquals("2.5 Gbps", formatSpeed(2_500_000_000L, Locale.US))
        assertEquals("1.5 Mbps", formatSpeed(1_500_000L, Locale.US))
    }

    @Test
    fun `values that round up to a whole number drop the decimal`() {
        assertEquals("2 Gbps", formatSpeed(1_960_000_000L, Locale.US))
    }

    @Test
    fun `small values use the lower units`() {
        assertEquals("0 bps", formatSpeed(0L, Locale.US))
        assertEquals("999 bps", formatSpeed(999L, Locale.US))
        assertEquals("1 Kbps", formatSpeed(1_000L, Locale.US))
    }

    @Test
    fun `decimal separator follows the supplied locale`() {
        assertEquals("2,5 Gbps", formatSpeed(2_500_000_000L, Locale.GERMANY))
    }
}
