package net.aieat.netswissknife.app.ui.screens.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SliderSnapTest {

    @Test
    fun `unit step rounds to nearest integer inside range`() {
        assertEquals(10, snapSliderValue(9.6f, step = 1, range = 1..100))
        assertEquals(10, snapSliderValue(10.4f, step = 1, range = 1..100))
    }

    @Test
    fun `timeout snaps to 500ms multiples`() {
        assertEquals(2000, snapSliderValue(2249f, step = 500, range = 500..10_000))
        assertEquals(2500, snapSliderValue(2251f, step = 500, range = 500..10_000))
    }

    @Test
    fun `values below range clamp to minimum`() {
        assertEquals(1, snapSliderValue(0f, step = 1, range = 1..500))
        assertEquals(500, snapSliderValue(260f, step = 500, range = 500..10_000))
    }

    @Test
    fun `values above range clamp to maximum`() {
        assertEquals(100, snapSliderValue(140f, step = 1, range = 1..100))
        assertEquals(10_000, snapSliderValue(10_400f, step = 500, range = 500..10_000))
    }

    @Test
    fun `step zero is never produced by a unit step slider`() {
        // Regression: a continuous (steps = 0) slider must still commit whole, in-range integers.
        assertEquals(1, snapSliderValue(1.2f, step = 1, range = 1..500))
    }
}
