package net.aieat.netswissknife.app.ui.screens.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val useCase: TlsInspectorUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TlsInspectorUiState())
    val uiState: StateFlow<TlsInspectorUiState> = _uiState.asStateFlow()

    fun onHostChange(value: String) {
        _uiState.value = _uiState.value.copy(host = value, error = null)
    }

    fun onPortChange(value: String) {
        _uiState.value = _uiState.value.copy(port = value, error = null)
    }

    fun inspect() {
        val state = _uiState.value
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
