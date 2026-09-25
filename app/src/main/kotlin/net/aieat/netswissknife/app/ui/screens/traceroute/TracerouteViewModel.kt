package net.aieat.netswissknife.app.ui.screens.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TracerouteViewMode { Visual, Raw }

sealed interface TracerouteUiState {
    object Idle : TracerouteUiState
    data class Running(
        val host: String,
        val hops: List<HopResult>
    ) : TracerouteUiState
    data class Canceling(
        val host: String,
        val hops: List<HopResult>,
        val operationId: Int,
        val elapsedMs: Long,
    ) : TracerouteUiState
    data class Canceled(
        val result: TracerouteResult,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual,
    ) : TracerouteUiState
    data class Finished(
        val result: TracerouteResult,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual,
        val timeLimitReached: Boolean = false,
    ) : TracerouteUiState
    data class Error(val message: String) : TracerouteUiState
}

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracerouteUseCase: TracerouteUseCase,
    private val recentHostsRepository: RecentHostsRepository,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    companion object {
        private const val NO_NETWORK_CONNECTION = "No network connection"
        // Resource cleanup may block while cancellation closes native or socket handles.
        // Keep it off the UI and independent of the ViewModel's clearing scope.
        private val cancellationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val _uiState = MutableStateFlow<TracerouteUiState>(TracerouteUiState.Idle)
    val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    private val _host         = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _maxHops      = MutableStateFlow(30)
    val maxHops: StateFlow<Int> = _maxHops.asStateFlow()

    private val _timeoutMs    = MutableStateFlow(3_000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _probesPerHop = MutableStateFlow(1)
    val probesPerHop: StateFlow<Int> = _probesPerHop.asStateFlow()

    private val _probeType    = MutableStateFlow(TracerouteProbeType.ICMP)
    val probeType: StateFlow<TracerouteProbeType> = _probeType.asStateFlow()

    /** 0 = MTU discovery; positive value = fixed packet size in bytes. */
    private val _packetSize   = MutableStateFlow(56)
    val packetSize: StateFlow<Int> = _packetSize.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var traceJob: Job? = null
    private var traceSession: OperationSession? = null
    private var traceStartedAtNanos: Long? = null
    internal var monotonicTimeNs: () -> Long = System::nanoTime
    /** Incremented each time a new trace is started; guards against stale emissions. */
    private var traceGeneration = 0

    /** Injectable for deterministic deadline tests; production budgets remain request-derived. */
    internal var operationSessionFactory: (TracerouteParams) -> OperationSession = { params ->
        TracerouteOperation.newSession(
            params.maxHops,
            params.timeoutMs,
            params.probesPerHop,
        )
    }

    // ── User actions ─────────────────────────────────────────────────────────

    fun onHostChange(value: String)          { _host.value = value }
    fun onMaxHopsChange(value: Int)          { _maxHops.value = value.coerceIn(1, 64) }
    fun onTimeoutChange(value: Int)          { _timeoutMs.value = value.coerceIn(500, 30_000) }
    fun onProbesPerHopChange(value: Int)     { _probesPerHop.value = value.coerceIn(1, 5) }
    fun onProbeTypeChange(value: TracerouteProbeType) { _probeType.value = value }

    /**
     * Toggle packet size between fixed mode (56 bytes) and MTU discovery (0).
     * When switching back to fixed mode the last known fixed size is restored.
     */
    fun onPacketSizeChange(value: Int)       { _packetSize.value = value.coerceIn(0, 1472) }
    fun onToggleMtuDiscovery(enabled: Boolean) {
        _packetSize.value = if (enabled) 0 else 56
    }

    fun onToggleViewMode() {
        when (val current = _uiState.value) {
            is TracerouteUiState.Finished -> {
                val next = if (current.viewMode == TracerouteViewMode.Visual)
                    TracerouteViewMode.Raw else TracerouteViewMode.Visual
                _uiState.value = current.copy(viewMode = next)
            }
            is TracerouteUiState.Canceled -> {
                val next = if (current.viewMode == TracerouteViewMode.Visual)
                    TracerouteViewMode.Raw else TracerouteViewMode.Visual
                _uiState.value = current.copy(viewMode = next)
            }
            else -> Unit
        }
    }

    fun onStop() {
        if (_uiState.value is TracerouteUiState.Canceling) return
        val current = _uiState.value as? TracerouteUiState.Running ?: return
        val operationId = traceGeneration
        traceGeneration++
        val elapsedMs = elapsedSinceStartMs()
        _uiState.value = TracerouteUiState.Canceling(
            host = current.host,
            hops = current.hops,
            operationId = operationId,
            elapsedMs = elapsedMs,
        )
        cancelActiveTrace(CancellationReason.USER_STOP)
    }

    fun onClear() {
        if (_uiState.value is TracerouteUiState.Canceling) return
        if (_uiState.value is TracerouteUiState.Running) {
            onStop()
            return
        }
        traceGeneration++
        _uiState.value = TracerouteUiState.Idle
    }

    fun onRetry() { startTrace() }

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS)
        }
    }

    fun startTrace() {
        if (traceSession != null || traceJob?.isActive == true ||
            _uiState.value is TracerouteUiState.Running || _uiState.value is TracerouteUiState.Canceling
        ) return
        val generation = ++traceGeneration

        val status = networkStatus.value
        if (!status.hasInternet && !status.hasLocalNetwork && !status.vpnActive) {
            _uiState.value = TracerouteUiState.Error(NO_NETWORK_CONNECTION)
            return
        }

        if (TracerouteOperation.requestedTimeoutMillis(
                _maxHops.value,
                _timeoutMs.value,
                _probesPerHop.value,
            ) == null
        ) {
            _uiState.value = TracerouteUiState.Error(
                "Requested trace exceeds the 20-minute time limit; reduce max hops, probes per hop, or timeout",
            )
            return
        }

        val validatedHost = HostValidator.normalize(_host.value)
        val normalizedHost = validatedHost ?: _host.value.trim()
        val params = TracerouteParams(
            host          = normalizedHost,
            maxHops       = _maxHops.value,
            timeoutMs     = _timeoutMs.value,
            probesPerHop  = _probesPerHop.value,
            probeType     = _probeType.value,
            packetSize    = _packetSize.value
        )
        val session = operationSessionFactory(params)
        traceSession = session
        val trimmedHost = params.host
        val startedAtNanos = monotonicTimeNs()
        traceStartedAtNanos = startedAtNanos
        val accumulated = mutableListOf<HopResult>()
        var terminalError: String? = null
        var terminalEventReceived = false

        // Transition to Running immediately so the UI responds before the first hop
        // arrives.  If validation fails the first emission will overwrite this with Error.
        _uiState.value = TracerouteUiState.Running(host = trimmedHost, hops = emptyList())

        traceJob = viewModelScope.launch {
            var recentSaved = false
            try {
                tracerouteUseCase(params, session).collect { result ->
                    // Discard any emission that was dispatched before the cancel took effect.
                    if (traceGeneration != generation || terminalEventReceived) return@collect
                    when (result) {
                        is TracerouteFlowResult.ValidationError -> {
                            terminalEventReceived = true
                            terminalError = result.message
                        }
                        is TracerouteFlowResult.Hop -> {
                            accumulated.add(result.hop)
                            val current = _uiState.value
                            if (current is TracerouteUiState.Running) {
                                _uiState.value = current.copy(hops = accumulated.toList())
                            }
                            if (!recentSaved && validatedHost != null) {
                                saveRecentHostAfterHop(validatedHost)
                                recentSaved = true
                            }
                        }
                        is TracerouteFlowResult.HopEnriched -> {
                            val index = accumulated.indexOfFirst { it.hopNumber == result.hopNumber }
                            if (index < 0) return@collect
                            val previous = accumulated[index]
                            accumulated[index] = previous.copy(
                                hostname = result.hostname ?: previous.hostname,
                                geoLocation = result.geoLocation ?: previous.geoLocation,
                            )
                            val current = _uiState.value
                            if (current is TracerouteUiState.Running) {
                                _uiState.value = current.copy(hops = accumulated.toList())
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Cancellation is resolved from the operation reason after collection cleanup.
                throw e
            } catch (e: Exception) {
                terminalError = e.message ?: "Traceroute failed"
            } finally {
                val cancellationReason = session.cancellationReason
                val current = _uiState.value
                if (traceSession === session) {
                    traceSession = null
                    traceJob = null
                    traceStartedAtNanos = null
                }
                val errorMessage = terminalError ?: when (cancellationReason) {
                    CancellationReason.DEADLINE_EXCEEDED -> "Traceroute timed out"
                    null, CancellationReason.USER_STOP, CancellationReason.LIFECYCLE_PAUSE -> null
                    else -> "Traceroute was interrupted"
                }
                when {
                    cancellationReason == CancellationReason.DEADLINE_EXCEEDED -> {
                        val resultHost = when (current) {
                            is TracerouteUiState.Running -> current.host
                            is TracerouteUiState.Canceling -> current.host
                            else -> trimmedHost
                        }
                        val partialHops = accumulated.toList()
                        _uiState.value = TracerouteUiState.Finished(
                            result = buildResult(
                                resultHost,
                                partialHops,
                                elapsedMsSince(startedAtNanos),
                            ),
                            timeLimitReached = true,
                        )
                    }
                    current is TracerouteUiState.Canceling && current.operationId == generation &&
                        (cancellationReason == null || cancellationReason == CancellationReason.USER_STOP) -> {
                        _uiState.value = TracerouteUiState.Canceled(
                            result = buildResult(current.host, current.hops, current.elapsedMs),
                        )
                    }
                    cancellationReason != CancellationReason.LIFECYCLE_PAUSE &&
                        cancellationReason != CancellationReason.USER_STOP && errorMessage != null -> {
                        _uiState.value = TracerouteUiState.Error(errorMessage)
                    }
                    cancellationReason == null && traceGeneration == generation &&
                        current is TracerouteUiState.Running -> {
                        _uiState.value = when {
                            errorMessage != null -> TracerouteUiState.Error(errorMessage)
                            current.hops.isEmpty() -> TracerouteUiState.Error("No route found to ${current.host}")
                            else -> TracerouteUiState.Finished(
                                buildResult(current.host, current.hops, elapsedMsSince(startedAtNanos)),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveRecentHostAfterHop(host: String) {
        viewModelScope.launch {
            try {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, host)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Recents are best-effort and must not interrupt the trace.
            }
        }
    }

    private fun cancelActiveTrace(reason: CancellationReason) {
        val session = traceSession
        val job = traceJob
        if (session != null || job != null) {
            cancellationScope.launch {
                try {
                    session?.cancel(reason)
                } finally {
                    job?.cancelAndJoin()
                }
            }
        }
    }

    override fun onCleared() {
        traceGeneration++
        cancelActiveTrace(CancellationReason.LIFECYCLE_PAUSE)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildResult(host: String, hops: List<HopResult>, totalMs: Long = 0L): TracerouteResult {
        val lastSuccessful = hops.lastOrNull { it.status == HopStatus.SUCCESS }
        return TracerouteResult(
            host         = host,
            resolvedIp   = lastSuccessful?.ip,
            hops         = hops,
            rawOutput    = buildRawOutput(host, hops),
            totalTimeMs  = totalMs
        )
    }

    private fun buildRawOutput(host: String, hops: List<HopResult>): String = buildString {
        val probeLabel = when (_probeType.value) {
            TracerouteProbeType.ICMP -> "ICMP"
            TracerouteProbeType.UDP  -> "UDP"
        }
        val sizeLabel = if (_packetSize.value == 0) "MTU discovery" else "${_packetSize.value} bytes"
        appendLine("traceroute to $host, ${_maxHops.value} hops max, $probeLabel, $sizeLabel")
        hops.forEach { hop ->
            val num   = "${hop.hopNumber}".padStart(3)
            val ip    = hop.ip ?: "*"
            val host2 = if (hop.hostname != null) " (${hop.hostname})" else ""
            val rtt   = if (hop.rtTimeMs != null) " ${hop.rtTimeMs} ms" else " *"
            val geo   = hop.geoLocation?.let { gl -> " [${gl.city.ifBlank { gl.country }}]" } ?: ""
            appendLine("$num  $ip$host2$rtt$geo")
        }
    }

    private fun elapsedSinceStartMs(): Long = traceStartedAtNanos?.let(::elapsedMsSince) ?: 0L

    private fun elapsedMsSince(startedAtNanos: Long): Long =
        ((monotonicTimeNs() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
}
