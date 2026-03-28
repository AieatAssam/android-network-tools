package net.aieat.netswissknife.app.ui.screens.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TracerouteViewMode { Visual, Raw }

sealed interface TracerouteUiState {
    object Idle : TracerouteUiState
    data class Running(
        val host: String,
        val hops: List<HopResult>
    ) : TracerouteUiState
    data class Finished(
        val result: TracerouteResult,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual
    ) : TracerouteUiState
    data class Error(val message: String) : TracerouteUiState
}

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracerouteUseCase: TracerouteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TracerouteUiState>(TracerouteUiState.Idle)
    val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()

    private val _host         = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _maxHops      = MutableStateFlow(30)
    val maxHops: StateFlow<Int> = _maxHops.asStateFlow()

    private val _timeoutMs    = MutableStateFlow(3_000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _probesPerHop = MutableStateFlow(1)
    val probesPerHop: StateFlow<Int> = _probesPerHop.asStateFlow()

    private val _probeType    = MutableStateFlow(TracerouteProbeType.ICMP)
    val probeType: StateFlow<TracerouteProbeType> = _probeType.asStateFlow()

    /** 0 = MTU discovery; positive value = fixed packet size in bytes. */
    private val _packetSize   = MutableStateFlow(56)
    val packetSize: StateFlow<Int> = _packetSize.asStateFlow()

    private var traceJob: Job? = null

    // ── User actions ─────────────────────────────────────────────────────────

    fun onHostChange(value: String)          { _host.value = value }
    fun onMaxHopsChange(value: Int)          { _maxHops.value = value.coerceIn(1, 64) }
    fun onTimeoutChange(value: Int)          { _timeoutMs.value = value.coerceIn(500, 30_000) }
    fun onProbesPerHopChange(value: Int)     { _probesPerHop.value = value.coerceIn(1, 5) }
    fun onProbeTypeChange(value: TracerouteProbeType) { _probeType.value = value }

    /**
     * Toggle packet size between fixed mode (56 bytes) and MTU discovery (0).
     * When switching back to fixed mode the last known fixed size is restored.
     */
    fun onPacketSizeChange(value: Int)       { _packetSize.value = value.coerceIn(0, 1472) }
    fun onToggleMtuDiscovery(enabled: Boolean) {
        _packetSize.value = if (enabled) 0 else 56
    }

    fun onToggleViewMode() {
        val current = _uiState.value as? TracerouteUiState.Finished ?: return
        val next = if (current.viewMode == TracerouteViewMode.Visual)
            TracerouteViewMode.Raw else TracerouteViewMode.Visual
        _uiState.value = current.copy(viewMode = next)
    }

    fun onStop() {
        traceJob?.cancel()
        val current = _uiState.value
        if (current is TracerouteUiState.Running && current.hops.isNotEmpty()) {
            _uiState.value = TracerouteUiState.Finished(buildResult(current.host, current.hops))
        } else {
            _uiState.value = TracerouteUiState.Idle
        }
    }

    fun onClear() {
        traceJob?.cancel()
        _uiState.value = TracerouteUiState.Idle
    }

    fun onRetry() { startTrace() }

    fun startTrace() {
        traceJob?.cancel()

        val params = TracerouteParams(
            host          = _host.value,
            maxHops       = _maxHops.value,
            timeoutMs     = _timeoutMs.value,
            probesPerHop  = _probesPerHop.value,
            probeType     = _probeType.value,
            packetSize    = _packetSize.value
        )
        val trimmedHost = params.host.trim()
        val startTime   = System.currentTimeMillis()
        val accumulated = mutableListOf<HopResult>()

        // Transition to Running immediately so the UI responds before the first hop
        // arrives.  If validation fails the first emission will overwrite this with Error.
        _uiState.value = TracerouteUiState.Running(host = trimmedHost, hops = emptyList())

        traceJob = viewModelScope.launch {
            try {
                tracerouteUseCase(params).collect { result ->
                    when (result) {
                        is TracerouteFlowResult.ValidationError -> {
                            _uiState.value = TracerouteUiState.Error(result.message)
                            return@collect
                        }
                        is TracerouteFlowResult.Hop -> {
                            accumulated.add(result.hop)
                            _uiState.value = TracerouteUiState.Running(
                                host = trimmedHost,
                                hops = accumulated.toList()
                            )
                        }
                    }
                }

                val current = _uiState.value
                if (current is TracerouteUiState.Running) {
                    _uiState.value = if (current.hops.isEmpty()) {
                        TracerouteUiState.Error("No route found to $trimmedHost")
                    } else {
                        TracerouteUiState.Finished(
                            buildResult(current.host, current.hops, System.currentTimeMillis() - startTime)
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Job was cancelled by onStop() or onClear(); those functions already set the
                // correct UI state (Finished or Idle).  Re-throw so the coroutine machinery
                // knows this coroutine ended due to cancellation, not a logic error.
                throw e
            } catch (e: Exception) {
                _uiState.value = TracerouteUiState.Error(e.message ?: "Traceroute failed")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildResult(host: String, hops: List<HopResult>, totalMs: Long = 0L): TracerouteResult {
        val lastSuccessful = hops.lastOrNull { it.status == HopStatus.SUCCESS }
        return TracerouteResult(
            host         = host,
            resolvedIp   = lastSuccessful?.ip,
            hops         = hops,
            rawOutput    = buildRawOutput(host, hops),
            totalTimeMs  = totalMs
        )
    }

    private fun buildRawOutput(host: String, hops: List<HopResult>): String = buildString {
        val probeLabel = when (_probeType.value) {
            TracerouteProbeType.ICMP -> "ICMP"
            TracerouteProbeType.UDP  -> "UDP"
        }
        val sizeLabel = if (_packetSize.value == 0) "MTU discovery" else "${_packetSize.value} bytes"
        appendLine("traceroute to $host, ${_maxHops.value} hops max, $probeLabel, $sizeLabel")
        hops.forEach { hop ->
            val num   = "${hop.hopNumber}".padStart(3)
            val ip    = hop.ip ?: "*"
            val host2 = if (hop.hostname != null) " (${hop.hostname})" else ""
            val rtt   = if (hop.rtTimeMs != null) " ${hop.rtTimeMs} ms" else " *"
            val geo   = hop.geoLocation?.let { gl -> " [${gl.city.ifBlank { gl.country }}]" } ?: ""
            appendLine("$num  $ip$host2$rtt$geo")
        }
    }
}
