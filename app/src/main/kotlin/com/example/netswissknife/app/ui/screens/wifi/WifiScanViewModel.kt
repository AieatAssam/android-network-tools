package com.example.netswissknife.app.ui.screens.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netswissknife.core.domain.WifiNotSupportedException
import com.example.netswissknife.core.domain.WifiScanUseCase
import com.example.netswissknife.core.network.wifi.WifiAccessPoint
import com.example.netswissknife.core.network.wifi.WifiBand
import com.example.netswissknife.core.network.wifi.WifiScanResult
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

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = WifiScanUiState.Scanning
            try {
                val result = wifiScanUseCase()
                if (!result.isWifiEnabled) {
                    _uiState.value = WifiScanUiState.WifiDisabled
                } else {
                    val prev = _uiState.value as? WifiScanUiState.Success
                    _uiState.value = WifiScanUiState.Success(
                        result = result,
                        bandFilter = prev?.bandFilter,
                        sortOrder = prev?.sortOrder ?: ApSortOrder.SIGNAL
                    )
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

    fun toggleAutoRefresh() {
        if (_autoRefresh.value) {
            stopAutoRefresh()
        } else {
            startAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        _autoRefresh.value = true
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (_uiState.value !is WifiScanUiState.Scanning) {
                    startScan()
                }
            }
        }
    }

    private fun stopAutoRefresh() {
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
