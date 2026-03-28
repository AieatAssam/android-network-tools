package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.core.domain.PortScanFlowResult
import net.aieat.netswissknife.core.domain.PortScanParams
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All possible UI states for the port scanner screen. */
sealed interface PortScanUiState {
    object Idle : PortScanUiState
    data class Scanning(
        val liveResults: List<PortScanResult>,
        val scannedCount: Int,
        val totalCount: Int,
        val progress: Float = if (totalCount > 0) scannedCount.toFloat() / totalCount else 0f
    ) : PortScanUiState
    data class Finished(val summary: PortScanSummary) : PortScanUiState
    data class Error(val message: String) : PortScanUiState
}

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanUseCase: PortScanUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PortScanUiState>(PortScanUiState.Idle)
    val uiState: StateFlow<PortScanUiState> = _uiState.asStateFlow()

    // ── Form state ────────────────────────────────────────────────────────────

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _selectedPreset = MutableStateFlow(PortScanPreset.COMMON)
    val selectedPreset: StateFlow<PortScanPreset> = _selectedPreset.asStateFlow()

    private val _startPort = MutableStateFlow("1")
    val startPort: StateFlow<String> = _startPort.asStateFlow()

    private val _endPort = MutableStateFlow("1024")
    val endPort: StateFlow<String> = _endPort.asStateFlow()

    private val _timeoutMs = MutableStateFlow(2000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _concurrency = MutableStateFlow(100)
    val concurrency: StateFlow<Int> = _concurrency.asStateFlow()

    private var scanJob: Job? = null

    // ── User actions ──────────────────────────────────────────────────────────

    fun onHostChange(value: String) { _host.value = value }

    fun onPresetChange(preset: PortScanPreset) { _selectedPreset.value = preset }

    fun onStartPortChange(value: String) { _startPort.value = value }

    fun onEndPortChange(value: String) { _endPort.value = value }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value }

    fun onConcurrencyChange(value: Int) { _concurrency.value = value }

    fun onClear() {
        scanJob?.cancel()
        _uiState.value = PortScanUiState.Idle
    }

    fun onStopScan() {
        scanJob?.cancel()
        val current = _uiState.value
        if (current is PortScanUiState.Scanning) {
            // Build partial summary from live results
            val partial = net.aieat.netswissknife.core.network.portscan.PortScanSummary(
                host = _host.value,
                resolvedIp = null,
                scannedPorts = current.liveResults.map { it.port },
                openPorts = current.liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.OPEN },
                closedPorts = current.liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.CLOSED },
                filteredPorts = current.liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.FILTERED },
                scanDurationMs = 0L,
                results = current.liveResults.sortedBy { it.port }
            )
            _uiState.value = PortScanUiState.Finished(partial)
        }
    }

    fun startScan() {
        scanJob?.cancel()
        val liveResults = mutableListOf<PortScanResult>()

        val params = PortScanParams(
            host = _host.value,
            preset = _selectedPreset.value,
            startPort = _startPort.value.toIntOrNull() ?: 1,
            endPort = _endPort.value.toIntOrNull() ?: 1024,
            timeoutMs = _timeoutMs.value,
            concurrency = _concurrency.value
        )

        scanJob = viewModelScope.launch {
            portScanUseCase(params).collect { result ->
                when (result) {
                    is PortScanFlowResult.ValidationError -> {
                        _uiState.value = PortScanUiState.Error(result.message)
                    }
                    is PortScanFlowResult.PortScanned -> {
                        liveResults.add(result.result)
                        _uiState.value = PortScanUiState.Scanning(
                            liveResults = liveResults.toList(),
                            scannedCount = result.scannedCount,
                            totalCount = result.totalCount
                        )
                    }
                    is PortScanFlowResult.ScanComplete -> {
                        _uiState.value = PortScanUiState.Finished(result.summary)
                    }
                }
            }
        }
    }
}
