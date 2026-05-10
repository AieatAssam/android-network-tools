package net.aieat.netswissknife.app.ui.screens.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.TlsInspectorParams
import net.aieat.netswissknife.core.domain.TlsInspectorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import javax.inject.Inject

data class TlsInspectorUiState(
    val host: String = "",
    val port: String = "443",
    val isLoading: Boolean = false,
    val result: TlsInspectorResult? = null,
    val error: String? = null
)

@HiltViewModel
class TlsInspectorViewModel @Inject constructor(
    private val useCase: TlsInspectorUseCase,
    private val recentHostsRepository: RecentHostsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TlsInspectorUiState())
    val uiState: StateFlow<TlsInspectorUiState> = _uiState.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_TLS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onHostChange(value: String) {
        _uiState.value = _uiState.value.copy(host = value, error = null)
    }

    fun onPortChange(value: String) {
        _uiState.value = _uiState.value.copy(port = value, error = null)
    }

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_TLS_HOSTS)
        }
    }

    fun inspect() {
        val state = _uiState.value
        viewModelScope.launch {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, state.host)
        }
        _uiState.value = state.copy(isLoading = true, error = null, result = null)
        viewModelScope.launch {
            val params = TlsInspectorParams(
                host      = state.host,
                port      = state.port.toIntOrNull() ?: 443,
                timeoutMs = 10_000
            )
            when (val res = useCase(params)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result    = res.data,
                    error     = null
                )
                is NetworkResult.Error   -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result    = null,
                    error     = res.message
                )
            }
        }
    }
}
