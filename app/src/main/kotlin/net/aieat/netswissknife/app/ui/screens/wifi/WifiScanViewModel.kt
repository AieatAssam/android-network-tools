package net.aieat.netswissknife.app.ui.screens.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.core.domain.WifiNotSupportedException
import net.aieat.netswissknife.core.domain.WifiScanUseCase
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

sealed interface WifiScanUiState {
    /** Initial state before any scan; also shown while checking permission. */
    object Idle : WifiScanUiState

    /** Location/Wi-Fi permission has not been granted. */
    object NoPermission : WifiScanUiState

    /** Wi-Fi hardware is unavailable on this device. */
    object NotSupported : WifiScanUiState

    /** Wi-Fi adapter is turned off. */
    object WifiDisabled : WifiScanUiState

    /** Scan is in progress. */
    object Scanning : WifiScanUiState

    /** Scan succeeded. [selectedAp] holds the AP the user tapped (for the detail sheet). */
    data class Success(
        val result: WifiScanResult,
        val bandFilter: WifiBand? = null,
        val sortOrder: ApSortOrder = ApSortOrder.SIGNAL,
        val selectedAp: WifiAccessPoint? = null
    ) : WifiScanUiState {
        val filteredAccessPoints: List<WifiAccessPoint> get() {
            val base = if (bandFilter == null) result.accessPoints
                       else result.accessPoints.filter { it.band == bandFilter }
            return when (sortOrder) {
                ApSortOrder.SIGNAL  -> base.sortedByDescending { it.rssi }
                ApSortOrder.SSID    -> base.sortedBy { it.displaySsid.lowercase() }
                ApSortOrder.CHANNEL -> base.sortedWith(compareBy({ it.band.ordinal }, { it.channel }, { -it.rssi }))
            }
        }

        val filteredNetworks: List<WifiNetwork> get() {
            val networks = if (bandFilter == null) result.networks
                           else result.networks.filter { n -> n.accessPoints.any { it.band == bandFilter } }
                               .map { n ->
                                   n.copy(accessPoints = n.accessPoints.filter { it.band == bandFilter }
                                       .sortedByDescending { it.rssi })
                               }
            return when (sortOrder) {
                ApSortOrder.SIGNAL  -> networks.sortedByDescending { it.bestRssi }
                ApSortOrder.SSID    -> networks.sortedBy { it.displaySsid.lowercase() }
                ApSortOrder.CHANNEL -> networks.sortedWith(
                    compareBy({ it.sortedBands.firstOrNull()?.ordinal ?: 99 }, { it.bestRssi * -1 })
                )
            }
        }
    }

    /** An error occurred during scanning. */
    data class Error(val message: String) : WifiScanUiState
}

enum class ApSortOrder(val label: String) {
    SIGNAL("Signal"), SSID("Name"), CHANNEL("Channel")
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class WifiScanViewModel @Inject constructor(
    private val wifiScanUseCase: WifiScanUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<WifiScanUiState>(WifiScanUiState.Idle)
    val uiState: StateFlow<WifiScanUiState> = _uiState.asStateFlow()

    /** Whether the auto-refresh timer is running. */
    private val _autoRefresh = MutableStateFlow(false)
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    /** Network IDs (ssid|security) that the user has expanded. Survives scans. */
    private val _expandedNetworks = MutableStateFlow<Set<String>>(emptySet())
    val expandedNetworks: StateFlow<Set<String>> = _expandedNetworks.asStateFlow()

    private var scanJob: Job? = null
    private var autoRefreshJob: Job? = null

    /** Called by the screen once it has confirmed location permission is granted. */
    fun onPermissionGranted() {
        if (!wifiScanUseCase.isSupported) {
            _uiState.value = WifiScanUiState.NotSupported
            return
        }
        startScan()
    }

    /** Called by the screen when permission is denied. */
    fun onPermissionDenied() {
        _uiState.value = WifiScanUiState.NoPermission
    }

    /**
     * @param silent When true (auto-refresh), keeps the current Success state visible
     *   while the scan runs instead of replacing it with the Scanning shimmer.
     */
    fun startScan(silent: Boolean = false) {
        // Capture user selections BEFORE any state mutation so they survive the scan.
        val prev = _uiState.value as? WifiScanUiState.Success
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (!silent) _uiState.value = WifiScanUiState.Scanning
            try {
                val result = wifiScanUseCase()
                if (!result.isWifiEnabled) {
                    _uiState.value = WifiScanUiState.WifiDisabled
                } else {
                    _uiState.value = WifiScanUiState.Success(
                        result = result,
                        bandFilter = prev?.bandFilter
                            ?.takeIf { it in result.detectedBands }
                            ?: result.detectedBands.firstOrNull(),
                        sortOrder = prev?.sortOrder ?: ApSortOrder.SIGNAL,
                        // Keep the detail sheet open if the AP is still present in the new scan.
                        selectedAp = prev?.selectedAp?.let { prevAp ->
                            result.accessPoints.find { it.bssid == prevAp.bssid }
                        }
                    )
                    if (!_autoRefresh.value) startAutoRefresh()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WifiNotSupportedException) {
                _uiState.value = WifiScanUiState.NotSupported
            } catch (e: SecurityException) {
                _uiState.value = WifiScanUiState.NoPermission
            } catch (e: Exception) {
                _uiState.value = WifiScanUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setBandFilter(band: WifiBand?) {
        val current = _uiState.value as? WifiScanUiState.Success ?: return
        _uiState.value = current.copy(bandFilter = band)
    }

    fun setSortOrder(order: ApSortOrder) {
        val current = _uiState.value as? WifiScanUiState.Success ?: return
        _uiState.value = current.copy(sortOrder = order)
    }

    fun selectAccessPoint(ap: WifiAccessPoint?) {
        val current = _uiState.value as? WifiScanUiState.Success ?: return
        _uiState.value = current.copy(selectedAp = ap)
    }

    fun toggleNetworkExpanded(networkId: String) {
        val current = _expandedNetworks.value
        _expandedNetworks.value =
            if (networkId in current) current - networkId else current + networkId
    }

    fun toggleAutoRefresh() {
        if (_autoRefresh.value) {
            stopAutoRefresh()
        } else {
            startAutoRefresh()
        }
    }

    fun startAutoRefresh() {
        _autoRefresh.value = true
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (_uiState.value !is WifiScanUiState.Scanning) {
                    startScan(silent = true)
                }
            }
        }
    }

    fun stopAutoRefresh() {
        _autoRefresh.value = false
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun onRetry() {
        _uiState.value = WifiScanUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        scanJob?.cancel()
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 10_000L
    }
}
