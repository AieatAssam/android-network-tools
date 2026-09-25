package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.core.domain.PortScanFlowResult
import net.aieat.netswissknife.core.domain.PortScanDeadlineBudget
import net.aieat.netswissknife.core.domain.PortScanParams
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationSession
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

object PortScanDefaults {
    const val CONCURRENCY = 50
}

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
    data class Finished(
        val summary: PortScanSummary,
        val completion: Completion = Completion.COMPLETE,
    ) : PortScanUiState
    data class Error(val message: String, val isBudgetLimit: Boolean = false) : PortScanUiState

    enum class Completion { COMPLETE, USER_STOPPED, DEADLINE }
}

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanUseCase: PortScanUseCase,
    private val dataStore: DataStore<Preferences>,
    private val recentHostsRepository: RecentHostsRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val monotonicClock: MonotonicClock = SystemMonotonicClock,
) : ViewModel() {

    companion object {
        private const val HANDOFF_CONSUMED_KEY = "portsHandoffConsumed"
        private const val HANDOFF_SOURCE_KEY = "portsHandoffSource"
        private const val EDITED_HOST_KEY = "editedHost"
    }

    private val rawIntentArgument = savedStateHandle.get<String>("intent")
    private val hasIntentArgument = rawIntentArgument != null
    private val decodedIntent = rawIntentArgument?.let(ToolIntentCodec::decode)
    private val intentHost = (decodedIntent?.destination as? ToolDestination.HostTarget)
        ?.takeIf { it.tool == HostTool.PORTS }
    private val routeHost = savedStateHandle.get<String>("host")
    private val routeArgumentsMatch = !hasIntentArgument || (
        intentHost != null &&
            (routeHost == null || ToolHost.parse(routeHost)?.canonical == intentHost.host.canonical)
        )

    /** True when an invalid typed route has not yet been replaced by a valid user edit. */
    private val _hasInvalidHandoff = MutableStateFlow(
        hasIntentArgument && !routeArgumentsMatch && savedStateHandle.get<Boolean>("handoffRecovered") != true,
    )
    val hasInvalidHandoff: StateFlow<Boolean> = _hasInvalidHandoff.asStateFlow()

    private val inboundIntent = decodedIntent.takeIf { routeArgumentsMatch }
    private val _sourceContext = MutableStateFlow(
        if (savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true) {
            savedStateHandle.get<String>(HANDOFF_SOURCE_KEY)?.let { wireName ->
                ToolSource.entries.singleOrNull { it.wireName == wireName }
            }?.takeIf { inboundIntent?.source == it }
        } else {
            inboundIntent?.source
        },
    )

    /** Context for a prefilled handoff, retained only while its form value is retained. */
    val sourceContext: ToolSource? get() = _sourceContext.value

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

    private val _concurrency = MutableStateFlow(PortScanDefaults.CONCURRENCY)
    val concurrency: StateFlow<Int> = _concurrency.asStateFlow()

    private val _aggressiveProbes = MutableStateFlow(true)
    val aggressiveProbes: StateFlow<Boolean> = _aggressiveProbes.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_PORTS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var scanJob: Job? = null
    private var scanStartedAtNanos: Long? = null
    private var scanOperationSession: OperationSession? = null

    init {
        val handoffConsumed = savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true
        val restoredEdit = savedStateHandle.get<String>(EDITED_HOST_KEY)
        if (handoffConsumed) {
            // Navigation keeps route arguments across recreation; once consumed,
            // the saved form snapshot is authoritative, including an intentional blank.
            _host.value = restoredEdit.orEmpty()
        } else {
            val initialHost = when {
                restoredEdit != null -> restoredEdit
                _hasInvalidHandoff.value -> null
                hasIntentArgument -> intentHost?.host?.value
                else -> routeHost
            }
            initialHost?.let { host ->
                _host.value = host
                savedStateHandle[EDITED_HOST_KEY] = host
            }
            _sourceContext.value?.let { source ->
                savedStateHandle[HANDOFF_SOURCE_KEY] = source.wireName
            } ?: savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
            savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        }
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _timeoutMs.value = prefs[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] ?: 2_000
            _concurrency.value = (prefs[AppPreferenceKeys.DEFAULT_CONCURRENCY] ?: PortScanDefaults.CONCURRENCY)
                .coerceIn(1, 500)
        }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun onHostChange(value: String) {
        // The clear icon routes through onHostChange(""); do not discard a
        // handoff while its scan still owns an active operation session.
        if (value.isEmpty() && (scanOperationSession != null || _uiState.value is PortScanUiState.Scanning)) {
            return
        }
        _host.value = value
        savedStateHandle[EDITED_HOST_KEY] = value
        if (value.isEmpty()) {
            _sourceContext.value = null
            savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
            savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        }
        if (_hasInvalidHandoff.value && HostValidator.normalize(value) != null) {
            savedStateHandle["handoffRecovered"] = true
            _hasInvalidHandoff.value = false
        }
    }

    /** Clear a supplied handoff while recording the blank form as the consumed state. */
    fun clearPrefill() {
        if (scanOperationSession != null || _uiState.value is PortScanUiState.Scanning) return
        _host.value = ""
        _sourceContext.value = null
        savedStateHandle[EDITED_HOST_KEY] = ""
        savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
        savedStateHandle[HANDOFF_CONSUMED_KEY] = true
    }

    fun onPresetChange(preset: PortScanPreset) { _selectedPreset.value = preset }

    fun onStartPortChange(value: String) { _startPort.value = value }

    fun onEndPortChange(value: String) { _endPort.value = value }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value }

    fun onConcurrencyChange(value: Int) { _concurrency.value = value.coerceIn(1, 500) }
    fun onAggressiveProbesChange(value: Boolean) { _aggressiveProbes.value = value }

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
        cancelScan(CancellationReason.USER_STOP)
        scanJob?.cancel()
        scanStartedAtNanos = null
        _uiState.value = PortScanUiState.Idle
    }

    fun onStopScan() {
        val session = scanOperationSession ?: return
        cancelPreservingExpiredDeadline(session, CancellationReason.USER_STOP)
        when (session.cancellationReason) {
            CancellationReason.DEADLINE_EXCEEDED -> Unit // Let the winning deadline publish its terminal state.
            null -> Unit // The operation already finished; let its queued Complete event reach the UI.
            else -> finishPartialScan()
        }
    }

    /** Stops socket probes when the screen leaves the foreground. */
    fun onLifecyclePause() {
        val session = scanOperationSession ?: return
        cancelPreservingExpiredDeadline(session, CancellationReason.LIFECYCLE_PAUSE)
        when (session.cancellationReason) {
            CancellationReason.DEADLINE_EXCEEDED -> Unit
            null -> Unit
            else -> finishPartialScan()
        }
    }

    /** Give an elapsed monotonic deadline its first-wins reason before Stop/Pause can claim it. */
    private fun cancelPreservingExpiredDeadline(
        session: OperationSession,
        requestedReason: CancellationReason,
    ) {
        val reason = try {
            session.budget.throwIfExpired()
            requestedReason
        } catch (_: OperationDeadlineExceededException) {
            CancellationReason.DEADLINE_EXCEEDED
        }
        session.cancel(reason)
    }

    private fun cancelScan(reason: CancellationReason) {
        scanOperationSession?.let { session ->
            scanOperationSession = null
            session.cancel(reason)
        }
    }

    private fun finishPartialScan() {
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
            _uiState.value = PortScanUiState.Finished(
                summary = partial,
                completion = PortScanUiState.Completion.USER_STOPPED,
            )
        }
    }

    fun startScan() {
        cancelScan(CancellationReason.USER_STOP)
        scanJob?.cancel()
        val normalizedHost = HostValidator.normalize(_host.value)
        val hostForScan = normalizedHost ?: _host.value.trim()
        if (normalizedHost != null) _host.value = normalizedHost
        val liveResults = mutableListOf<PortScanResult>()

        val params = PortScanParams(
            host = hostForScan,
            preset = _selectedPreset.value,
            startPort = _startPort.value.toIntOrNull() ?: 1,
            endPort = _endPort.value.toIntOrNull() ?: 1024,
            timeoutMs = _timeoutMs.value,
            concurrency = _concurrency.value,
            aggressiveProbes = _aggressiveProbes.value,
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

        if (totalPorts > 0 && params.timeoutMs in 100..30_000) {
            val estimate = PortScanDeadlineBudget.estimate(
                portCount = totalPorts,
                timeoutMs = params.timeoutMs,
                requestedConcurrency = params.concurrency,
            )
            if (estimate.exceedsHardCeiling) {
                scanStartedAtNanos = null
                _uiState.value = PortScanUiState.Error(
                    message = "This scan may exceed the 15-minute operation limit; increase concurrency or reduce the port range or timeout.",
                    isBudgetLimit = true,
                )
                return
            }
        }

        scanStartedAtNanos = monotonicClock.nowNanos()
        _uiState.value = PortScanUiState.Scanning(
            liveResults = emptyList(),
            scannedCount = 0,
            totalCount = totalPorts
        )

        val operationSession = portScanUseCase.newSession(params)
        scanOperationSession = operationSession
        scanJob = viewModelScope.launch {
            var recentSaved = false
            try {
                portScanUseCase(params, operationSession).collect { result ->
                    if (scanOperationSession !== operationSession) return@collect
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
                            if (!recentSaved && normalizedHost != null) {
                                saveRecentHostAfterResult(normalizedHost)
                                recentSaved = true
                            }
                        }
                        is PortScanFlowResult.ScanComplete -> {
                            scanStartedAtNanos = null
                            _uiState.value = PortScanUiState.Finished(result.summary)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: OperationDeadlineExceededException) {
                if (scanOperationSession !== operationSession) return@launch
                val scanning = _uiState.value as? PortScanUiState.Scanning
                val startedAtNanos = scanStartedAtNanos ?: monotonicClock.nowNanos()
                val partial = PortScanSummary(
                    host = hostForScan,
                    resolvedIp = scanning?.resolvedIp,
                    scannedPorts = liveResults.map { it.port },
                    openPorts = liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.OPEN },
                    closedPorts = liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.CLOSED },
                    filteredPorts = liveResults.count { it.status == net.aieat.netswissknife.core.network.portscan.PortStatus.FILTERED },
                    scanDurationMs = monotonicClock.elapsedMillisSince(startedAtNanos),
                    results = liveResults.sortedBy { it.port },
                )
                scanStartedAtNanos = null
                _uiState.value = PortScanUiState.Finished(
                    summary = partial,
                    completion = PortScanUiState.Completion.DEADLINE,
                )
            } catch (e: Exception) {
                if (scanOperationSession !== operationSession) return@launch
                scanStartedAtNanos = null
                _uiState.value = PortScanUiState.Error("Scan failed: ${e.message ?: "Unknown error"}")
            } finally {
                if (scanOperationSession === operationSession) scanOperationSession = null
            }
        }
    }

    private fun saveRecentHostAfterResult(host: String) {
        viewModelScope.launch {
            try {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, host)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Recents are best-effort and must not interrupt a running scan.
            }
        }
    }

    override fun onCleared() {
        cancelScan(CancellationReason.LIFECYCLE_PAUSE)
        super.onCleared()
    }
}
