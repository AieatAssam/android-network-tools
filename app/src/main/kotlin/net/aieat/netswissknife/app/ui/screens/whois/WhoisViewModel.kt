package net.aieat.netswissknife.app.ui.screens.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.domain.WhoisParams
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import javax.inject.Inject

enum class HopStatus { PENDING, QUERYING, DONE, FAILED, SKIPPED }

data class HopUiState(
    val server: WhoisServer,
    val status: HopStatus,
    val queryTimeMs: Long? = null,
    val referral: String? = null
)

data class WhoisUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val hopStates: List<HopUiState> = emptyList(),
    val result: WhoisResult? = null,
    val error: String? = null,
    val showRawResponse: Boolean = false
)

@HiltViewModel
class WhoisViewModel @Inject constructor(
    private val whoisLookupUseCase: WhoisLookupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhoisUiState())
    val uiState: StateFlow<WhoisUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onToggleRawResponse() {
        _uiState.update { it.copy(showRawResponse = !it.showRawResponse) }
    }

    fun lookup() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        progressJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hopStates = emptyList(), result = null, error = null) }

        // Subscribe to hop progress to animate each server node in real time
        progressJob = viewModelScope.launch {
            whoisLookupUseCase.hopProgress.collect { hop ->
                _uiState.update { state ->
                    val existing = state.hopStates
                    // Mark previous QUERYING → DONE, then add new hop as DONE
                    val updated = existing.map { h ->
                        if (h.status == HopStatus.QUERYING) h.copy(status = HopStatus.DONE) else h
                    } + HopUiState(
                        server = hop.server,
                        status = if (hop.error != null) HopStatus.FAILED else HopStatus.DONE,
                        queryTimeMs = hop.queryTimeMs,
                        referral = hop.referral
                    )
                    state.copy(hopStates = updated)
                }
            }
        }

        viewModelScope.launch {
            // Yield so the progress-collection coroutine above can reach collect() first
            kotlinx.coroutines.yield()
            val result = whoisLookupUseCase(WhoisParams(query = query))
            progressJob?.cancel()
            progressJob = null
            _uiState.update { state ->
                when (result) {
                    is NetworkResult.Success -> state.copy(
                        isLoading = false,
                        result = result.data,
                        hopStates = result.data.hops.map { hop ->
                            HopUiState(
                                server = hop.server,
                                status = if (hop.error != null) HopStatus.FAILED else HopStatus.DONE,
                                queryTimeMs = hop.queryTimeMs,
                                referral = hop.referral
                            )
                        }
                    )
                    is NetworkResult.Error -> state.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
