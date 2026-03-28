package net.aieat.netswissknife.app.ui.screens.lan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.app.util.AppLogger
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanParams
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import net.aieat.netswissknife.core.network.lan.SubnetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "LanScanViewModel"

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

    /** Detects the current subnet from active network interfaces on an IO thread. */
    fun refreshSubnet() {
        viewModelScope.launch {
            _isSubnetLoading.value = true
            try {
                // Run on IO – NetworkInterface reads from /proc/net, which is blocking I/O.
                // All AppLogger calls are also inside withContext(IO) to avoid disk writes on
                // the main thread (which can trigger StrictMode violations on some devices).
                val detected = withContext(Dispatchers.IO) {
                    AppLogger.d(TAG, "refreshSubnet: starting subnet detection")
                    SubnetUtils.getCurrentSubnet().also { result ->
                        AppLogger.i(TAG, "refreshSubnet: detected subnet = $result")
                    }
                }
                if (_subnet.value.isBlank()) {
                    _subnet.value = detected ?: "192.168.1.0/24"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) {
                    AppLogger.e(TAG, "refreshSubnet: failed to detect subnet", e)
                }
                if (_subnet.value.isBlank()) {
                    _subnet.value = "192.168.1.0/24"
                }
            } finally {
                _isSubnetLoading.value = false
            }
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
            withContext(Dispatchers.IO) {
                AppLogger.i(TAG, "startScan: subnet=${params.subnet} timeoutMs=${params.timeoutMs} concurrency=${params.concurrency}")
            }
            try {
                lanScanUseCase(params).collect { result ->
                    when (result) {
                        is LanScanFlowResult.ValidationError -> {
                            AppLogger.w(TAG, "startScan: validation error – ${result.message}")
                            _uiState.value = LanScanUiState.Error(result.message)
                        }

                        is LanScanFlowResult.HostFound -> {
                            AppLogger.d(TAG, "startScan: host found – ip=${result.host.ip} ping=${result.host.pingTimeMs}ms ports=${result.host.openPorts}")
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
                            AppLogger.i(TAG, "startScan: scan complete – scanned=${result.summary.totalScanned} alive=${result.summary.aliveHosts} duration=${result.summary.scanDurationMs}ms")
                            _uiState.value = LanScanUiState.Finished(result.summary)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "startScan: unexpected exception during scan", e)
                _uiState.value = LanScanUiState.Error("Scan failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun onStopScan() {
        AppLogger.i(TAG, "onStopScan: cancelling scan job")
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
        AppLogger.d(TAG, "onClear")
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
