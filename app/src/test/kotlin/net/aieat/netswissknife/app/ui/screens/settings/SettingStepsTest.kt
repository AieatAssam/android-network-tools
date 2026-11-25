package net.aieat.netswissknife.app.ui.screens.settings

import net.aieat.netswissknife.app.data.AppPreferenceDefaults
import net.aieat.netswissknife.app.ui.screens.PORT_CONCURRENCY_STEPS
import net.aieat.netswissknife.app.ui.screens.ping.PING_TIMEOUT_PRESETS_MS
import net.aieat.netswissknife.app.ui.screens.portscan.PortScanDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingStepsTest {
    @Test
    fun `ping count steps stay inside the 1 to 100 probes Ping runs`() {
        val steps = SettingSteps.PING_COUNT.values
        assertEquals(1, steps.first())
        assertEquals(100, steps.last())
    }

    @Test
    fun `every timeout step is a Ping chip, so Ping can show the default`() {
        assertEquals(PING_TIMEOUT_PRESETS_MS, SettingSteps.TIMEOUT_MS.values)
    }

    @Test
    fun `timeout steps stay inside what Port Scanner and LAN Scanner accept`() {
        SettingSteps.TIMEOUT_MS.values.forEach { assertTrue(it in 200..10_000, "$it ms") }
    }

    @Test
    fun `concurrency steps are the Port Scanner steps and stay inside 1 to 500`() {
        assertEquals(PORT_CONCURRENCY_STEPS, SettingSteps.CONCURRENCY.values)
        SettingSteps.CONCURRENCY.values.forEach { assertTrue(it in 1..500, "$it") }
    }

    @Test
    fun `each default the tools fall back to is a step`() {
        assertTrue(AppPreferenceDefaults.PING_COUNT in SettingSteps.PING_COUNT.values)
        assertTrue(AppPreferenceDefaults.TIMEOUT_MS in SettingSteps.TIMEOUT_MS.values)
        assertTrue(PortScanDefaults.CONCURRENCY in SettingSteps.CONCURRENCY.values)
    }

    @Test
    fun `the slider has one stop per step`() {
        val steps = SettingSteps(listOf(1, 2, 5, 10))
        assertEquals(3, steps.lastIndex)
        assertEquals(2, steps.sliderSteps)
        assertEquals(0, SettingSteps(listOf(7)).sliderSteps)
    }

    @Test
    fun `a slider position maps to the step it rests on`() {
        val steps = SettingSteps(listOf(1, 2, 5, 10))
        assertEquals(1, steps.valueAt(0f))
        assertEquals(5, steps.valueAt(1.6f))
        assertEquals(10, steps.valueAt(9f))
        assertEquals(1, steps.valueAt(-2f))
    }

    @Test
    fun `a value maps to the nearest step, a tie to the smaller one`() {
        val steps = SettingSteps(listOf(1, 2, 5, 10))
        assertEquals(2, steps.nearest(3))
        assertEquals(5, steps.nearest(4))
        assertEquals(10, steps.nearest(490))
        assertEquals(1, steps.nearest(-3))
        assertEquals(2, steps.indexOf(5))
    }

    @Test
    fun `a stored value off the steps is shown as stored until the thumb moves`() {
        val steps = SettingSteps.CONCURRENCY
        val stored = 35
        val resting = steps.indexOf(stored)
        assertEquals(35, steps.shownValue(stored, resting))
        assertEquals(50, steps.shownValue(stored, resting + 1))
        assertEquals(2, steps.shownValue(stored, steps.indexOf(2)))
    }
}
