package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.PortScanFlowResult
import net.aieat.netswissknife.core.domain.PortScanParams
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All possible UI states for the port scanner screen. */
sealed interface PortScanUiState {
    object Idle : PortScanUiState
    data class Scanning(
        val liveResults: List<PortScanResult>,
        val scannedCount: Int,
        val totalCount: Int,
        val resolvedIp: String? = null,
        val progress: Float = if (totalCount > 0) scannedCount.toFloat() / totalCount else 0f
    ) : PortScanUiState
    data class Finished(val summary: PortScanSummary) : PortScanUiState
    data class Error(val message: String) : PortScanUiState
}

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanUseCase: PortScanUseCase,
    private val dataStore: DataStore<Preferences>,
    private val recentHostsRepository: RecentHostsRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val monotonicClock: MonotonicClock = SystemMonotonicClock,
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

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_PORTS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var scanJob: Job? = null
    private var scanStartedAtNanos: Long? = null

    init {
        savedStateHandle.get<String>("host")
            ?.takeIf { it.isNotBlank() }
            ?.let { _host.value = it }
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _timeoutMs.value = prefs[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] ?: 2_000
            _concurrency.value = (prefs[AppPreferenceKeys.DEFAULT_CONCURRENCY] ?: 50).coerceIn(1, 500)
        }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun onHostChange(value: String) { _host.value = value }

    fun onPresetChange(preset: PortScanPreset) { _selectedPreset.value = preset }

    fun onStartPortChange(value: String) { _startPort.value = value }

    fun onEndPortChange(value: String) { _endPort.value = value }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value }

    fun onConcurrencyChange(value: Int) { _concurrency.value = value.coerceIn(1, 500) }

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_PORTS_HOSTS)
        }
    }

    fun onClear() {
        scanJob?.cancel()
        scanStartedAtNanos = null
        _uiState.value = PortScanUiState.Idle
    }

    fun onStopScan() {
        scanJob?.cancel()
        val current = _uiState.value
        if (current is PortScanUiState.Scanning) {
            // Build partial summary from live results
            val startedAtNanos = scanStartedAtNanos ?: monotonicClock.nowNanos()
            val partial = net.aieat.netswissknife.core.network.portscan.PortScanSummary(
                host = _host.value,
                resolvedIp = current.resolvedIp,
                scannedPorts = current.liveResults.map { it.port },
                openPorts = current.liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.OPEN },
                closedPorts = current.liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.CLOSED },
                filteredPorts = current.liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.FILTERED },
                scanDurationMs = monotonicClock.elapsedMillisSince(startedAtNanos),
                results = current.liveResults.sortedBy { it.port }
            )
            scanStartedAtNanos = null
            _uiState.value = PortScanUiState.Finished(partial)
        }
    }

    fun startScan() {
        scanJob?.cancel()
        val normalizedHost = HostValidator.normalize(_host.value)
        val hostForScan = normalizedHost ?: _host.value.trim()
        if (normalizedHost != null) {
            _host.value = normalizedHost
            viewModelScope.launch {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, normalizedHost)
            }
        }
        val liveResults = mutableListOf<PortScanResult>()

        val params = PortScanParams(
            host = hostForScan,
            preset = _selectedPreset.value,
            startPort = _startPort.value.toIntOrNull() ?: 1,
            endPort = _endPort.value.toIntOrNull() ?: 1024,
            timeoutMs = _timeoutMs.value,
            concurrency = _concurrency.value
        )

        val totalPorts = if (_selectedPreset.value == PortScanPreset.CUSTOM) {
            val start = params.startPort
            val end = params.endPort
            if (start in 1..65_535 && end in start..65_535 && end - start + 1 <= 10_000) {
                end - start + 1
            } else {
                0
            }
        } else {
            _selectedPreset.value.ports.size
        }
        scanStartedAtNanos = monotonicClock.nowNanos()
        _uiState.value = PortScanUiState.Scanning(
            liveResults = emptyList(),
            scannedCount = 0,
            totalCount = totalPorts
        )

        scanJob = viewModelScope.launch {
            try {
                portScanUseCase(params).collect { result ->
                    when (result) {
                        is PortScanFlowResult.Started -> {
                            _uiState.value = PortScanUiState.Scanning(
                                liveResults = liveResults.toList(),
                                scannedCount = 0,
                                totalCount = result.totalCount,
                                resolvedIp = result.resolvedIp
                            )
                        }
                        is PortScanFlowResult.ValidationError -> {
                            scanStartedAtNanos = null
                            _uiState.value = PortScanUiState.Error(result.message)
                        }
                        is PortScanFlowResult.PortScanned -> {
                            liveResults.add(result.result)
                            _uiState.value = PortScanUiState.Scanning(
                                liveResults = liveResults.toList(),
                                scannedCount = result.scannedCount,
                                totalCount = result.totalCount,
                                resolvedIp = (_uiState.value as? PortScanUiState.Scanning)?.resolvedIp
                            )
                        }
                        is PortScanFlowResult.ScanComplete -> {
                            scanStartedAtNanos = null
                            _uiState.value = PortScanUiState.Finished(result.summary)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanStartedAtNanos = null
                _uiState.value = PortScanUiState.Error("Scan failed: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
