package net.aieat.netswissknife.app.ui.screens.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.SpeedTestUseCase
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.core.network.speedtest.LatencySample
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestPhase
import net.aieat.netswissknife.core.network.speedtest.SpeedTestResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import net.aieat.netswissknife.core.network.speedtest.ServerInfo
import net.aieat.netswissknife.core.network.speedtest.SpeedTestConfig
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.speedtest.SpeedTestOperation
import javax.inject.Inject

/** All possible states for the Speed Test UI. */
sealed interface SpeedTestUiState {
    object Idle : SpeedTestUiState
    data class Running(
        val phase: SpeedTestPhase,
        val latencyStats: LatencyStats = LatencyStats.EMPTY,
        val serverInfo: ServerInfo? = null,
        val downloadSamples: List<ThroughputSample> = emptyList(),
        val downloadResult: ThroughputResult? = null,
        val uploadSamples: List<ThroughputSample> = emptyList(),
        val loadedLatencyDown: LatencyStats = LatencyStats.EMPTY,
        val loadedLatencyUp: LatencyStats = LatencyStats.EMPTY
    ) : SpeedTestUiState
    data class Finished(val result: SpeedTestResult) : SpeedTestUiState
    data class Error(val phase: SpeedTestPhase, val message: String) : SpeedTestUiState
}

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val speedTestUseCase: SpeedTestUseCase,
    private val dataStore: DataStore<Preferences>,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpeedTestUiState>(SpeedTestUiState.Idle)
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    private val _config = MutableStateFlow(SpeedTestConfig())
    val config: StateFlow<SpeedTestConfig> = _config.asStateFlow()
    private val configInitialized = CompletableDeferred<Unit>()
    private var downloadStreamsOverride: Int? = null
    private var uploadStreamsOverride: Int? = null

    private var testJob: Job? = null
    private var operationSession: OperationSession? = null

    init {
        viewModelScope.launch {
            try {
                val preferences = dataStore.data.first()
                _config.value = SpeedTestConfig(
                    downloadStreams = downloadStreamsOverride
                        ?: preferences[AppPreferenceKeys.SPEEDTEST_DOWN_STREAMS]
                        ?: SpeedTestConfig().downloadStreams,
                    uploadStreams = uploadStreamsOverride
                        ?: preferences[AppPreferenceKeys.SPEEDTEST_UP_STREAMS]
                        ?: SpeedTestConfig().uploadStreams,
                ).normalized()
                configInitialized.complete(Unit)
            } catch (cancelled: CancellationException) {
                configInitialized.cancel(cancelled)
                throw cancelled
            } catch (failure: Exception) {
                configInitialized.completeExceptionally(failure)
            }
        }
    }

    fun startTest() {
        operationSession?.cancel(CancellationReason.USER_STOP)
        operationSession = null
        testJob?.cancel()
        var session: OperationSession? = null

        val latencySamples = mutableListOf<LatencySample>()
        val downloadSamples = mutableListOf<ThroughputSample>()
        val uploadSamples = mutableListOf<ThroughputSample>()
        var current = SpeedTestUiState.Running(phase = SpeedTestPhase.LATENCY)

        _uiState.value = current

        fun emit(next: SpeedTestUiState.Running) {
            current = next
            _uiState.value = next
        }

        testJob = viewModelScope.launch {
            try {
                // Wait for persisted settings, then use one snapshot for both capacity and run.
                configInitialized.await()
                val runConfig = _config.value.normalized()
                _config.value = runConfig
                val runSession = SpeedTestOperation.newSession(runConfig)
                session = runSession
                operationSession = runSession
                var serverInfo: ServerInfo? = null
                var loadedDown = mutableListOf<LatencySample>()
                var loadedUp = mutableListOf<LatencySample>()
                speedTestUseCase(runSession, runConfig).collect { event ->
                    when (event) {
                        is SpeedTestEvent.ServerInfoReceived -> {
                            serverInfo = event.info
                            emit(current.copy(serverInfo = event.info))
                        }
                        is SpeedTestEvent.LatencyProgress -> {
                            latencySamples.add(event.sample)
                            emit(current.copy(
                                phase = SpeedTestPhase.LATENCY,
                                latencyStats = LatencyStats.compute(latencySamples)
                            ))
                        }
                        is SpeedTestEvent.LatencyFinished -> {
                            emit(current.copy(phase = SpeedTestPhase.DOWNLOAD, latencyStats = event.stats))
                        }
                        is SpeedTestEvent.DownloadProgress -> {
                            downloadSamples.add(event.sample)
                            emit(current.copy(downloadSamples = downloadSamples.toList()))
                        }
                        is SpeedTestEvent.DownloadFinished -> {
                            emit(current.copy(phase = SpeedTestPhase.UPLOAD, downloadResult = event.result))
                        }
                        is SpeedTestEvent.LoadedLatencySample -> {
                            val list = if (event.phase == SpeedTestPhase.DOWNLOAD) loadedDown else loadedUp
                            list.add(LatencySample(list.size + 1, event.rttMs))
                            emit(if (event.phase == SpeedTestPhase.DOWNLOAD) {
                                current.copy(loadedLatencyDown = LatencyStats.compute(list))
                            } else current.copy(loadedLatencyUp = LatencyStats.compute(list)))
                        }
                        is SpeedTestEvent.LoadedLatencyFinished -> {
                            if (event.phase == SpeedTestPhase.DOWNLOAD) loadedDown = event.stats.samples.toMutableList()
                            else loadedUp = event.stats.samples.toMutableList()
                            emit(if (event.phase == SpeedTestPhase.DOWNLOAD) {
                                current.copy(loadedLatencyDown = event.stats)
                            } else current.copy(loadedLatencyUp = event.stats))
                        }
                        is SpeedTestEvent.UploadProgress -> {
                            uploadSamples.add(event.sample)
                            emit(current.copy(uploadSamples = uploadSamples.toList()))
                        }
                        is SpeedTestEvent.UploadFinished -> {
                            val download = checkNotNull(current.downloadResult) {
                                "UploadFinished received before DownloadFinished"
                            }
                            _uiState.value = SpeedTestUiState.Finished(
                                SpeedTestResult(
                                    latency = current.latencyStats,
                                    download = download,
                                    upload = event.result,
                                    serverInfo = serverInfo,
                                    loadedLatencyDown = current.loadedLatencyDown,
                                    loadedLatencyUp = current.loadedLatencyUp,
                                    config = runConfig
                                )
                            )
                        }
                        is SpeedTestEvent.Failed -> {
                            _uiState.value = SpeedTestUiState.Error(event.phase, event.message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SpeedTestUiState.Error(current.phase, e.message ?: "Unknown error")
            } finally {
                if (session != null && operationSession === session) operationSession = null
            }
        }
    }

    fun onCancel() {
        operationSession?.cancel(CancellationReason.USER_STOP)
        operationSession = null
        testJob?.cancel()
        testJob = null
        _uiState.value = SpeedTestUiState.Idle
    }

    fun onRetry() = startTest()

    fun setDownloadStreams(value: Int) {
        val streams = value.coerceIn(1, 8)
        downloadStreamsOverride = streams
        _config.value = _config.value.copy(downloadStreams = streams)
        viewModelScope.launch { dataStore.edit { it[AppPreferenceKeys.SPEEDTEST_DOWN_STREAMS] = streams } }
    }

    fun setUploadStreams(value: Int) {
        val streams = value.coerceIn(1, 4)
        uploadStreamsOverride = streams
        _config.value = _config.value.copy(uploadStreams = streams)
        viewModelScope.launch { dataStore.edit { it[AppPreferenceKeys.SPEEDTEST_UP_STREAMS] = streams } }
    }

    override fun onCleared() {
        operationSession?.cancel(CancellationReason.LIFECYCLE_PAUSE)
        operationSession = null
        testJob?.cancel()
    }
}
