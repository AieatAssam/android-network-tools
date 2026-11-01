package net.aieat.netswissknife.app.ui.screens.ping

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.data.ToolSessionMemory

// SavedStateHandle keys for the advanced sliders; they survive process death and rotation.
internal const val COUNT_KEY = "pingCount"
internal const val TIMEOUT_KEY = "pingTimeoutMs"
internal const val PAYLOAD_KEY = "pingPayloadBytes"
internal const val TTL_KEY = "pingTtl"
internal const val INTERVAL_KEY = "pingIntervalMs"

/** [ToolSessionMemory] slot for the slider values when the tool is left (the form only, never results). */
internal const val SLIDERS_SESSION_KEY = "pingSliders"

internal data class SliderSnapshot(
    val count: Int,
    val timeoutMs: Int,
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
 * (rotation, process death) wins over the values kept when the tool was left, which win over the
 * DataStore defaults applied by [applyDefaults]. Ping results are never kept here.
 */
internal class PingSliders(
    savedStateHandle: SavedStateHandle,
    memory: ToolSessionMemory,
) {
    private val restored: SliderSnapshot? = memory.get<SliderSnapshot>(SLIDERS_SESSION_KEY)
    private val restoredCount: Int? = savedStateHandle.get<Int>(COUNT_KEY) ?: restored?.count
    private val restoredTimeoutMs: Int? = savedStateHandle.get<Int>(TIMEOUT_KEY) ?: restored?.timeoutMs

    val count = MutableStateFlow(restoredCount ?: 10)
    val timeoutMs = MutableStateFlow(restoredTimeoutMs ?: 2_000)
    val payloadBytes =
        MutableStateFlow(
            savedStateHandle.get<Int>(PAYLOAD_KEY) ?: restored?.payloadBytes ?: PingViewModel.DEFAULT_PAYLOAD_BYTES,
        )
    val ttl = MutableStateFlow(savedStateHandle.get<Int>(TTL_KEY) ?: restored?.ttl ?: PingViewModel.DEFAULT_TTL)
    val intervalMs =
        MutableStateFlow(
            savedStateHandle.get<Int>(INTERVAL_KEY) ?: restored?.intervalMs ?: PingViewModel.DEFAULT_INTERVAL_MS,
        )

    /** Applies the DataStore defaults for count and timeout, unless a kept or saved value already won. */
    fun applyDefaults(
        defaultCount: Int,
        defaultTimeoutMs: Int,
    ) {
        if (restoredCount == null) count.value = defaultCount
        if (restoredTimeoutMs == null) timeoutMs.value = defaultTimeoutMs
    }
}
