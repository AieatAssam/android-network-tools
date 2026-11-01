package net.aieat.netswissknife.app.ui.screens.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanSummaryLayoutTest {
    @Test
    fun `tablet width fits all three stats in one row`() {
        // 640dp max content width minus 2 x 16dp card padding
        assertEquals(3, lanStatColumnCount(availableWidthDp = 576f, fontScale = 1f, statCount = 3))
    }

    @Test
    fun `phone width at normal font fits three stats`() {
        // 360dp phone minus 2 x 16dp gutter minus 2 x 20dp card padding
        assertEquals(3, lanStatColumnCount(availableWidthDp = 288f, fontScale = 1f, statCount = 3))
    }

    @Test
    fun `phone width at font 1_3 wraps to two columns`() {
        assertEquals(2, lanStatColumnCount(availableWidthDp = 288f, fontScale = 1.3f, statCount = 3))
    }

    @Test
    fun `very narrow width at large font falls back to one column`() {
        assertEquals(1, lanStatColumnCount(availableWidthDp = 200f, fontScale = 1.3f, statCount = 3))
    }

    @Test
    fun `column count never exceeds the number of stats`() {
        assertEquals(2, lanStatColumnCount(availableWidthDp = 800f, fontScale = 1f, statCount = 2))
        assertEquals(1, lanStatColumnCount(availableWidthDp = 800f, fontScale = 1f, statCount = 1))
        assertEquals(1, lanStatColumnCount(availableWidthDp = 800f, fontScale = 1f, statCount = 0))
    }

    @Test
    fun `font scale below one is treated as one`() {
        assertEquals(
            lanStatColumnCount(availableWidthDp = 288f, fontScale = 1f, statCount = 3),
            lanStatColumnCount(availableWidthDp = 288f, fontScale = 0.85f, statCount = 3),
        )
    }

    @Test
    fun `rows keep order and wrap the remainder onto a shorter last row`() {
        assertEquals(listOf(listOf("a", "b"), listOf("c")), lanStatRows(listOf("a", "b", "c"), columns = 2))
        assertEquals(listOf(listOf("a", "b", "c")), lanStatRows(listOf("a", "b", "c"), columns = 3))
    }

    @Test
    fun `rows treat a non-positive column count as one column`() {
        assertEquals(listOf(listOf("a"), listOf("b")), lanStatRows(listOf("a", "b"), columns = 0))
    }
}
