package net.aieat.netswissknife.app.ui.screens.ping

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.core.domain.ContinuousPingParams
import net.aieat.netswissknife.core.domain.ContinuousPingUseCase
import net.aieat.netswissknife.core.domain.PingFlowResult
import net.aieat.netswissknife.core.domain.PingParams
import net.aieat.netswissknife.core.domain.PingSessionLogger
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingRequest
import net.aieat.netswissknife.core.network.ping.PingResult
import net.aieat.netswissknife.core.network.ping.PingStats
import net.aieat.netswissknife.core.network.ping.PingStatus
import net.aieat.netswissknife.core.network.ping.PingEngineKind
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.ping.PingOperation
import java.io.File
import javax.inject.Inject

internal class ContinuousPingLogWriter(
    scope: CoroutineScope,
    private val logger: PingSessionLogger,
    private val appendPacket: suspend (PingSessionLogger, Int, PingPacketResult) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val packets = Channel<Pair<Int, PingPacketResult>>(PACKET_QUEUE_CAPACITY)

    internal companion object {
        /** Bounds queued log data while allowing brief bursts during disk writes. */
        const val PACKET_QUEUE_CAPACITY = 64
    }

    @Volatile
    private var initializationFailed = false

    private val writerJob = scope.launch(dispatcher) {
        try {
            logger.init()
            for ((sequence, packet) in packets) {
                try {
                    appendPacket(logger, sequence, packet)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Logging is best-effort; the live ping results remain available.
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            initializationFailed = true
            packets.close()
        }
    }

    suspend fun append(sequence: Int, packet: PingPacketResult) {
        try {
            packets.send(sequence to packet)
        } catch (_: ClosedSendChannelException) {
            // Logging remains best-effort if initialization has failed or the writer
            // has already been closed. Cancellation still propagates to the producer.
        }
    }

    suspend fun closeAndJoin(): Boolean {
        packets.close()
        writerJob.join()
        return !initializationFailed && !writerJob.isCancelled
    }

    fun cancel() {
        packets.cancel()
        writerJob.cancel()
    }
}

private class ContinuousPingSession(
    val file: File,
    val logWriter: ContinuousPingLogWriter,
    val operationSession: OperationSession,
) {
    var producerJob: Job? = null
    var stopRequested: Boolean = false
}

private class ContinuousPingValidationException(val validationMessage: String) :
    RuntimeException(validationMessage)

/** All possible states for the Ping UI. */
sealed interface PingUiState {
    object Idle : PingUiState
    data class Running(
        val host: String,
        val packets: List<PingPacketResult>,
        val totalCount: Int,
        val isContinuous: Boolean = false,
        val pingsSent: Int = 0
    ) : PingUiState
    data class Finished(
        val result: PingResult,
        val showRaw: Boolean = false,
        val sessionLogFile: File? = null
    ) : PingUiState
    data class Error(val message: String) : PingUiState
}

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pingUseCase: PingUseCase,
    private val continuousPingUseCase: ContinuousPingUseCase,
    private val dataStore: DataStore<Preferences>,
    private val recentHostsRepository: RecentHostsRepository,
    private val linkInfoProvider: LinkInfoProvider = LinkInfoProvider { true },
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    companion object {
        private const val ROLLING_WINDOW = 100
        private const val NO_NETWORK_CONNECTION = "No network connection"
        private const val HANDOFF_CONSUMED_KEY = "pingHandoffConsumed"
        private const val HANDOFF_SOURCE_KEY = "pingHandoffSource"
        private const val EDITED_HOST_KEY = "editedHost"
    }

    private val _uiState = MutableStateFlow<PingUiState>(PingUiState.Idle)
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    // ── Form field state ─────────────────────────────────────────────────────

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val rawIntentArgument = savedStateHandle.get<String>("intent")
    private val hasIntentArgument = rawIntentArgument != null
    private val decodedIntent = rawIntentArgument?.let(ToolIntentCodec::decode)
    private val intentHost = (decodedIntent?.destination as? ToolDestination.HostTarget)
        ?.takeIf { it.tool == HostTool.PING }
    private val routeHost = savedStateHandle.get<String>("host")
    private val routeArgumentsMatch = !hasIntentArgument || (
        intentHost != null &&
            (routeHost == null || ToolHost.parse(routeHost)?.canonical == intentHost.host.canonical)
        )

    /** A present typed handoff must be valid and intended for Ping. */
    private val _hasInvalidHandoff = MutableStateFlow(
        hasIntentArgument && !routeArgumentsMatch && savedStateHandle.get<Boolean>("handoffRecovered") != true,
    )
    val hasInvalidHandoff: StateFlow<Boolean> = _hasInvalidHandoff.asStateFlow()
    private val _sourceContext = MutableStateFlow(
        if (savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true) {
            savedStateHandle.get<String>(HANDOFF_SOURCE_KEY)?.let { wireName ->
                ToolSource.entries.singleOrNull { it.wireName == wireName }
            }?.takeIf { intentHost != null && routeArgumentsMatch && decodedIntent?.source == it }
        } else {
            decodedIntent?.source?.takeIf { routeArgumentsMatch }
        },
    )
    val sourceContext: ToolSource? get() = _sourceContext.value
    val sourceContextState: StateFlow<ToolSource?> = _sourceContext.asStateFlow()

    private val _count = MutableStateFlow(10)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _timeoutMs = MutableStateFlow(2_000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _payloadBytes = MutableStateFlow(56)
    val payloadBytes: StateFlow<Int> = _payloadBytes.asStateFlow()

    private val _ttl = MutableStateFlow(64)
    val ttl: StateFlow<Int> = _ttl.asStateFlow()

    private val _intervalMs = MutableStateFlow(1_000)
    val intervalMs: StateFlow<Int> = _intervalMs.asStateFlow()

    private val _continuousMode = MutableStateFlow(false)
    val continuousMode: StateFlow<Boolean> = _continuousMode.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_PING_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pingJob: Job? = null
    private var pingOperationSession: OperationSession? = null
    private var continuousSession: ContinuousPingSession? = null
    private val retiringSessions = mutableSetOf<ContinuousPingSession>()

    internal var sessionLogFileFactory: () -> File = {
        File.createTempFile("ping_session_", ".csv")
    }
    internal var sessionLogAppendHook: suspend (PingSessionLogger, Int, PingPacketResult) -> Unit =
        { logger, sequence, packet -> logger.append(sequence, packet) }

    init {
        val handoffConsumed = savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true
        val restoredEdit = savedStateHandle.get<String>(EDITED_HOST_KEY)
        if (handoffConsumed) {
            // The NavBackStackEntry retains route arguments across recreation. Once
            // consumed, only the SavedStateHandle form snapshot is authoritative;
            // an empty string is an intentional clear and must not fall back to args.
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
            }
            savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        }
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _count.value = prefs[AppPreferenceKeys.DEFAULT_PING_COUNT] ?: 10
            _timeoutMs.value = prefs[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] ?: 2_000
        }
    }

    // ── User actions ─────────────────────────────────────────────────────────

    fun onHostChange(value: String) {
        _host.value = value
        savedStateHandle[EDITED_HOST_KEY] = value
        if (_hasInvalidHandoff.value && HostValidator.normalize(value) != null) {
            savedStateHandle["handoffRecovered"] = true
            _hasInvalidHandoff.value = false
        }
    }

    /** Clear a supplied handoff while recording the blank form as the consumed state. */
    fun clearPrefill() {
        if (_uiState.value is PingUiState.Running) return
        _host.value = ""
        _sourceContext.value = null
        savedStateHandle[EDITED_HOST_KEY] = ""
        savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
        savedStateHandle[HANDOFF_CONSUMED_KEY] = true
    }

    fun onCountChange(value: Int) { _count.value = value.coerceIn(1, 100) }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value.coerceIn(100, 30_000) }

    fun onPayloadSizeChange(value: Int) { _payloadBytes.value = value.coerceIn(0, 1_472) }

    fun onTtlChange(value: Int) { _ttl.value = value.coerceIn(1, 255) }

    fun onIntervalChange(value: Int) { _intervalMs.value = value.coerceIn(100, 10_000) }

    fun onToggleContinuous(enabled: Boolean) { _continuousMode.value = enabled }

    fun onToggleRawView() {
        val current = _uiState.value
        if (current is PingUiState.Finished) {
            _uiState.value = current.copy(showRaw = !current.showRaw)
        }
    }

    fun onClearResults() {
        pingOperationSession?.cancel(CancellationReason.USER_STOP)
        pingOperationSession = null
        pingJob?.cancel()
        pingJob = null
        discardContinuousSession()
        _uiState.value = PingUiState.Idle
    }

    fun onStop() {
        val current = _uiState.value
        if (current is PingUiState.Running && current.isContinuous) {
            stopContinuousPing(CancellationReason.USER_STOP)
            return
        }

        pingOperationSession?.cancel(CancellationReason.USER_STOP)
        pingOperationSession = null
        pingJob?.cancel()
        pingJob = null
        if (current is PingUiState.Running) {
            if (current.packets.isNotEmpty()) {
                _uiState.value = PingUiState.Finished(
                    buildResult(current.host, current.packets, current.totalCount)
                )
            } else {
                _uiState.value = PingUiState.Idle
            }
        }
    }

    fun onLifecycleStop() {
        val current = _uiState.value
        if (current is PingUiState.Running && current.isContinuous) {
            stopContinuousPing(CancellationReason.LIFECYCLE_PAUSE)
        }
    }

    fun onRetry() { startPing() }

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_PING_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_PING_HOSTS)
        }
    }

    fun startPing() {
        if (_continuousMode.value) startContinuousPing() else startNormalPing()
    }

    // ── Normal (bounded) ping ────────────────────────────────────────────────

    private fun startNormalPing() {
        pingOperationSession?.cancel(CancellationReason.USER_STOP)
        pingOperationSession = null
        pingJob?.cancel()
        pingJob = null
        discardContinuousSession()

        if (!linkInfoProvider.hasValidatedNetwork()) {
            _uiState.value = PingUiState.Error(NO_NETWORK_CONNECTION)
            return
        }

        val trimmedHost = HostValidator.normalize(_host.value) ?: _host.value.trim()
        val params = PingParams(
            host = trimmedHost,
            count = _count.value,
            timeoutMs = _timeoutMs.value,
            intervalMs = _intervalMs.value,
            payloadBytes = _payloadBytes.value,
            ttl = _ttl.value
        )
        _uiState.value = PingUiState.Running(
            host = trimmedHost, packets = emptyList(), totalCount = params.count
        )

        val operationSession = PingOperation.newSession(
            PingRequest(
                host = trimmedHost,
                count = params.count,
                timeoutMs = params.timeoutMs,
                intervalMs = params.intervalMs,
                payloadBytes = params.payloadBytes,
                ttl = params.ttl,
            )
        )
        pingOperationSession = operationSession
        pingJob = viewModelScope.launch {
            val accumulated = mutableListOf<PingPacketResult>()
            var savedToRecents = false

            try {
                pingUseCase(params, operationSession).collect { result ->
                    when (result) {
                        is PingFlowResult.ValidationError -> {
                            _uiState.value = PingUiState.Error(result.message)
                            return@collect
                        }
                        is PingFlowResult.Packet -> {
                            if (!savedToRecents) {
                                savedToRecents = true
                                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, trimmedHost)
                            }
                            accumulated.add(result.packet)
                            _uiState.value = PingUiState.Running(
                                host = trimmedHost,
                                packets = accumulated.toList(),
                                totalCount = params.count
                            )
                        }
                    }
                }

                val current = _uiState.value
                if (current is PingUiState.Running) {
                    _uiState.value = if (current.packets.isEmpty()) {
                        PingUiState.Error("No response received from $trimmedHost")
                    } else {
                        PingUiState.Finished(buildResult(current.host, current.packets, params.count))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PingUiState.Error(e.message ?: "Ping failed")
            } finally {
                if (pingOperationSession === operationSession) pingOperationSession = null
            }
        }
    }

    // ── Continuous ping ──────────────────────────────────────────────────────

    private fun startContinuousPing() {
        pingOperationSession?.cancel(CancellationReason.USER_STOP)
        pingOperationSession = null
        pingJob?.cancel()
        pingJob = null
        discardContinuousSession()

        if (!linkInfoProvider.hasValidatedNetwork()) {
            _uiState.value = PingUiState.Error(NO_NETWORK_CONNECTION)
            return
        }

        val trimmedHost = HostValidator.normalize(_host.value) ?: _host.value.trim()
        val params = ContinuousPingParams(
            host = trimmedHost,
            timeoutMs = _timeoutMs.value,
            intervalMs = _intervalMs.value,
            payloadBytes = _payloadBytes.value,
            ttl = _ttl.value
        )

        val logFile = sessionLogFileFactory()
        val logger = PingSessionLogger(logFile)
        val operationSession = PingOperation.newSession(
            PingRequest(
                host = trimmedHost,
                count = 0,
                timeoutMs = params.timeoutMs,
                intervalMs = params.intervalMs,
                payloadBytes = params.payloadBytes,
                ttl = params.ttl,
            )
        )
        val session = ContinuousPingSession(
            file = logFile,
            logWriter = ContinuousPingLogWriter(
                scope = viewModelScope,
                logger = logger,
                appendPacket = sessionLogAppendHook
            ),
            operationSession = operationSession,
        )
        continuousSession = session

        _uiState.value = PingUiState.Running(
            host = trimmedHost, packets = emptyList(), totalCount = 0,
            isContinuous = true, pingsSent = 0
        )

        val producer = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val window = ArrayDeque<PingPacketResult>(ROLLING_WINDOW)
            var seq = 0
            var savedToRecents = false

            try {
                continuousPingUseCase(params, operationSession).collect { result ->
                    when (result) {
                        is PingFlowResult.ValidationError -> {
                            throw ContinuousPingValidationException(result.message)
                        }
                        is PingFlowResult.Packet -> {
                            seq++
                            if (!savedToRecents) {
                                savedToRecents = true
                                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, trimmedHost)
                            }
                            session.logWriter.append(seq, result.packet)
                            if (window.size >= ROLLING_WINDOW) window.removeFirst()
                            window.addLast(result.packet)
                            _uiState.value = PingUiState.Running(
                                host = trimmedHost,
                                packets = window.toList(),
                                totalCount = 0,
                                isContinuous = true,
                                pingsSent = seq
                            )
                        }
                    }
                }

                val logAvailable = session.logWriter.closeAndJoin()
                val current = _uiState.value
                if (continuousSession === session && current is PingUiState.Running && current.isContinuous) {
                    finalizeContinuousSession(current, session, logAvailable)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ContinuousPingValidationException) {
                session.logWriter.closeAndJoin()
                if (continuousSession === session) {
                    continuousSession = null
                    session.file.delete()
                    _uiState.value = PingUiState.Error(e.validationMessage)
                }
            } catch (e: Exception) {
                session.logWriter.closeAndJoin()
                if (continuousSession === session) {
                    continuousSession = null
                    session.file.delete()
                    _uiState.value = PingUiState.Error(e.message ?: "Ping failed")
                }
            }
        }
        session.producerJob = producer
        pingJob = producer
        producer.start()
    }

    private fun finalizeContinuousSession(
        current: PingUiState.Running,
        session: ContinuousPingSession,
        logAvailable: Boolean
    ) {
        if (continuousSession !== session) return
        val pingsSent = current.pingsSent
        val result = buildResult(current.host, current.packets, pingsSent)
        val logFile = session.file.takeIf { pingsSent > 0 && logAvailable }
        _uiState.value = PingUiState.Finished(
            result = result,
            sessionLogFile = logFile
        )
        if (logFile == null) {
            continuousSession = null
            session.file.delete()
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private fun discardContinuousSession() {
        val session = continuousSession ?: return
        continuousSession = null
        session.stopRequested = true
        retiringSessions += session
        val producer = session.producerJob
        session.operationSession.cancel(CancellationReason.USER_STOP)
        producer?.cancel()
        viewModelScope.launch {
            try {
                producer?.join()
                session.logWriter.closeAndJoin()
            } finally {
                session.file.delete()
                retiringSessions.remove(session)
            }
        }
    }

    override fun onCleared() {
        pingOperationSession?.cancel(CancellationReason.LIFECYCLE_PAUSE)
        pingOperationSession = null
        val sessions = retiringSessions.toList() + listOfNotNull(continuousSession)
        continuousSession = null
        sessions.forEach { session ->
            session.operationSession.cancel(CancellationReason.LIFECYCLE_PAUSE)
            session.producerJob?.cancel()
            session.logWriter.cancel()
            session.file.delete()
        }
        retiringSessions.clear()
    }

    private fun stopContinuousPing(reason: CancellationReason) {
        val current = _uiState.value
        val session = continuousSession
        if (current !is PingUiState.Running || !current.isContinuous || session == null) return
        if (session.stopRequested) return
        session.stopRequested = true
        val producer = session.producerJob
        session.operationSession.cancel(reason)
        producer?.cancel()
        if (pingJob === producer) pingJob = null
        viewModelScope.launch {
            producer?.join()
            val logAvailable = session.logWriter.closeAndJoin()
            val latest = _uiState.value
            if (continuousSession === session && latest is PingUiState.Running && latest.isContinuous) {
                finalizeContinuousSession(latest, session, logAvailable)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildResult(host: String, packets: List<PingPacketResult>, totalCount: Int): PingResult {
        val stats = PingStats.compute(packets)
        val raw = buildRawOutput(host, packets, stats)
        return PingResult(
            host = host,
            packets = packets,
            stats = stats,
            rawOutput = raw,
            engine = packets.firstNotNullOfOrNull { it.engine }
                ?: observedEngine()
                ?: PingEngineKind.REACHABILITY,
            resolvedIp = packets.firstNotNullOfOrNull { it.fromIp }
        )
    }

    private fun buildRawOutput(
        host: String,
        packets: List<PingPacketResult>,
        stats: PingStats
    ): String = buildString {
        val firstPacket = packets.firstOrNull()
        val resolvedIp = firstPacket?.fromIp
        val engine = firstPacket?.engine ?: observedEngine()
        appendLine(
            "PING $host${resolvedIp?.let { " ($it)" } ?: ""}: " +
                "${_payloadBytes.value} data bytes, ttl ${_ttl.value}, " +
                "engine ${if (engine == PingEngineKind.ICMP) "ICMP" else "Reachability (ICMP/TCP fallback)"}"
        )
        appendLine()
        packets.forEach { p ->
            when (p.status) {
                PingStatus.SUCCESS ->
                    appendLine("${p.host}: probe_seq=${p.sequence} time=${p.rtTimeMs} ms")
                PingStatus.TIMEOUT ->
                    appendLine("Request timeout for probe_seq ${p.sequence}")
                PingStatus.UNREACHABLE ->
                    appendLine("Destination unreachable for probe_seq ${p.sequence}: ${p.errorMessage ?: "unknown reason"}")
                PingStatus.ERROR ->
                    appendLine("Error for probe_seq ${p.sequence}: ${p.errorMessage}")
            }
        }
        appendLine()
        appendLine("--- $host ping statistics ---")
        appendLine(
            "${stats.sent} packets transmitted, ${stats.received} packets received, " +
                "${"%.1f".format(stats.lossPercent)}% packet loss"
        )
        if (stats.received > 0) {
            appendLine(
                "round-trip min/avg/max/jitter = ${stats.minMs}/${"%.3f".format(stats.avgMs)}/" +
                    "${stats.maxMs}/${"%.3f".format(stats.jitterMs)} ms"
            )
        }
    }

    private fun observedEngine(): PingEngineKind? =
        runCatching { pingUseCase.lastEngineUsed?.value }.getOrNull()
}
