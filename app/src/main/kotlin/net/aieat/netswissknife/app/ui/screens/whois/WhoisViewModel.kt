package net.aieat.netswissknife.app.ui.screens.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.domain.WhoisParams
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.whois.WhoisOperation
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
    private val whoisLookupUseCase: WhoisLookupUseCase,
    private val recentHostsRepository: RecentHostsRepository,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhoisUiState())
    val uiState: StateFlow<WhoisUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_WHOIS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var progressJob: Job? = null
    private var resultJob: Job? = null
    private var operationSession: OperationSession? = null

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onToggleRawResponse() {
        _uiState.update { it.copy(showRawResponse = !it.showRawResponse) }
    }

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_WHOIS_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_WHOIS_HOSTS)
        }
    }

    fun lookup() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_WHOIS_HOSTS, query)
        }
        cancelActiveOperation(CancellationReason.USER_STOP, updateState = false)
        // A prior lookup's own result coroutine must also be cancelled here — otherwise
        // a rapid re-submit (edit query, hit lookup again before the first WHOIS
        // referral chain finishes) leaves two independent result coroutines racing to
        // write _uiState, and whichever finishes last (not necessarily the newer query)
        // wins.
        resultJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hopStates = emptyList(), result = null, error = null) }

        val session = WhoisOperation.newSession()
        operationSession = session

        // Subscribe to hop progress to animate each server node in real time. The operation
        // id prevents buffered progress from a cancelled/replaced lookup leaking into this one.
        progressJob = viewModelScope.launch {
            whoisLookupUseCase.hopProgress.collect { hop ->
                if (operationSession !== session || hop.operationId != session.budget.operationId) return@collect
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

        resultJob = viewModelScope.launch {
            // Yield so the progress-collection coroutine above can reach collect() first
            kotlinx.coroutines.yield()
            val result = whoisLookupUseCase(WhoisParams(query = query), session)
            if (operationSession !== session) return@launch
            progressJob?.cancel()
            progressJob = null
            operationSession = null
            resultJob = null
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

    fun stopLookup() = cancelActiveOperation(CancellationReason.USER_STOP, updateState = true)

    /** Stop an in-flight lookup when this tool leaves the foreground. */
    fun onLifecyclePause() = cancelActiveOperation(CancellationReason.LIFECYCLE_PAUSE, updateState = true)

    private fun cancelActiveOperation(reason: CancellationReason, updateState: Boolean) {
        val session = operationSession
        if (session == null) return
        operationSession = null
        session.cancel(reason)
        progressJob?.cancel()
        progressJob = null
        resultJob?.cancel()
        resultJob = null
        if (updateState) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (reason == CancellationReason.LIFECYCLE_PAUSE) {
                        "Lookup stopped when WHOIS left the foreground. Retry when ready."
                    } else {
                        "Lookup stopped. Retry when ready."
                    },
                )
            }
        }
    }
}
