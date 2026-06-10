package net.aieat.netswissknife.app.ui.screens.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.SpeedTestUseCase
import net.aieat.netswissknife.core.network.speedtest.LatencySample
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestEvent
import net.aieat.netswissknife.core.network.speedtest.SpeedTestPhase
import net.aieat.netswissknife.core.network.speedtest.SpeedTestResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import javax.inject.Inject

/** All possible states for the Speed Test UI. */
sealed interface SpeedTestUiState {
    object Idle : SpeedTestUiState
    data class Running(
        val phase: SpeedTestPhase,
        val latencyStats: LatencyStats = LatencyStats.EMPTY,
        val downloadSamples: List<ThroughputSample> = emptyList(),
        val downloadResult: ThroughputResult? = null,
        val uploadSamples: List<ThroughputSample> = emptyList()
    ) : SpeedTestUiState
    data class Finished(val result: SpeedTestResult) : SpeedTestUiState
    data class Error(val phase: SpeedTestPhase, val message: String) : SpeedTestUiState
}

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val speedTestUseCase: SpeedTestUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpeedTestUiState>(SpeedTestUiState.Idle)
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    fun startTest() {
        testJob?.cancel()

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
            speedTestUseCase().collect { event ->
                when (event) {
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
                                upload = event.result
                            )
                        )
                    }
                    is SpeedTestEvent.Failed -> {
                        _uiState.value = SpeedTestUiState.Error(event.phase, event.message)
                    }
                }
            }
        }
    }

    fun onCancel() {
        testJob?.cancel()
        testJob = null
        _uiState.value = SpeedTestUiState.Idle
    }

    fun onRetry() = startTest()

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}
