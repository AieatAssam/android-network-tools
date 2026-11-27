package net.aieat.netswissknife.app.ui.screens.ping

import androidx.lifecycle.SavedStateHandle
import net.aieat.netswissknife.app.data.AppPreferenceDefaults
import net.aieat.netswissknife.app.data.ToolSessionMemory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PingSliders Settings defaults")
class PingSlidersTest {
    private val memory = ToolSessionMemory()

    @Test
    fun `an idle default is applied to an untouched count and timeout`() {
        val sliders = PingSliders(SavedStateHandle(), memory)

        sliders.applyDefaults(defaultCount = 40, defaultTimeoutMs = 5_000, running = false)

        assertEquals(40, sliders.count.value)
        assertEquals(5_000, sliders.timeoutMs.value)
    }

    @Test
    fun `no default is applied while a run is in progress`() {
        val sliders = PingSliders(SavedStateHandle(), memory)

        sliders.applyDefaults(defaultCount = 40, defaultTimeoutMs = 5_000, running = true)

        assertEquals(AppPreferenceDefaults.PING_COUNT, sliders.count.value)
        assertEquals(AppPreferenceDefaults.TIMEOUT_MS, sliders.timeoutMs.value)
    }

    @Test
    fun `a default held back during a run is applied once the run ends`() {
        val sliders = PingSliders(SavedStateHandle(), memory)
        sliders.applyDefaults(defaultCount = 40, defaultTimeoutMs = 5_000, running = true)

        sliders.applyDefaults(defaultCount = 40, defaultTimeoutMs = 5_000, running = false)

        assertEquals(40, sliders.count.value)
        assertEquals(5_000, sliders.timeoutMs.value)
    }

    @Test
    fun `a value the user set wins over a later default`() {
        val sliders = PingSliders(SavedStateHandle(), memory)
        sliders.setCount(25)
        sliders.setTimeoutMs(4_000)

        sliders.applyDefaults(defaultCount = 40, defaultTimeoutMs = 5_000, running = false)

        assertEquals(25, sliders.count.value)
        assertEquals(4_000, sliders.timeoutMs.value)
    }

    @Test
    fun `a recreated tool with an untouched snapshot picks up a newer default`() {
        PingSliders(SavedStateHandle(), memory).apply {
            applyDefaults(defaultCount = 10, defaultTimeoutMs = 2_000, running = false)
            memory.put(SLIDERS_SESSION_KEY, snapshot())
        }

        val returned = PingSliders(SavedStateHandle(), memory)
        returned.applyDefaults(defaultCount = 53, defaultTimeoutMs = 5_500, running = false)

        assertEquals(53, returned.count.value)
        assertEquals(5_500, returned.timeoutMs.value)
    }

    @Test
    fun `a recreated tool keeps a value the user set before leaving`() {
        PingSliders(SavedStateHandle(), memory).apply {
            setCount(25)
            memory.put(SLIDERS_SESSION_KEY, snapshot())
        }

        val returned = PingSliders(SavedStateHandle(), memory)
        returned.applyDefaults(defaultCount = 53, defaultTimeoutMs = 5_500, running = false)

        assertEquals(25, returned.count.value)
    }
}
