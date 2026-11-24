package net.aieat.netswissknife.app.ui.screens.ping

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.data.AppPreferenceDefaults
import net.aieat.netswissknife.app.data.ToolSessionMemory

// SavedStateHandle keys for the advanced sliders; they survive process death and rotation.
internal const val COUNT_KEY = "pingCount"
internal const val TIMEOUT_KEY = "pingTimeoutMs"
internal const val PAYLOAD_KEY = "pingPayloadBytes"
internal const val TTL_KEY = "pingTtl"
internal const val INTERVAL_KEY = "pingIntervalMs"

/** [ToolSessionMemory] slot for the slider values when the tool is left (the form only, never results). */
internal const val SLIDERS_SESSION_KEY = "pingSliders"

/**
 * Slider values kept when the tool is left. [count] and [timeoutMs] are null unless the user set them,
 * so a default that was only applied from Settings does not stop later Settings changes from reaching Ping.
 */
internal data class SliderSnapshot(
    val count: Int?,
    val timeoutMs: Int?,
    val payloadBytes: Int,
    val ttl: Int,
    val intervalMs: Int,
)

/** Sets a slider value and keeps it in the handle, so it survives rotation and process death. */
internal fun MutableStateFlow<Int>.setKept(
    handle: SavedStateHandle,
    key: String,
    value: Int,
) {
    this.value = value
    handle[key] = value
}

/**
 * The advanced Ping sliders (count, timeout, payload, TTL, interval). Restore order: SavedStateHandle
 * (rotation, process death) wins over the values kept when the tool was left. Count and timeout that
 * the user has not set follow the Settings defaults through [applyDefaults]. Ping results are never kept here.
 */
internal class PingSliders(
    private val savedStateHandle: SavedStateHandle,
    memory: ToolSessionMemory,
) {
    private val restored: SliderSnapshot? = memory.get<SliderSnapshot>(SLIDERS_SESSION_KEY)
    private val restoredCount: Int? = savedStateHandle.get<Int>(COUNT_KEY) ?: restored?.count
    private val restoredTimeoutMs: Int? = savedStateHandle.get<Int>(TIMEOUT_KEY) ?: restored?.timeoutMs
    private var countSetByUser = restoredCount != null
    private var timeoutSetByUser = restoredTimeoutMs != null

    val count = MutableStateFlow(restoredCount ?: AppPreferenceDefaults.PING_COUNT)
    val timeoutMs = MutableStateFlow(restoredTimeoutMs ?: AppPreferenceDefaults.TIMEOUT_MS)
    val payloadBytes =
        MutableStateFlow(
            savedStateHandle.get<Int>(PAYLOAD_KEY) ?: restored?.payloadBytes ?: PingViewModel.DEFAULT_PAYLOAD_BYTES,
        )
    val ttl = MutableStateFlow(savedStateHandle.get<Int>(TTL_KEY) ?: restored?.ttl ?: PingViewModel.DEFAULT_TTL)
    val intervalMs =
        MutableStateFlow(
            savedStateHandle.get<Int>(INTERVAL_KEY) ?: restored?.intervalMs ?: PingViewModel.DEFAULT_INTERVAL_MS,
        )

    fun setCount(value: Int) {
        countSetByUser = true
        count.setKept(savedStateHandle, COUNT_KEY, value)
    }

    fun setTimeoutMs(value: Int) {
        timeoutSetByUser = true
        timeoutMs.setKept(savedStateHandle, TIMEOUT_KEY, value)
    }

    /**
     * Applies the Settings defaults to count and timeout that the user has not set. Nothing changes while
     * a run is in progress; the caller re-applies once the run ends.
     */
    fun applyDefaults(
        defaultCount: Int,
        defaultTimeoutMs: Int,
        running: Boolean,
    ) {
        if (running) return
        if (!countSetByUser) count.value = defaultCount
        if (!timeoutSetByUser) timeoutMs.value = defaultTimeoutMs
    }

    /** Values to keep when the tool is left; count and timeout only when the user set them. */
    fun snapshot(): SliderSnapshot =
        SliderSnapshot(
            count = count.value.takeIf { countSetByUser },
            timeoutMs = timeoutMs.value.takeIf { timeoutSetByUser },
            payloadBytes = payloadBytes.value,
            ttl = ttl.value,
            intervalMs = intervalMs.value,
        )
}
