package net.aieat.netswissknife.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImeFocusRevealTest {
    @Test
    fun `field and the trailing row are both requested when they fit above the keyboard`() {
        assertEquals(300, focusRevealHeightPx(fieldHeightPx = 200, trailingPx = 100, visibleHeightPx = 1000))
    }

    @Test
    fun `request is capped at the visible height so the top edge stays in view`() {
        assertEquals(250, focusRevealHeightPx(fieldHeightPx = 200, trailingPx = 100, visibleHeightPx = 250))
    }

    @Test
    fun `field is never cut short even when the visible height is smaller than the field`() {
        assertEquals(200, focusRevealHeightPx(fieldHeightPx = 200, trailingPx = 100, visibleHeightPx = 150))
    }

    @Test
    fun `no trailing room requests only the field`() {
        assertEquals(200, focusRevealHeightPx(fieldHeightPx = 200, trailingPx = 0, visibleHeightPx = 1000))
    }
}
