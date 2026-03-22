package com.example.netswissknife.app.ui.screens.lan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netswissknife.core.domain.LanScanFlowResult
import com.example.netswissknife.core.domain.LanScanParams
import com.example.netswissknife.core.domain.LanScanUseCase
import com.example.netswissknife.core.network.lan.LanHost
import com.example.netswissknife.core.network.lan.LanScanSummary
import com.example.netswissknife.core.network.lan.SubnetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All possible UI states for the LAN scanner screen. */
sealed interface LanScanUiState {
    object Idle : LanScanUiState

    data class Scanning(
        val hosts: List<LanHost>,
        val scannedCount: Int,
        val totalCount: Int,
        val progress: Float = if (totalCount > 0) scannedCount.toFloat() / totalCount else 0f,
    ) : LanScanUiState

    data class Finished(
        val summary: LanScanSummary,
        val expandedHostIp: String? = null,
    ) : LanScanUiState

    data class Error(val message: String) : LanScanUiState
}

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val lanScanUseCase: LanScanUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LanScanUiState>(LanScanUiState.Idle)
    val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()

    // ── Form state ────────────────────────────────────────────────────────────

    private val _subnet = MutableStateFlow("")
    val subnet: StateFlow<String> = _subnet.asStateFlow()

    private val _timeoutMs = MutableStateFlow(1_000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _concurrency = MutableStateFlow(50)
    val concurrency: StateFlow<Int> = _concurrency.asStateFlow()

    private val _isSubnetLoading = MutableStateFlow(false)
    val isSubnetLoading: StateFlow<Boolean> = _isSubnetLoading.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshSubnet()
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun onSubnetChange(value: String) { _subnet.value = value }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value }

    fun onConcurrencyChange(value: Int) { _concurrency.value = value }

    /** Detects the current subnet from active network interfaces. */
    fun refreshSubnet() {
        viewModelScope.launch {
            _isSubnetLoading.value = true
            // SubnetUtils.getCurrentSubnet() uses java.net.NetworkInterface (blocking I/O on a
            // background dispatcher would be ideal but it's very fast on device).
            val detected = SubnetUtils.getCurrentSubnet()
            if (_subnet.value.isBlank()) {
                _subnet.value = detected ?: "192.168.1.0/24"
            }
            _isSubnetLoading.value = false
        }
    }

    fun startScan() {
        scanJob?.cancel()
        val liveHosts = mutableListOf<LanHost>()

        val params = LanScanParams(
            subnet = _subnet.value,
            timeoutMs = _timeoutMs.value,
            concurrency = _concurrency.value,
        )

        _uiState.value = LanScanUiState.Scanning(
            hosts = emptyList(),
            scannedCount = 0,
            totalCount = 0,
        )

        scanJob = viewModelScope.launch {
            lanScanUseCase(params).collect { result ->
                when (result) {
                    is LanScanFlowResult.ValidationError -> {
                        _uiState.value = LanScanUiState.Error(result.message)
                    }

                    is LanScanFlowResult.HostFound -> {
                        liveHosts.add(result.host)
                        _uiState.value = LanScanUiState.Scanning(
                            hosts = liveHosts.toList(),
                            scannedCount = result.scannedCount,
                            totalCount = result.totalCount,
                        )
                    }

                    is LanScanFlowResult.ScanProgress -> {
                        val current = _uiState.value
                        if (current is LanScanUiState.Scanning) {
                            _uiState.value = current.copy(
                                scannedCount = result.scannedCount,
                                totalCount = result.totalCount,
                            )
                        }
                    }

                    is LanScanFlowResult.ScanComplete -> {
                        _uiState.value = LanScanUiState.Finished(result.summary)
                    }
                }
            }
        }
    }

    fun onStopScan() {
        scanJob?.cancel()
        val current = _uiState.value
        if (current is LanScanUiState.Scanning) {
            val partial = LanScanSummary(
                subnet = _subnet.value,
                totalScanned = current.totalCount,
                aliveHosts = current.hosts.size,
                scanDurationMs = 0L,
                hosts = current.hosts,
            )
            _uiState.value = LanScanUiState.Finished(partial)
        }
    }

    fun onClear() {
        scanJob?.cancel()
        _uiState.value = LanScanUiState.Idle
    }

    fun onToggleHostExpanded(ip: String) {
        val current = _uiState.value as? LanScanUiState.Finished ?: return
        _uiState.value = current.copy(
            expandedHostIp = if (current.expandedHostIp == ip) null else ip
        )
    }
}
