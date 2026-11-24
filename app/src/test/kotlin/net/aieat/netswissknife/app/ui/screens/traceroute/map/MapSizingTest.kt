package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MapSizing")
class MapSizingTest {
    private val eps = 0.01f

    @Test
    fun `a phone in portrait gets a taller map, never squarer than 1 point 1 to 1`() {
        // 360 dp wide, 800 dp tall: 45% of the window is 360, capped at 360 / 1.1.
        assertEquals(327.27f, MapSizing.heightDp(widthDp = 360f, windowHeightDp = 800f), eps)
    }

    @Test
    fun `a short window falls back to the wide aspect`() {
        assertEquals(360f / 1.6f, MapSizing.heightDp(widthDp = 360f, windowHeightDp = 400f), eps)
    }

    @Test
    fun `a tall tablet window gives the map more height than the old 440 dp cap allowed`() {
        // The QA tablet: 600 x 960 dp, so a card about 568 dp wide.
        val height = MapSizing.heightDp(widthDp = 568f, windowHeightDp = 960f)

        assertEquals(432f, height, eps)
        assertEquals(640f, MapSizing.heightDp(widthDp = 900f, windowHeightDp = 1_600f), eps)
    }

    @Test
    fun `the map never gets shorter than its minimum`() {
        assertEquals(MapSizing.MIN_HEIGHT_DP, MapSizing.heightDp(widthDp = 200f, windowHeightDp = 300f), eps)
    }

    @Test
    fun `the legend uses two columns from 440 dp`() {
        assertEquals(1, MapSizing.legendColumns(439f))
        assertEquals(2, MapSizing.legendColumns(440f))
    }
}
