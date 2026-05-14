package net.aieat.netswissknife.app.ui.screens.ping

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.ContinuousPingParams
import net.aieat.netswissknife.core.domain.ContinuousPingUseCase
import net.aieat.netswissknife.core.domain.PingFlowResult
import net.aieat.netswissknife.core.domain.PingParams
import net.aieat.netswissknife.core.domain.PingSessionLogger
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingResult
import net.aieat.netswissknife.core.network.ping.PingStats
import net.aieat.netswissknife.core.network.ping.PingStatus
import java.io.File
import javax.inject.Inject

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
    private val recentHostsRepository: RecentHostsRepository
) : ViewModel() {

    companion object {
        private const val ROLLING_WINDOW = 100
    }

    private val _uiState = MutableStateFlow<PingUiState>(PingUiState.Idle)
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    // ── Form field state ─────────────────────────────────────────────────────

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _count = MutableStateFlow(10)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _timeoutMs = MutableStateFlow(2_000)
    val timeoutMs: StateFlow<Int> = _timeoutMs.asStateFlow()

    private val _packetSize = MutableStateFlow(56)
    val packetSize: StateFlow<Int> = _packetSize.asStateFlow()

    private val _continuousMode = MutableStateFlow(false)
    val continuousMode: StateFlow<Boolean> = _continuousMode.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_PING_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pingJob: Job? = null
    private var sessionLogFile: File? = null

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _count.value = prefs[AppPreferenceKeys.DEFAULT_PING_COUNT] ?: 10
            _timeoutMs.value = prefs[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] ?: 2_000
        }
    }

    // ── User actions ─────────────────────────────────────────────────────────

    fun onHostChange(value: String) { _host.value = value }

    fun onCountChange(value: Int) { _count.value = value.coerceIn(1, 100) }

    fun onTimeoutChange(value: Int) { _timeoutMs.value = value.coerceIn(100, 30_000) }

    fun onPacketSizeChange(value: Int) { _packetSize.value = value.coerceIn(1, 65_507) }

    fun onToggleContinuous(enabled: Boolean) { _continuousMode.value = enabled }

    fun onToggleRawView() {
        val current = _uiState.value
        if (current is PingUiState.Finished) {
            _uiState.value = current.copy(showRaw = !current.showRaw)
        }
    }

    fun onClearResults() {
        pingJob?.cancel()
        pingJob = null
        cleanupSessionFile()
        _uiState.value = PingUiState.Idle
    }

    fun onStop() {
        pingJob?.cancel()
        pingJob = null
        val current = _uiState.value
        if (current is PingUiState.Running) {
            if (current.isContinuous) {
                finalizeContinuousSession(current)
            } else if (current.packets.isNotEmpty()) {
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
            onStop()
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
        pingJob?.cancel()

        val params = PingParams(
            host = _host.value,
            count = _count.value,
            timeoutMs = _timeoutMs.value,
            packetSize = _packetSize.value
        )
        val trimmedHost = params.host.trim()

        _uiState.value = PingUiState.Running(
            host = trimmedHost, packets = emptyList(), totalCount = params.count
        )

        pingJob = viewModelScope.launch {
            val accumulated = mutableListOf<PingPacketResult>()
            var savedToRecents = false

            pingUseCase(params).collect { result ->
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
        }
    }

    // ── Continuous ping ──────────────────────────────────────────────────────

    private fun startContinuousPing() {
        pingJob?.cancel()
        cleanupSessionFile()

        val trimmedHost = _host.value.trim()
        val params = ContinuousPingParams(
            host = trimmedHost,
            timeoutMs = _timeoutMs.value,
            packetSize = _packetSize.value
        )

        val logFile = File.createTempFile("ping_session_", ".csv")
        sessionLogFile = logFile
        val logger = PingSessionLogger(logFile)

        _uiState.value = PingUiState.Running(
            host = trimmedHost, packets = emptyList(), totalCount = 0,
            isContinuous = true, pingsSent = 0
        )

        pingJob = viewModelScope.launch {
            // File writes are dispatched to IO via a channel so the collect loop
            // is never blocked on disk. The channel is cancelled with the pingJob.
            val logChannel = Channel<Pair<Int, PingPacketResult>>(Channel.UNLIMITED)
            launch(Dispatchers.IO) {
                logger.init()
                for ((seq, packet) in logChannel) {
                    runCatching { logger.append(seq, packet) }
                }
            }

            val window = ArrayDeque<PingPacketResult>(ROLLING_WINDOW)
            var seq = 0
            var savedToRecents = false

            continuousPingUseCase(params).collect { result ->
                when (result) {
                    is PingFlowResult.ValidationError -> {
                        logChannel.close()
                        cleanupSessionFile()
                        _uiState.value = PingUiState.Error(result.message)
                        return@collect
                    }
                    is PingFlowResult.Packet -> {
                        seq++
                        if (!savedToRecents) {
                            savedToRecents = true
                            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, trimmedHost)
                        }
                        logChannel.trySend(Pair(seq, result.packet))
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

            logChannel.close()
            val current = _uiState.value
            if (current is PingUiState.Running && current.isContinuous) {
                finalizeContinuousSession(current)
            }
        }
    }

    private fun finalizeContinuousSession(current: PingUiState.Running) {
        val file = sessionLogFile
        val pingsSent = current.pingsSent
        val result = buildResult(current.host, current.packets, pingsSent)
        _uiState.value = PingUiState.Finished(
            result = result,
            sessionLogFile = if (pingsSent > 0) file else null
        )
        if (pingsSent == 0) cleanupSessionFile()
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private fun cleanupSessionFile() {
        sessionLogFile?.delete()
        sessionLogFile = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupSessionFile()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildResult(host: String, packets: List<PingPacketResult>, totalCount: Int): PingResult {
        val stats = PingStats.compute(packets)
        val raw = buildRawOutput(host, packets, stats, _packetSize.value)
        return PingResult(host = host, packets = packets, stats = stats, rawOutput = raw)
    }

    private fun buildRawOutput(
        host: String,
        packets: List<PingPacketResult>,
        stats: PingStats,
        packetSize: Int
    ): String = buildString {
        appendLine("PING $host: $packetSize data bytes")
        appendLine()
        packets.forEach { p ->
            when (p.status) {
                PingStatus.SUCCESS ->
                    appendLine("${packetSize + 8} bytes from ${p.host}: icmp_seq=${p.sequence} ttl=64 time=${p.rtTimeMs} ms")
                PingStatus.TIMEOUT ->
                    appendLine("Request timeout for icmp_seq ${p.sequence}")
                PingStatus.ERROR ->
                    appendLine("Error for icmp_seq ${p.sequence}: ${p.errorMessage}")
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
}
