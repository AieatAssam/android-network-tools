package net.aieat.netswissknife.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenHeightModeTest {
    @Test
    fun `phone landscape height uses the compact hero`() {
        // Phone landscape at 640x353dp (PHA-21, PHA-23, PHB-25).
        assertTrue(isCompactHeroHeight(353))
    }

    @Test
    fun `height just under the threshold is compact and the threshold itself is not`() {
        assertTrue(isCompactHeroHeight(479))
        assertFalse(isCompactHeroHeight(480))
    }

    @Test
    fun `phone portrait and tablet landscape keep the full hero`() {
        assertFalse(isCompactHeroHeight(593)) // phone portrait at 400dp width
        assertFalse(isCompactHeroHeight(600)) // tablet landscape, unchanged layout
    }

    @Test
    fun `phone width gives two tile columns`() {
        // 400dp phone portrait minus 16dp gutters on each side.
        assertEquals(2, homeGridColumns(availableWidthDp = 368))
    }

    @Test
    fun `wide windows keep the adaptive 160dp column count`() {
        assertEquals(3, homeGridColumns(availableWidthDp = 608))
    }

    @Test
    fun `column count never drops below one`() {
        assertEquals(1, homeGridColumns(availableWidthDp = 0))
        assertEquals(1, homeGridColumns(availableWidthDp = 120))
    }
}

class NarrowHeroTest {
    @Test
    fun `360dp wide window uses the compact hero even when it is tall`() {
        assertTrue(isCompactHeroWidth(360))
    }

    @Test
    fun `400dp wide window keeps the full hero`() {
        assertFalse(isCompactHeroWidth(400))
    }
}
