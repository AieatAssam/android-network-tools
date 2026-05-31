package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import javax.inject.Inject

data class MdnsDiscoveryUiState(
    val isScanning: Boolean = false,
    val services: List<DiscoveredService> = emptyList(),
    val servicesByType: Map<String, List<DiscoveredService>> = emptyMap(),
    val error: String? = null,
    val elapsedMs: Long = 0,
    val totalFound: Int = 0,
    val scanComplete: Boolean = false
)

@HiltViewModel
class MdnsDiscoveryViewModel @Inject constructor(
    private val useCase: MdnsDiscoveryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MdnsDiscoveryUiState())
    val uiState: StateFlow<MdnsDiscoveryUiState> = _uiState

    private var scanJob: Job? = null
    private var timerJob: Job? = null

    fun startScan(timeoutMs: Long = 5_000L) {
        if (_uiState.value.isScanning) return

        _uiState.value = MdnsDiscoveryUiState(isScanning = true)

        val startTime = System.currentTimeMillis()

        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                _uiState.update { it.copy(elapsedMs = System.currentTimeMillis() - startTime) }
            }
        }

        scanJob = viewModelScope.launch {
            try {
                useCase(timeoutMs).collect { update ->
                    when (update) {
                        is MdnsUpdate.ServiceFound -> {
                            _uiState.update { state ->
                                val existing = state.services.indexOfFirst { it.instanceName == update.service.instanceName }
                                val updated = if (existing >= 0) {
                                    state.services.toMutableList().also { it[existing] = update.service }
                                } else {
                                    state.services + update.service
                                }
                                val byType = updated.groupBy { it.serviceType }.toSortedMap()
                                state.copy(services = updated, servicesByType = byType)
                            }
                        }
                        is MdnsUpdate.DiscoveryComplete -> {
                            _uiState.update { it.copy(
                                isScanning = false,
                                scanComplete = true,
                                totalFound = update.totalFound
                            )}
                            stopTimer()
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, error = e.message ?: "Discovery failed") }
                stopTimer()
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        stopTimer()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun reset() {
        stopScan()
        _uiState.value = MdnsDiscoveryUiState()
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
