package net.aieat.netswissknife.app.ui.screens.traceroute

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.data.purgeInvalidHostRecents
import net.aieat.netswissknife.app.data.validHostRecents
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.platform.LiteralDestinationClassifier
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.OperationAvailability
import net.aieat.netswissknife.app.ui.i18n.UiText
import net.aieat.netswissknife.app.util.enumFromSavedName
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.TracerouteHops
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import javax.inject.Inject

/** Result tabs, in display order: Hop Details, Map, Raw Output. */
enum class TracerouteViewMode { Visual, Map, Raw }

sealed interface TracerouteUiState {
    object Idle : TracerouteUiState

    /**
     * A trace in progress. [hops] are in TTL order and end at the destination; [traceId] stays the same
     * when this trace moves on to [Canceling], [Canceled] or [Finished], so its view is not rebuilt.
     */
    data class Running(
        val host: String,
        val hops: List<HopResult>,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual,
        val rawOutput: String = "",
        val traceId: Int = 0,
    ) : TracerouteUiState

    data class Canceling(
        val host: String,
        val hops: List<HopResult>,
        val operationId: Int,
        val elapsedMs: Long,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual,
        val rawOutput: String = "",
        val traceId: Int = 0,
    ) : TracerouteUiState

    data class Canceled(
        val result: TracerouteResult,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual,
        /** True when the trace stopped because the user left the screen, not because they pressed Stop. */
        val stoppedWhenLeft: Boolean = false,
        val traceId: Int = 0,
    ) : TracerouteUiState

    data class Finished(
        val result: TracerouteResult,
        val viewMode: TracerouteViewMode = TracerouteViewMode.Visual,
        val timeLimitReached: Boolean = false,
        val traceId: Int = 0,
    ) : TracerouteUiState

    data class Error(
        val text: UiText,
        /** Developer-facing copy for logs and tests. */
        val message: String,
    ) : TracerouteUiState {
        constructor(message: String) : this(UiText.Plain(message), message)
    }
}

/**
 * The Traceroute form and its last terminal result (finished, stopped by the user, or failed), kept in
 * [ToolSessionMemory] when the tool is left. A running trace is never kept as running.
 */
internal data class TracerouteSessionSnapshot(
    val host: String,
    val maxHops: Int,
    val timeoutMs: Int,
    val probesPerHop: Int,
    val probeType: TracerouteProbeType,
    val packetSize: Int,
    val finished: TracerouteUiState?,
)

