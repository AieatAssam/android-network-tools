package net.aieat.netswissknife.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatUtilsTest {
    @Test
    fun `formatBytesSi uses decimal units so 100 million bytes is 100 MB`() {
        assertEquals("100.0 MB", formatBytesSi(100_000_000L))
    }

    @Test
    fun `formatBytesSi keeps small sizes in bytes and steps up at 1000`() {
        assertEquals("0 B", formatBytesSi(0L))
        assertEquals("999 B", formatBytesSi(999L))
        assertEquals("1.0 KB", formatBytesSi(1_000L))
    }

    @Test
    fun `formatBytesSi reports gigabytes in decimal units`() {
        assertEquals("2.50 GB", formatBytesSi(2_500_000_000L))
    }

    @Test
    fun `formatBytes keeps its binary units for other tools`() {
        assertEquals("1.0 KB", formatBytes(1_024L))
    }
}
