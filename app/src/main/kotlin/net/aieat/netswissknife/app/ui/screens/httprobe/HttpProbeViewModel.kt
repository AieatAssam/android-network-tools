package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.HttpProbeParams
import net.aieat.netswissknife.core.domain.HttpProbeUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import javax.inject.Inject

data class HeaderEntry(val key: String = "", val value: String = "")

data class HttpProbeUiState(
    val url: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val customHeaders: List<HeaderEntry> = emptyList(),
    val body: String = "",
    val followRedirects: Boolean = true,
    val isLoading: Boolean = false,
    val result: HttpProbeResult? = null,
    val error: String? = null,
    val selectedTab: Int = 0,
    val headersExpanded: Boolean = false
)

@HiltViewModel
class HttpProbeViewModel @Inject constructor(
    private val useCase: HttpProbeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HttpProbeUiState())
    val uiState: StateFlow<HttpProbeUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) = _uiState.update { it.copy(url = url) }

    fun onMethodChange(method: HttpMethod) = _uiState.update { it.copy(method = method) }

    fun onBodyChange(body: String) = _uiState.update { it.copy(body = body) }

    fun onFollowRedirectsToggle() =
        _uiState.update { it.copy(followRedirects = !it.followRedirects) }

    fun onTabSelected(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    fun onToggleHeadersExpanded() =
        _uiState.update { it.copy(headersExpanded = !it.headersExpanded) }

    fun addHeader() =
        _uiState.update { it.copy(customHeaders = it.customHeaders + HeaderEntry()) }

    fun removeHeader(index: Int) =
        _uiState.update { it.copy(customHeaders = it.customHeaders.toMutableList().also { list -> list.removeAt(index) }) }

    fun updateHeaderKey(index: Int, key: String) = _uiState.update { state ->
        val updated = state.customHeaders.toMutableList()
        updated[index] = updated[index].copy(key = key)
        state.copy(customHeaders = updated)
    }

    fun updateHeaderValue(index: Int, value: String) = _uiState.update { state ->
        val updated = state.customHeaders.toMutableList()
        updated[index] = updated[index].copy(value = value)
        state.copy(customHeaders = updated)
    }

    fun send() {
        val state = _uiState.value
        if (state.url.isBlank() || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, result = null, error = null, selectedTab = 0) }

        viewModelScope.launch {
            val headers = state.customHeaders
                .filter { it.key.isNotBlank() }
                .map { it.key.trim() to it.value.trim() }

            val result = useCase(
                HttpProbeParams(
                    url = state.url.trim(),
                    method = state.method,
                    headers = headers,
                    body = state.body.takeIf { it.isNotBlank() && state.method.supportsBody },
                    followRedirects = state.followRedirects
                )
            )

            _uiState.update { current ->
                when (result) {
                    is NetworkResult.Success -> current.copy(
                        isLoading = false,
                        result = result.data,
                        selectedTab = 0
                    )
                    is NetworkResult.Error -> current.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