@HiltViewModel
class TracerouteViewModel
    @Inject
    constructor(
        private val tracerouteUseCase: TracerouteUseCase,
        private val recentHostsRepository: RecentHostsRepository,
        private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
        private val linkInfoProvider: LinkInfoProvider = LinkInfoProvider({ true }, { true }),
        // Hilt supplies the owner-backed handle at runtime; this empty default supports direct unit tests.
        @param:SuppressLint("VisibleForTests")
        private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
        private val sessionMemory: ToolSessionMemory = ToolSessionMemory(),
    ) : ViewModel() {
        companion object {
            // Resource cleanup may block while cancellation closes native or socket handles.
            // Keep it off the UI and independent of the ViewModel's clearing scope.
            private val cancellationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // SavedStateHandle keys for the form; they survive process death and rotation.
            private const val HOST_KEY = "traceHost"
            private const val MAX_HOPS_KEY = "traceMaxHops"
            private const val TIMEOUT_KEY = "traceTimeoutMs"
            private const val PROBES_KEY = "traceProbesPerHop"
            private const val PROBE_TYPE_KEY = "traceProbeType"
            private const val PACKET_SIZE_KEY = "tracePacketSize"

            /** [ToolSessionMemory] slot for the form and last terminal result when the tool is left. */
            private const val SESSION_KEY = "traceSession"

            /** Three probes per hop let the map and hop list show loss and jitter. */
            const val DEFAULT_PROBES_PER_HOP = 3

            /** The timeout moves in half-second steps. */
            const val TIMEOUT_STEP_MS = 500
        }

        // Restore order: a SavedStateHandle value (rotation) wins over the snapshot kept when the tool
        // was left, which in turn wins over the defaults. A kept result is shown only for the same host.
        private val restoredSession: TracerouteSessionSnapshot? =
            sessionMemory.get<TracerouteSessionSnapshot>(SESSION_KEY)
        private val initialHost: String =
            savedStateHandle.get<String>(HOST_KEY) ?: restoredSession?.host.orEmpty()

        private val _uiState =
            MutableStateFlow<TracerouteUiState>(
                restoredSession?.takeIf { it.host == initialHost }?.finished ?: TracerouteUiState.Idle,
            )
        val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()
        val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

        private val _host = MutableStateFlow(initialHost)
        val host: StateFlow<String> = _host.asStateFlow()

        private val _maxHops =
            MutableStateFlow(savedStateHandle.get<Int>(MAX_HOPS_KEY) ?: restoredSession?.maxHops ?: 30)
        val maxHops: StateFlow<Int> = _maxHops.asStateFlow()

        private val _timeoutMs =
            MutableStateFlow(savedStateHandle.get<Int>(TIMEOUT_KEY) ?: restoredSession?.timeoutMs ?: 3_000)
        val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

        private val _probesPerHop =
            MutableStateFlow(
                savedStateHandle.get<Int>(PROBES_KEY) ?: restoredSession?.probesPerHop ?: DEFAULT_PROBES_PER_HOP,
            )
        val probesPerHop: StateFlow<Int> = _probesPerHop.asStateFlow()

        private val _probeType =
            MutableStateFlow(
                enumFromSavedName(
                    savedStateHandle.get<String>(PROBE_TYPE_KEY) ?: restoredSession?.probeType?.name,
                    TracerouteProbeType.entries,
                ) ?: TracerouteProbeType.ICMP,
            )
        val probeType: StateFlow<TracerouteProbeType> = _probeType.asStateFlow()

        /** 0 = MTU discovery; positive value = fixed packet size in bytes. */
        private val _packetSize =
            MutableStateFlow(savedStateHandle.get<Int>(PACKET_SIZE_KEY) ?: restoredSession?.packetSize ?: 56)
        val packetSize: StateFlow<Int> = _packetSize.asStateFlow()

        val recentHosts: StateFlow<List<String>> =
            recentHostsRepository
                .getRecents(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS)
                .validHostRecents()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        init {
            viewModelScope.launch {
                recentHostsRepository.purgeInvalidHostRecents(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS)
            }
        }

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

        fun onHostChange(value: String) {
            _host.value = value
            savedStateHandle[HOST_KEY] = value
            // A failure message describes the previous host; drop it when the host changes.
            if (_uiState.value is TracerouteUiState.Error) {
                _uiState.value = TracerouteUiState.Idle
            }
        }

        fun onMaxHopsChange(value: Int) {
            _maxHops.value = value.coerceIn(1, 64)
            savedStateHandle[MAX_HOPS_KEY] = _maxHops.value
        }

        fun onTimeoutChange(value: Int) {
            val stepped = (value + TIMEOUT_STEP_MS / 2) / TIMEOUT_STEP_MS * TIMEOUT_STEP_MS
            _timeoutMs.value = stepped.coerceIn(500, 30_000)
            savedStateHandle[TIMEOUT_KEY] = _timeoutMs.value
        }

        fun onProbesPerHopChange(value: Int) {
            _probesPerHop.value = value.coerceIn(1, 5)
            savedStateHandle[PROBES_KEY] = _probesPerHop.value
        }

        fun onProbeTypeChange(value: TracerouteProbeType) {
            _probeType.value = value
            savedStateHandle[PROBE_TYPE_KEY] = value.name
        }

        /**
         * Toggle packet size between fixed mode (56 bytes) and MTU discovery (0).
         * When switching back to fixed mode the last known fixed size is restored.
         */
        fun onPacketSizeChange(value: Int) {
            _packetSize.value = value.coerceIn(0, 1472)
            savedStateHandle[PACKET_SIZE_KEY] = _packetSize.value
        }

        fun onToggleMtuDiscovery(enabled: Boolean) {
            _packetSize.value = if (enabled) 0 else 56
            savedStateHandle[PACKET_SIZE_KEY] = _packetSize.value
        }

        /** The view the user last picked. A trace keeps it when it finishes, and the next trace opens in it. */
        private var selectedViewMode = TracerouteViewMode.Visual

        /** Show [mode] for the current trace, running or done; ignored while there is no trace. */
        fun onSelectViewMode(mode: TracerouteViewMode) {
            _uiState.value =
                when (val current = _uiState.value) {
                    is TracerouteUiState.Running -> current.copy(viewMode = mode)
                    is TracerouteUiState.Canceling -> current.copy(viewMode = mode)
                    is TracerouteUiState.Finished -> current.copy(viewMode = mode)
                    is TracerouteUiState.Canceled -> current.copy(viewMode = mode)
                    else -> return
                }
            selectedViewMode = mode
        }

        fun onStop() {
            cancelRunningTrace(CancellationReason.USER_STOP)
        }

        /**
         * Cancel an in-flight trace when its screen leaves the foreground. A configuration
         * change (rotation) also stops the screen but must not end the trace.
         */
        fun onLifecycleStop(configurationChange: Boolean = false) {
            if (configurationChange) return
            cancelRunningTrace(CancellationReason.LIFECYCLE_PAUSE)
        }

        private fun cancelRunningTrace(reason: CancellationReason) {
            if (_uiState.value is TracerouteUiState.Canceling) return
            val current = _uiState.value as? TracerouteUiState.Running ?: return
            val operationId = traceGeneration
            traceGeneration++
            val elapsedMs = elapsedSinceStartMs()
            _uiState.value =
                TracerouteUiState.Canceling(
                    host = current.host,
                    hops = current.hops,
                    operationId = operationId,
                    elapsedMs = elapsedMs,
                    viewMode = current.viewMode,
                    rawOutput = current.rawOutput,
                    traceId = current.traceId,
                )
            cancelActiveTrace(reason)
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

        fun onRetry() {
            startTrace()
        }

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
            ) {
                return
            }
            val generation = ++traceGeneration

            val validatedHost = HostValidator.normalize(_host.value)
            val normalizedHost = validatedHost ?: _host.value.trim()
            val target = LiteralDestinationClassifier.target(normalizedHost)
            val requirement = OperationAvailability.requirementFor(target)
            val localPermission =
                if (
                    requirement == OperationRequirement.LOCAL_NETWORK
                ) {
                    linkInfoProvider.localNetworkPermissionAllowed()
                } else {
                    null
                }
            val availability =
                OperationAvailability.classifyObserved(
                    target = target,
                    status = networkStatus.value,
                    localNetworkPermissionAllowed = localPermission,
                )
            if (!availability.allowed) {
                _uiState.value = TracerouteErrorText.unavailable(availability)
                return
            }

            if (TracerouteOperation.requestedTimeoutMillis(
                    _maxHops.value,
                    _timeoutMs.value,
                    _probesPerHop.value,
                ) == null
            ) {
                _uiState.value = TracerouteErrorText.tooLong()
                return
            }

            val params =
                TracerouteParams(
                    host = normalizedHost,
                    maxHops = _maxHops.value,
                    timeoutMs = _timeoutMs.value,
                    probesPerHop = _probesPerHop.value,
                    probeType = _probeType.value,
                    packetSize = _packetSize.value,
                )
            val session = operationSessionFactory(params)
            traceSession = session
            val trimmedHost = params.host
            val startedAtNanos = monotonicTimeNs()
            traceStartedAtNanos = startedAtNanos
            var accumulated = emptyList<HopResult>()
            var terminalError: TracerouteUiState.Error? = null
            var terminalEventReceived = false
            var unreachableFromThisDevice = false

            // Transition to Running immediately so the UI responds before the first hop
            // arrives.  If validation fails the first emission will overwrite this with Error.
            _uiState.value =
                TracerouteUiState.Running(
                    host = trimmedHost,
                    hops = emptyList(),
                    viewMode = selectedViewMode,
                    rawOutput = rawOutputOf(trimmedHost, emptyList()),
                    traceId = generation,
                )

            fun showRunning() {
                val current = _uiState.value as? TracerouteUiState.Running ?: return
                val ordered = TracerouteHops.ordered(accumulated)
                _uiState.value = current.copy(hops = ordered, rawOutput = rawOutputOf(current.host, ordered))
            }

            traceJob =
                viewModelScope.launch {
                    var recentSaved = false
                    try {
                        tracerouteUseCase(params, session).collect { result ->
                            // Discard any emission that was dispatched before the cancel took effect.
                            if (traceGeneration != generation || terminalEventReceived) return@collect
                            when (result) {
                                is TracerouteFlowResult.ValidationError -> {
                                    terminalEventReceived = true
                                    terminalError = TracerouteErrorText.validation(result)
                                }

                                is TracerouteFlowResult.Hop -> {
                                    accumulated = TracerouteHops.merge(accumulated, result.hop)
                                    showRunning()
                                    if (!recentSaved && validatedHost != null) {
                                        saveRecentHostAfterHop(validatedHost)
                                        recentSaved = true
                                    }
                                }

                                is TracerouteFlowResult.UnreachableFromThisDevice -> {
                                    unreachableFromThisDevice = true
                                }

                                is TracerouteFlowResult.HopEnriched -> {
                                    if (accumulated.none { it.hopNumber == result.hopNumber }) return@collect
                                    accumulated =
                                        accumulated.map { previous ->
                                            if (previous.hopNumber != result.hopNumber) return@map previous
                                            previous.copy(
                                                hostname = result.hostname ?: previous.hostname,
                                                geoLocation = result.geoLocation ?: previous.geoLocation,
                                            )
                                        }
                                    showRunning()
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        // Cancellation is resolved from the operation reason after collection cleanup.
                        throw e
                    } catch (e: Exception) {
                        terminalError = TracerouteErrorText.failure(e, trimmedHost)
                    } finally {
                        val cancellationReason = session.cancellationReason
                        val current = _uiState.value
                        if (traceSession === session) {
                            traceSession = null
                            traceJob = null
                            traceStartedAtNanos = null
                        }
                        val errorMessage = terminalError ?: TracerouteErrorText.interrupted(cancellationReason)
                        when {
                            cancellationReason == CancellationReason.DEADLINE_EXCEEDED -> {
                                val resultHost =
                                    when (current) {
                                        is TracerouteUiState.Running -> current.host
                                        is TracerouteUiState.Canceling -> current.host
                                        else -> trimmedHost
                                    }
                                val partialHops = TracerouteHops.ordered(accumulated)
                                _uiState.value =
                                    TracerouteUiState.Finished(
                                        result =
                                            buildResult(
                                                resultHost,
                                                partialHops,
                                                elapsedMsSince(startedAtNanos),
                                            ),
                                        viewMode = selectedViewMode,
                                        timeLimitReached = true,
                                        traceId = generation,
                                    )
                            }

                            current is TracerouteUiState.Canceling && current.operationId == generation &&
                                (
                                    cancellationReason == null || cancellationReason == CancellationReason.USER_STOP ||
                                        cancellationReason == CancellationReason.LIFECYCLE_PAUSE
                                ) -> {
                                _uiState.value =
                                    TracerouteUiState.Canceled(
                                        result = buildResult(current.host, current.hops, current.elapsedMs),
                                        viewMode = current.viewMode,
                                        stoppedWhenLeft = cancellationReason == CancellationReason.LIFECYCLE_PAUSE,
                                        traceId = current.traceId,
                                    )
                            }

                            cancellationReason != CancellationReason.LIFECYCLE_PAUSE &&
                                cancellationReason != CancellationReason.USER_STOP && errorMessage != null -> {
                                _uiState.value = errorMessage
                            }

                            cancellationReason == null && traceGeneration == generation &&
                                current is TracerouteUiState.Running -> {
                                _uiState.value =
                                    when {
                                        errorMessage != null -> {
                                            errorMessage
                                        }

                                        current.hops.isEmpty() -> {
                                            TracerouteErrorText.noHops(current.host)
                                        }

                                        else -> {
                                            TracerouteUiState.Finished(
                                                buildResult(
                                                    current.host,
                                                    TracerouteHops.complete(accumulated),
                                                    elapsedMsSince(startedAtNanos),
                                                    unreachableFromThisDevice,
                                                ),
                                                viewMode = current.viewMode,
                                                traceId = current.traceId,
                                            )
                                        }
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
            // Only a terminal state is kept: a trace that is still running is cancelled, not restored as running.
            val state = _uiState.value
            val finished =
                state.takeIf {
                    it is TracerouteUiState.Finished ||
                        it is TracerouteUiState.Canceled ||
                        it is TracerouteUiState.Error
                }
            sessionMemory.put(
                SESSION_KEY,
                TracerouteSessionSnapshot(
                    host = _host.value,
                    maxHops = _maxHops.value,
                    timeoutMs = _timeoutMs.value,
                    probesPerHop = _probesPerHop.value,
                    probeType = _probeType.value,
                    packetSize = _packetSize.value,
                    finished = finished,
                ),
            )
        }

        // ── Helpers ──────────────────────────────────────────────────────────────

        private fun buildResult(
            host: String,
            hops: List<HopResult>,
            totalMs: Long = 0L,
            unreachableFromThisDevice: Boolean = false,
        ): TracerouteResult {
            val ordered = hops.sortedBy { it.hopNumber }
            return TracerouteResult(
                host = host,
                resolvedIp = ordered.firstNotNullOfOrNull { it.resolvedDestinationIp },
                hops = ordered,
                rawOutput = rawOutputOf(host, ordered),
                totalTimeMs = totalMs,
                unreachableFromThisDevice = unreachableFromThisDevice,
            )
        }

        private fun rawOutputOf(
            host: String,
            hops: List<HopResult>,
        ): String = tracerouteRawOutput(host, hops, _maxHops.value, _probeType.value, _packetSize.value)

        private fun elapsedSinceStartMs(): Long = traceStartedAtNanos?.let(::elapsedMsSince) ?: 0L

        private fun elapsedMsSince(startedAtNanos: Long): Long {
            val elapsedNanos = (monotonicTimeNs() - startedAtNanos).coerceAtLeast(0L)
            return elapsedNanos / 1_000_000L
        }
    }
