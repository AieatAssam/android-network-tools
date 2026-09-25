package net.aieat.netswissknife.app.ui.screens.lan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.toNetworkErrorKind
import net.aieat.netswissknife.app.util.AppLogger
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanParams
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import net.aieat.netswissknife.core.network.lan.LanScanOperationBudget
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.app.ui.navigation.ToolMacAddress
import net.aieat.netswissknife.core.network.lan.SubnetUtils
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject

private const val TAG = "LanScanViewModel"

/** Prefer a reported conventional cleartext HTTP port; otherwise keep the editable form on port 80. */
internal fun preferredHttpProbePort(openPorts: Collection<Int>): Int =
    listOf(80, 8080, 8000, 8888).firstOrNull(openPorts::contains) ?: 80

/** Compatibility facade for the budget shared with the repository's no-session path. */
internal object LanScanTimeBudget {
    const val HARD_CEILING_MILLIS = LanScanOperationBudget.HARD_CEILING_MILLIS

    fun estimate(
        targetCount: Int,
        timeoutMs: Int,
        concurrency: Int,
        enableNameProbes: Boolean = true,
    ) = LanScanOperationBudget.estimate(targetCount, timeoutMs, concurrency, enableNameProbes)
}

private fun Throwable.hasDeadlineFailure(): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is OperationDeadlineExceededException) return true
        current = current.cause
    }
    return false
}

/** All possible UI states for the LAN scanner screen. */
sealed interface LanScanUiState {
    object Idle : LanScanUiState

    data class Scanning(
        val hosts: List<LanHost>,
        val scannedCount: Int,
        val totalCount: Int,
        val uncertainCount: Int = 0,
        val uncertainDiagnostics: List<LanScanDiagnostic> = emptyList(),
        val progress: Float = if (totalCount > 0) scannedCount.toFloat() / totalCount else 0f,
    ) : LanScanUiState

    data class Canceling(val summary: LanScanSummary) : LanScanUiState

    data class Canceled(
        val summary: LanScanSummary,
        val expandedHostIp: String? = null,
        val showDiagnostics: Boolean = false,
    ) : LanScanUiState

    data class Finished(
        val summary: LanScanSummary,
        val expandedHostIp: String? = null,
        val showDiagnostics: Boolean = false,
        val partial: Boolean = false,
        val timeLimitReached: Boolean = false,
    ) : LanScanUiState

    data class Error(
        val message: String,
        val networkErrorKind: NetworkErrorKind = NetworkErrorKind.GENERAL,
        val isBudgetLimit: Boolean = false,
    ) : LanScanUiState
}

