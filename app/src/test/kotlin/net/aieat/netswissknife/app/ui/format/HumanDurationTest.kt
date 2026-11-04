package net.aieat.netswissknife.app.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HumanDurationTest {
    @Test
    fun `below one second stays in milliseconds`() {
        assertEquals(HumanDuration.Milliseconds(0), humanDuration(0))
        assertEquals(HumanDuration.Milliseconds(999), humanDuration(999))
    }

    @Test
    fun `negative input is treated as zero`() {
        assertEquals(HumanDuration.Milliseconds(0), humanDuration(-5))
    }

    @Test
    fun `exactly one second shows one decimal`() {
        assertEquals(HumanDuration.Seconds(1.0), humanDuration(1_000))
    }

    @Test
    fun `thirty point five nine seconds rounds to one decimal`() {
        assertEquals(HumanDuration.Seconds(30.6), humanDuration(30_590))
    }

    @Test
    fun `sub-tenth remainder rounds half up`() {
        assertEquals(HumanDuration.Seconds(1.2), humanDuration(1_150))
        assertEquals(HumanDuration.Seconds(1.1), humanDuration(1_149))
    }

    @Test
    fun `just under a minute stays in seconds`() {
        assertEquals(HumanDuration.Seconds(59.9), humanDuration(59_940))
    }

    @Test
    fun `rounding up to sixty seconds moves to minutes`() {
        assertEquals(HumanDuration.Minutes(1), humanDuration(59_950))
    }

    @Test
    fun `one minute twelve seconds`() {
        assertEquals(HumanDuration.MinutesSeconds(1, 12), humanDuration(72_000))
    }

    @Test
    fun `whole minutes have no seconds part`() {
        assertEquals(HumanDuration.Minutes(2), humanDuration(120_000))
    }

    @Test
    fun `seconds remainder rounds to the nearest second`() {
        assertEquals(HumanDuration.Minutes(2), humanDuration(120_499))
        assertEquals(HumanDuration.MinutesSeconds(2, 1), humanDuration(120_500))
    }
}
