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

    /**
     * Scan succeeded. [selectedAp] holds the AP the user tapped (for the detail sheet).
     *
     * [frozenOrder] pins the visible network order (a list of [WifiNetwork.id]) while
     * the user is inspecting something — a selected AP or an expanded network — so a
     * silent auto-refresh updates signal values in place without reordering the list
     * out from under them. It's set the moment inspection starts and cleared once
     * nothing is selected or expanded, at which point live sorting resumes.
     */
    data class Success(
        val result: WifiScanResult,
        val bandFilter: WifiBand? = null,
        val sortOrder: ApSortOrder = ApSortOrder.SIGNAL,
        val selectedAp: WifiAccessPoint? = null,
        val frozenOrder: List<String>? = null
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
            val sorted = when (sortOrder) {
                ApSortOrder.SIGNAL  -> networks.sortedByDescending { it.bestRssi }
                ApSortOrder.SSID    -> networks.sortedBy { it.displaySsid.lowercase() }
                ApSortOrder.CHANNEL -> networks.sortedWith(
                    compareBy({ it.sortedBands.firstOrNull()?.ordinal ?: 99 }, { it.bestRssi * -1 })
                )
            }
            val pinnedOrder = frozenOrder ?: return sorted
            val positionOf = pinnedOrder.withIndex().associate { (i, id) -> id to i }
            // Stable sort: anything not in the pinned order (newly appeared since the
            // freeze) falls through to the end, in its normal live-sort relative order.
            return sorted.sortedBy { positionOf[it.id] ?: Int.MAX_VALUE }
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

    /** One-shot message for the screen to show (e.g. as a Snackbar), then dismiss. */
    private val _apDisappearedMessage = MutableStateFlow<String?>(null)
    val apDisappearedMessage: StateFlow<String?> = _apDisappearedMessage.asStateFlow()

    fun dismissApDisappearedMessage() {
        _apDisappearedMessage.value = null
    }

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
                    // Keep the detail sheet open if the AP is still present in the new scan;
                    // otherwise let the user know it dropped out rather than closing silently.
                    val stillPresentAp = prev?.selectedAp?.let { prevAp ->
                        result.accessPoints.find { it.bssid == prevAp.bssid }
                    }
                    if (prev?.selectedAp != null && stillPresentAp == null) {
                        _apDisappearedMessage.value = "This network is no longer in range"
                    }
                    _uiState.value = WifiScanUiState.Success(
                        result = result,
                        bandFilter = prev?.bandFilter
                            ?.takeIf { it in result.detectedBands }
                            ?: result.detectedBands.firstOrNull(),
                        sortOrder = prev?.sortOrder ?: ApSortOrder.SIGNAL,
                        selectedAp = stillPresentAp,
                        frozenOrder = prev?.frozenOrder
                    )
                    updateFreezeState()
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
        updateFreezeState()
    }

    fun toggleNetworkExpanded(networkId: String) {
        val current = _expandedNetworks.value
        _expandedNetworks.value =
            if (networkId in current) current - networkId else current + networkId
        updateFreezeState()
    }

    /**
     * Pins [WifiScanUiState.Success.frozenOrder] to the current list order the moment
     * the user starts inspecting something (a selection or an expanded network), and
     * releases it once nothing is selected or expanded so live sorting resumes.
     */
    private fun updateFreezeState() {
        val current = _uiState.value as? WifiScanUiState.Success ?: return
        val isInspecting = current.selectedAp != null || _expandedNetworks.value.isNotEmpty()
        when {
            isInspecting && current.frozenOrder == null ->
                _uiState.value = current.copy(frozenOrder = current.filteredNetworks.map { it.id })
            !isInspecting && current.frozenOrder != null ->
                _uiState.value = current.copy(frozenOrder = null)
        }
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
        stopAutoRefresh()
        scanJob?.cancel()
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 10_000L
    }
}