sealed interface LanNavEvent {
    data class NavigateToPorts(val host: String) : LanNavEvent
    data class NavigateToPing(val host: String) : LanNavEvent
    data class NavigateToHttp(val host: String, val port: Int) : LanNavEvent
    data class NavigateToTls(val host: String, val port: Int) : LanNavEvent
    data class NavigateToWakeOnLan(val mac: ToolMacAddress) : LanNavEvent
}

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val lanScanUseCase: LanScanUseCase,
    private val dataStore: DataStore<Preferences>,
    private val recentHostsRepository: RecentHostsRepository,
    private val linkInfoProvider: LinkInfoProvider? = null,
    networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

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

    private val _gatewayIp = MutableStateFlow<String?>(null)
    val gatewayIp: StateFlow<String?> = _gatewayIp.asStateFlow()

    private val navigationEventsChannel = Channel<LanNavEvent>(Channel.BUFFERED)
    val navigationEvents = navigationEventsChannel.receiveAsFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val recentSubnets: StateFlow<List<String>>

    private var scanJob: Job? = null
    private var scanOperationSession: OperationSession? = null
    private var pendingCancellation: PendingCancellation? = null
    private var scanStartNanos: Long = 0L

    private data class PendingCancellation(
        val session: OperationSession,
        val summary: LanScanSummary,
        val reason: CancellationReason,
    )

    internal var operationSessionFactory: (LanScanParams) -> OperationSession = { params ->
        val estimateMillis = if (SubnetUtils.isValidCidr(params.subnet) &&
            params.timeoutMs in 100..10_000 && params.concurrency in 1..500
        ) {
            LanScanTimeBudget.estimate(
                targetCount = SubnetUtils.parseSubnet(params.subnet).size,
                timeoutMs = params.timeoutMs,
                concurrency = params.concurrency,
                enableNameProbes = params.enableNameProbes,
            ).timeoutMillis
        } else {
            // Invalid requests still need to reach the use case's ordinary validation path.
            OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS
        }
        OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                timeoutMillis = estimateMillis,
                maxConcurrentProbes = params.concurrency.coerceIn(1, 500),
            )
        )
    }

    init {
        // ViewModelStore closes registered resources before cancelling viewModelScope.
        // Register first so the active operation records a typed lifecycle reason before
        // scope cancellation unwinds its collector.
        addCloseable("lan-scan-operation", AutoCloseable {
            cancelScan(CancellationReason.LIFECYCLE_PAUSE)
        })
        recentSubnets = recentHostsRepository
            .getRecents(AppPreferenceKeys.RECENT_LAN_SUBNETS)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _timeoutMs.value = prefs[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] ?: 1_000
            _concurrency.value = prefs[AppPreferenceKeys.DEFAULT_CONCURRENCY] ?: 50
        }
        refreshSubnet()
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun onSubnetChange(value: String) { _subnet.value = value }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value }

    fun onConcurrencyChange(value: Int) { _concurrency.value = value }

    fun onSearchQueryChange(value: String) { _searchQuery.value = value }

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
                    val linkInfo = linkInfoProvider?.getLinkInfo()
                    _gatewayIp.value = linkInfo?.gatewayIp
                    (linkInfo?.cidr ?: SubnetUtils.getCurrentSubnet()).also { result ->
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

    fun removeRecentSubnet(subnet: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_LAN_SUBNETS, subnet)
        }
    }

    fun clearRecentSubnets() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_LAN_SUBNETS)
        }
    }

    fun startScan() {
        // Do not overlap a new scan with an operation that is still releasing resources.
        if (scanJob?.isCompleted == false) return
        // Search and filter apply to one completed result set. The screen's local filter
        // resets when Finished content leaves composition; clear the ViewModel query too
        // so a new scan cannot return with only half of the old filter still applied.
        _searchQuery.value = ""
        val liveHosts = mutableListOf<LanHost>()
        val uncertainDiagnostics = mutableListOf<LanScanDiagnostic>()
        scanStartNanos = SystemMonotonicClock.nowNanos()

        val params = LanScanParams(
            subnet = _subnet.value,
            timeoutMs = _timeoutMs.value,
            concurrency = _concurrency.value,
            gatewayIp = _gatewayIp.value,
        )

        // Preserve the normal validator's field-specific errors for malformed values. Only
        // valid requests are eligible for a resource estimate and early size warning.
        if (SubnetUtils.isValidCidr(params.subnet) &&
            params.timeoutMs in 100..10_000 && params.concurrency in 1..500
        ) {
            val estimate = LanScanTimeBudget.estimate(
                targetCount = SubnetUtils.parseSubnet(params.subnet).size,
                timeoutMs = params.timeoutMs,
                concurrency = params.concurrency,
                enableNameProbes = params.enableNameProbes,
            )
            if (estimate.exceedsHardCeiling) {
                _uiState.value = LanScanUiState.Error(
                    LanScanOperationBudget.OVER_CEILING_MESSAGE,
                    isBudgetLimit = true,
                )
                return
            }
        }

        _uiState.value = LanScanUiState.Scanning(
            hosts = emptyList(),
            scannedCount = 0,
            totalCount = 0,
        )

        val operationSession = operationSessionFactory(params)
        scanOperationSession = operationSession
        scanJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppLogger.i(TAG, "startScan: subnet=${params.subnet} timeoutMs=${params.timeoutMs} concurrency=${params.concurrency}")
            }
            var savedToRecents = false
            try {
                lanScanUseCase(params, operationSession).collect { result ->
                    // A cancelled scan may finish late in a non-cooperative dependency. Only
                    // the currently owned operation is allowed to publish state or recents.
                    if (scanOperationSession !== operationSession) return@collect
                    when (result) {
                        is LanScanFlowResult.ValidationError -> {
                            AppLogger.w(TAG, "startScan: validation error – ${result.message}")
                            _uiState.value = LanScanUiState.Error(result.message)
                        }

                        is LanScanFlowResult.HostFound -> {
                            AppLogger.d(TAG, "startScan: host found – ip=${result.host.ip} ping=${result.host.pingTimeMs}ms ports=${result.host.openPorts}")
                            // Save to recents only on first host found — avoids persisting
                            // subnets that immediately fail validation.
                            if (!savedToRecents) {
                                savedToRecents = true
                                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_LAN_SUBNETS, params.subnet)
                            }
                            liveHosts.add(result.host)
                            _uiState.value = LanScanUiState.Scanning(
                                hosts = liveHosts.toList(),
                                scannedCount = result.scannedCount,
                                totalCount = result.totalCount,
                                uncertainCount = result.uncertainCount,
                                uncertainDiagnostics = uncertainDiagnostics.toList(),
                            )
                        }

                        is LanScanFlowResult.ScanProgress -> {
                            result.diagnostic?.let(uncertainDiagnostics::add)
                            val current = _uiState.value
                            if (current is LanScanUiState.Scanning) {
                                _uiState.value = current.copy(
                                    scannedCount = result.scannedCount,
                                    totalCount = result.totalCount,
                                    uncertainCount = result.uncertainCount,
                                    uncertainDiagnostics = uncertainDiagnostics.toList(),
                                )
                            }
                        }

                        is LanScanFlowResult.ScanComplete -> {
                            AppLogger.i(TAG, "startScan: scan complete – scanned=${result.summary.totalScanned} alive=${result.summary.aliveHosts} duration=${result.summary.scanDurationMs}ms")
                            _uiState.value = LanScanUiState.Finished(result.summary)
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (operationSession.cancellationReason != CancellationReason.DEADLINE_EXCEEDED) throw e
                if (scanOperationSession !== operationSession) return@launch
                _uiState.value = partialDeadlineResult(liveHosts, uncertainDiagnostics)
            } catch (e: Exception) {
                if (scanOperationSession !== operationSession) return@launch
                if (e.hasDeadlineFailure() || operationSession.cancellationReason == CancellationReason.DEADLINE_EXCEEDED) {
                    _uiState.value = partialDeadlineResult(liveHosts, uncertainDiagnostics)
                    return@launch
                }
                AppLogger.e(TAG, "startScan: unexpected exception during scan", e)
                _uiState.value = LanScanUiState.Error(
                    "Scan failed: ${e.message ?: "Unknown error"}",
                    e.toNetworkErrorKind(),
                )
            } finally {
                if (scanOperationSession === operationSession) scanOperationSession = null
                if (scanJob === coroutineContext[Job]) scanJob = null
                pendingCancellation?.takeIf { it.session === operationSession }?.let { pending ->
                    pendingCancellation = null
                    _uiState.value = when (
                        pending.session.cancellationReason ?: pending.reason
                    ) {
                        CancellationReason.USER_STOP -> LanScanUiState.Canceled(pending.summary)
                        CancellationReason.DEADLINE_EXCEEDED -> LanScanUiState.Finished(
                            pending.summary,
                            partial = true,
                            timeLimitReached = true,
                        )
                        else -> LanScanUiState.Finished(pending.summary, partial = true)
                    }
                }
            }
        }
    }

    private fun partialDeadlineResult(
        hosts: List<LanHost>,
        diagnostics: List<LanScanDiagnostic>,
    ): LanScanUiState.Finished {
        val current = _uiState.value as? LanScanUiState.Scanning
        val partialHosts = current?.hosts ?: hosts.toList()
        val summary = LanScanSummary(
            subnet = _subnet.value,
            totalScanned = current?.scannedCount ?: 0,
            aliveHosts = partialHosts.size,
            scanDurationMs = SystemMonotonicClock.elapsedMillisSince(scanStartNanos),
            hosts = partialHosts,
            uncertainHosts = current?.uncertainDiagnostics ?: diagnostics.toList(),
            uncertainCount = current?.uncertainCount ?: diagnostics.size,
        )
        return LanScanUiState.Finished(summary, partial = true, timeLimitReached = true)
    }

    fun onStopScan() {
        AppLogger.i(TAG, "onStopScan: cancelling scan job")
        cancelWithPartial(CancellationReason.USER_STOP)
    }

    /** Pauses active probing when the LAN tool leaves the foreground. */
    fun onLifecyclePause() {
        cancelWithPartial(CancellationReason.LIFECYCLE_PAUSE)
    }

    private fun cancelWithPartial(reason: CancellationReason) {
        val current = _uiState.value as? LanScanUiState.Scanning ?: return
        val session = scanOperationSession
        val job = scanJob
        val partial = LanScanSummary(
            subnet = _subnet.value,
            totalScanned = current.scannedCount,
            aliveHosts = current.hosts.size,
            scanDurationMs = SystemMonotonicClock.elapsedMillisSince(scanStartNanos),
            hosts = current.hosts,
            uncertainHosts = current.uncertainDiagnostics,
            uncertainCount = current.uncertainCount,
        )
        if (session == null || job == null || job.isCompleted) {
            _uiState.value = if (reason == CancellationReason.USER_STOP) {
                LanScanUiState.Canceled(partial)
            } else {
                LanScanUiState.Finished(partial, partial = true)
            }
            return
        }

        pendingCancellation = PendingCancellation(
            session = session,
            summary = partial,
            reason = session.cancellationReason ?: reason,
        )
        scanOperationSession = null
        _uiState.value = LanScanUiState.Canceling(partial)
        session.cancel(reason)
        job.cancel()
    }

    fun onClear() {
        AppLogger.d(TAG, "onClear")
        pendingCancellation = null
        cancelScan(CancellationReason.USER_STOP)
        scanJob?.cancel()
        _searchQuery.value = ""
        _uiState.value = LanScanUiState.Idle
    }

    fun onToggleHostExpanded(ip: String) {
        _uiState.value = when (val current = _uiState.value) {
            is LanScanUiState.Finished -> current.copy(expandedHostIp = if (current.expandedHostIp == ip) null else ip)
            is LanScanUiState.Canceled -> current.copy(expandedHostIp = if (current.expandedHostIp == ip) null else ip)
            else -> return
        }
    }

    fun onToggleDiagnostics() {
        _uiState.value = when (val current = _uiState.value) {
            is LanScanUiState.Finished -> current.copy(showDiagnostics = !current.showDiagnostics)
            is LanScanUiState.Canceled -> current.copy(showDiagnostics = !current.showDiagnostics)
            else -> return
        }
    }

    fun onScanPorts(host: String) {
        navigationEventsChannel.trySend(LanNavEvent.NavigateToPorts(host))
    }

    fun onPingHost(host: String) {
        navigationEventsChannel.trySend(LanNavEvent.NavigateToPing(host))
    }

    fun onProbeHttp(host: String, port: Int) {
        navigationEventsChannel.trySend(LanNavEvent.NavigateToHttp(host, port))
    }

    fun onInspectTls(host: String, port: Int) {
        navigationEventsChannel.trySend(LanNavEvent.NavigateToTls(host, port))
    }

    fun onWakeDevice(macAddress: String) {
        ToolMacAddress.parse(macAddress)?.let { mac ->
            navigationEventsChannel.trySend(LanNavEvent.NavigateToWakeOnLan(mac))
        }
    }

    private fun cancelScan(reason: CancellationReason) {
        scanOperationSession?.let { session ->
            scanOperationSession = null
            session.cancel(reason)
        }
    }

    override fun onCleared() {
        pendingCancellation = null
        cancelScan(CancellationReason.LIFECYCLE_PAUSE)
    }
}
