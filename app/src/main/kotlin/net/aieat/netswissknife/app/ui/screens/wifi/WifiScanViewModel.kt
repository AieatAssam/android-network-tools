package net.aieat.netswissknife.app.ui.screens.wifi

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.WifiNotSupportedException
import net.aieat.netswissknife.core.domain.WifiScanUseCase
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.R
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

    /** Location Services are off, so Android will not provide Wi-Fi scan results. */
    object LocationDisabled : WifiScanUiState

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
        val isFresh: Boolean get() = result.isFresh
        val scanAgeMs: Long? get() = result.scanAgeMs
        val throttled: Boolean get() = result.throttled

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

data class ApDisappearedEvent(@StringRes val messageResId: Int = R.string.wifi_ap_disappeared)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class WifiScanViewModel @Inject constructor(
    private val wifiScanUseCase: WifiScanUseCase,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow<WifiScanUiState>(WifiScanUiState.Idle)
    val uiState: StateFlow<WifiScanUiState> = _uiState.asStateFlow()

    /** Whether the auto-refresh timer is running. */
    private val _autoRefresh = MutableStateFlow(false)
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    /** Null means the user explicitly disabled automatic refresh. */
    val refreshIntervalMs: StateFlow<Long?> = dataStore.data
        .map { preferences ->
            when (val stored = preferences[AppPreferenceKeys.WIFI_REFRESH_INTERVAL_MS]) {
                null -> DEFAULT_REFRESH_INTERVAL_MS
                DISABLED_REFRESH_INTERVAL_MS -> null
                in REFRESH_INTERVAL_OPTIONS -> stored
                else -> DEFAULT_REFRESH_INTERVAL_MS
            }
        }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            DEFAULT_REFRESH_INTERVAL_MS
        )

    /** Network IDs (ssid|security) that the user has expanded. Survives scans. */
    private val _expandedNetworks = MutableStateFlow<Set<String>>(emptySet())
    val expandedNetworks: StateFlow<Set<String>> = _expandedNetworks.asStateFlow()

    /** One-shot message for the screen to show (e.g. as a Snackbar), then dismiss. */
    private val _apDisappearedEvent = MutableStateFlow<ApDisappearedEvent?>(null)
    val apDisappearedEvent: StateFlow<ApDisappearedEvent?> = _apDisappearedEvent.asStateFlow()

    fun dismissApDisappearedEvent() {
        _apDisappearedEvent.value = null
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
                val result = wifiScanUseCase(trigger = true)
                if (!result.locationEnabled) {
                    stopAutoRefresh()
                    _uiState.value = WifiScanUiState.LocationDisabled
                } else if (!result.isWifiEnabled) {
                    _uiState.value = WifiScanUiState.WifiDisabled
                } else {
                    // Keep the detail sheet open if the AP is still present in the new scan;
                    // otherwise let the user know it dropped out rather than closing silently.
                    val stillPresentAp = prev?.selectedAp?.let { prevAp ->
                        result.accessPoints.find { it.bssid == prevAp.bssid }
                    }
                    if (prev?.selectedAp != null && stillPresentAp == null) {
                        _apDisappearedEvent.value = ApDisappearedEvent()
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
        if (refreshIntervalMs.value == null) {
            _autoRefresh.value = false
            return
        }
        _autoRefresh.value = true
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (_autoRefresh.value) {
                val interval = refreshIntervalMs.first()
                if (interval == null) {
                    _autoRefresh.value = false
                    break
                }
                delay(interval)
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

    /** Resumes the configured refresh loop when the Wi-Fi screen becomes visible again. */
    fun onLifecycleResume() {
        if (_uiState.value is WifiScanUiState.Success && !_autoRefresh.value) {
            startAutoRefresh()
        }
    }

    fun setRefreshInterval(intervalMs: Long?) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[AppPreferenceKeys.WIFI_REFRESH_INTERVAL_MS] =
                    intervalMs ?: DISABLED_REFRESH_INTERVAL_MS
            }
        }
        if (intervalMs == null) {
            stopAutoRefresh()
        } else if (_uiState.value is WifiScanUiState.Success && !_autoRefresh.value) {
            startAutoRefresh()
        }
    }

    fun onRetry() {
        val shouldScan = _uiState.value is WifiScanUiState.LocationDisabled
        _uiState.value = WifiScanUiState.Idle
        if (shouldScan) startScan()
    }

    override fun onCleared() {
        stopAutoRefresh()
        scanJob?.cancel()
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MS = 30_000L
        const val DISABLED_REFRESH_INTERVAL_MS = -1L
        val REFRESH_INTERVAL_OPTIONS = setOf(15_000L, DEFAULT_REFRESH_INTERVAL_MS, 60_000L)
    }
}
