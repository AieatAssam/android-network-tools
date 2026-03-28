package net.aieat.netswissknife.app.ui.screens.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.core.domain.PingFlowResult
import net.aieat.netswissknife.core.domain.PingParams
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingResult
import net.aieat.netswissknife.core.network.ping.PingStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All possible states for the Ping UI. */
sealed interface PingUiState {
    object Idle : PingUiState
    data class Running(
        val host: String,
        val packets: List<PingPacketResult>,
        val totalCount: Int
    ) : PingUiState
    data class Finished(
        val result: PingResult,
        val showRaw: Boolean = false
    ) : PingUiState
    data class Error(val message: String) : PingUiState
}

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pingUseCase: PingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PingUiState>(PingUiState.Idle)
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    // ── Form field state ─────────────────────────────────────────────────────

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _count = MutableStateFlow(4)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _timeoutMs = MutableStateFlow(3_000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _packetSize = MutableStateFlow(56)
    val packetSize: StateFlow<Int> = _packetSize.asStateFlow()

    private var pingJob: Job? = null

    // ── User actions ─────────────────────────────────────────────────────────

    fun onHostChange(value: String) {
        _host.value = value
    }

    fun onCountChange(value: Int) {
        _count.value = value.coerceIn(1, 100)
    }

    fun onTimeoutChange(value: Int) {
        _timeoutMs.value = value.coerceIn(100, 30_000)
    }

    fun onPacketSizeChange(value: Int) {
        _packetSize.value = value.coerceIn(1, 65_507)
    }

    fun onToggleRawView() {
        val current = _uiState.value
        if (current is PingUiState.Finished) {
            _uiState.value = current.copy(showRaw = !current.showRaw)
        }
    }

    fun onClearResults() {
        pingJob?.cancel()
        _uiState.value = PingUiState.Idle
    }

    fun onStop() {
        pingJob?.cancel()
        val current = _uiState.value
        if (current is PingUiState.Running && current.packets.isNotEmpty()) {
            _uiState.value = PingUiState.Finished(buildResult(current.host, current.packets, current.totalCount))
        } else {
            _uiState.value = PingUiState.Idle
        }
    }

    fun onRetry() {
        startPing()
    }

    fun startPing() {
        pingJob?.cancel()

        val params = PingParams(
            host = _host.value,
            count = _count.value,
            timeoutMs = _timeoutMs.value,
            packetSize = _packetSize.value
        )

        pingJob = viewModelScope.launch {
            val accumulatedPackets = mutableListOf<PingPacketResult>()

            pingUseCase(params).collect { result ->
                when (result) {
                    is PingFlowResult.ValidationError -> {
                        _uiState.value = PingUiState.Error(result.message)
                        return@collect
                    }
                    is PingFlowResult.Packet -> {
                        accumulatedPackets.add(result.packet)
                        _uiState.value = PingUiState.Running(
                            host = params.host.trim(),
                            packets = accumulatedPackets.toList(),
                            totalCount = params.count
                        )
                    }
                }
            }

            // Flow completed – move to Finished if we have packets
            val current = _uiState.value
            if (current is PingUiState.Running) {
                _uiState.value = if (current.packets.isEmpty()) {
                    PingUiState.Error("No response received from ${params.host.trim()}")
                } else {
                    PingUiState.Finished(buildResult(current.host, current.packets, params.count))
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildResult(host: String, packets: List<PingPacketResult>, totalCount: Int): PingResult {
        val stats = PingStats.compute(packets)
        val raw = buildRawOutput(host, packets, stats, _packetSize.value)
        return PingResult(host = host, packets = packets, stats = stats, rawOutput = raw)
    }

    private fun buildRawOutput(
        host: String,
        packets: List<PingPacketResult>,
        stats: PingStats,
        packetSize: Int
    ): String = buildString {
        appendLine("PING $host: $packetSize data bytes")
        appendLine()
        packets.forEach { p ->
            when (p.status) {
                net.aieat.netswissknife.core.network.ping.PingStatus.SUCCESS ->
                    appendLine("${packetSize + 8} bytes from ${p.host}: icmp_seq=${p.sequence} ttl=64 time=${p.rtTimeMs} ms")
                net.aieat.netswissknife.core.network.ping.PingStatus.TIMEOUT ->
                    appendLine("Request timeout for icmp_seq ${p.sequence}")
                net.aieat.netswissknife.core.network.ping.PingStatus.ERROR ->
                    appendLine("Error for icmp_seq ${p.sequence}: ${p.errorMessage}")
            }
        }
        appendLine()
        appendLine("--- $host ping statistics ---")
        appendLine("${stats.sent} packets transmitted, ${stats.received} packets received, " +
                "${"%.1f".format(stats.lossPercent)}% packet loss")
        if (stats.received > 0) {
            appendLine("round-trip min/avg/max/jitter = ${stats.minMs}/${"%.3f".format(stats.avgMs)}/${stats.maxMs}/${"%.3f".format(stats.jitterMs)} ms")
        }
    }
}
