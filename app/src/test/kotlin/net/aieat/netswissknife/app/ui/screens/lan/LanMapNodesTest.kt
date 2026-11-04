package net.aieat.netswissknife.app.ui.screens.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanMapNodesTest {
    @Test
    fun `node caption is the first hostname label`() {
        assertEquals("ledpanel-zero2w", lanMapNodeCaption(hostname = "ledpanel-zero2w.aieat.net", ip = "10.0.0.147"))
    }

    @Test
    fun `node caption falls back to the last octet when no hostname is known`() {
        assertEquals("147", lanMapNodeCaption(hostname = null, ip = "10.0.0.147"))
    }

    @Test
    fun `blank hostname counts as unknown`() {
        assertEquals("147", lanMapNodeCaption(hostname = "  ", ip = "10.0.0.147"))
    }

    @Test
    fun `vendor text never becomes a node caption`() {
        // The caption is derived from hostname and address only; the table already shows vendors.
        assertEquals("1", lanMapNodeCaption(hostname = null, ip = "192.168.1.1"))
    }

    @Test
    fun `preview shows every node when the list fits the limit`() {
        val preview = lanMapVisibleNodes(listOf(1, 2, 3), showAll = false, limit = 4)

        assertEquals(listOf(1, 2, 3), preview.visible)
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun `preview caps nodes at the limit and counts the rest`() {
        val preview = lanMapVisibleNodes((1..10).toList(), showAll = false, limit = 4)

        assertEquals(listOf(1, 2, 3, 4), preview.visible)
        assertEquals(6, preview.hiddenCount)
    }

    @Test
    fun `show all reveals every node`() {
        val preview = lanMapVisibleNodes((1..10).toList(), showAll = true, limit = 4)

        assertEquals((1..10).toList(), preview.visible)
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun `an empty list has no nodes and no hidden count`() {
        val preview = lanMapVisibleNodes(emptyList<Int>(), showAll = false, limit = 4)

        assertEquals(emptyList<Int>(), preview.visible)
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun `a phone-width card fits four nodes in a row`() {
        // 400dp phone minus 2 x 16dp screen gutter minus 2 x 16dp card padding.
        assertEquals(4, lanMapColumnCount(availableWidthDp = 336f, nodeWidthDp = 72f, spacingDp = 8f))
    }

    @Test
    fun `a 360dp phone at font 1_3 still fits three nodes`() {
        assertEquals(3, lanMapColumnCount(availableWidthDp = 296f, nodeWidthDp = 72f, spacingDp = 8f))
    }

    @Test
    fun `a wide tablet card fits as many nodes as the width allows`() {
        assertEquals(7, lanMapColumnCount(availableWidthDp = 576f, nodeWidthDp = 72f, spacingDp = 8f))
    }

    @Test
    fun `a width too narrow for one node still lays out one column`() {
        assertEquals(1, lanMapColumnCount(availableWidthDp = 40f, nodeWidthDp = 72f, spacingDp = 8f))
        assertEquals(1, lanMapColumnCount(availableWidthDp = 0f, nodeWidthDp = 72f, spacingDp = 8f))
    }
}
