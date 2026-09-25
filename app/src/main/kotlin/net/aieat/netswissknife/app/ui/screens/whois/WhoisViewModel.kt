package net.aieat.netswissknife.app.ui.screens.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import net.aieat.netswissknife.core.network.whois.WhoisProtocol
import net.aieat.netswissknife.core.network.whois.WhoisQueryTypeDetector
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
    val protocol: WhoisProtocol = WhoisProtocol.AUTO,
    val isLoading: Boolean = false,
    val isCanceling: Boolean = false,
    val isCanceled: Boolean = false,
    val isLifecyclePaused: Boolean = false,
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
    private var cancellationJob: Job? = null
    private var pendingReplacementQuery: String? = null
    private var cancellationReason: CancellationReason = CancellationReason.USER_STOP
    private var operationSession: OperationSession? = null

    fun onQueryChange(value: String) {
        _uiState.update { state ->
            val effectiveQueryChanged = lookupIdentity(value) != lookupIdentity(state.query)
            if (state.isCanceled && effectiveQueryChanged) {
                // The canceled card and its partial relay chain belong to the query that
                // was stopped. Once the user changes the input, Retry must not appear to
                // retry that old operation while actually submitting a different query.
                state.copy(
                    query = value,
                    isCanceled = false,
                    isLifecyclePaused = false,
                    hopStates = emptyList(),
                    result = null,
                    error = null,
                )
            } else {
                state.copy(query = value)
            }
        }
    }

    fun onProtocolChange(protocol: WhoisProtocol) {
        if (_uiState.value.isLoading || _uiState.value.isCanceling) return
        if (_uiState.value.protocol == protocol) return
        _uiState.update {
            it.copy(
                protocol = protocol,
                hopStates = emptyList(),
                result = null,
                error = null,
                isCanceled = false,
                isLifecyclePaused = false,
            )
        }
    }

    private fun lookupIdentity(query: String): String =
        WhoisQueryTypeDetector.normalize(query)?.let { "${it.type}:${it.value}" } ?: query.trim()

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
        // Keep the current operation session fenced until its cancellation cleanup
        // finishes. A second lookup during that window could otherwise overlap it.
        if (_uiState.value.isCanceling) return
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_WHOIS_HOSTS, query)
        }
        val activeSession = operationSession
        if (activeSession != null) {
            replaceActiveOperation(query, activeSession)
            return
        }
        startLookup(query)
    }

    private fun startLookup(query: String) {
        val protocol = _uiState.value.protocol
        _uiState.update {
            it.copy(
                isLoading = true,
                isCanceling = false,
                isCanceled = false,
                isLifecyclePaused = false,
                hopStates = emptyList(),
                result = null,
                error = null
            )
        }

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
            val result = whoisLookupUseCase(WhoisParams(query = query, protocol = protocol), session)
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
    fun onLifecyclePause() {
        if (_uiState.value.isCanceling) {
            // If a replacement was requested while cleanup ran, do not start it
            // after the screen has left the foreground. Finish this as a pause.
            pendingReplacementQuery = null
            cancellationReason = CancellationReason.LIFECYCLE_PAUSE
            return
        }
        cancelActiveOperation(CancellationReason.LIFECYCLE_PAUSE, updateState = true)
    }

    private fun cancelActiveOperation(reason: CancellationReason, updateState: Boolean) {
        if (_uiState.value.isCanceling) return
        val session = operationSession
        if (session == null) return
        val (oldProgressJob, oldResultJob) = detachOperation(session, reason)
        cancellationReason = reason
        pendingReplacementQuery = null
        if (updateState) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isCanceling = true,
                    isCanceled = false,
                    isLifecyclePaused = false,
                    error = null,
                )
            }
        }
        oldProgressJob?.cancel()
        oldResultJob?.cancel()

        if (updateState) {
            cancellationJob = viewModelScope.launch {
                oldProgressJob?.cancelAndJoin()
                oldResultJob?.cancelAndJoin()
                finishCanceledState()
                cancellationJob = null
            }
        }
    }

    private fun replaceActiveOperation(query: String, session: OperationSession) {
        val (oldProgressJob, oldResultJob) = detachOperation(session, CancellationReason.USER_STOP)
        cancellationReason = CancellationReason.USER_STOP
        pendingReplacementQuery = query
        _uiState.update {
            it.copy(
                isLoading = true,
                isCanceling = true,
                isCanceled = false,
                isLifecyclePaused = false,
                hopStates = emptyList(),
                result = null,
                error = null,
            )
        }
        oldProgressJob?.cancel()
        oldResultJob?.cancel()
        cancellationJob = viewModelScope.launch {
            oldProgressJob?.cancelAndJoin()
            oldResultJob?.cancelAndJoin()
            val replacement = pendingReplacementQuery
            pendingReplacementQuery = null
            cancellationJob = null
            if (replacement != null) startLookup(replacement) else finishCanceledState()
        }
    }

    private fun detachOperation(
        session: OperationSession,
        reason: CancellationReason,
    ): Pair<Job?, Job?> {
        operationSession = null
        session.cancel(reason)
        val oldProgressJob = progressJob
        val oldResultJob = resultJob
        progressJob = null
        resultJob = null
        return oldProgressJob to oldResultJob
    }

    private fun finishCanceledState() {
        _uiState.update { state ->
            if (!state.isCanceling) state else state.copy(
                isLoading = false,
                isCanceling = false,
                isCanceled = true,
                isLifecyclePaused = cancellationReason == CancellationReason.LIFECYCLE_PAUSE,
                error = null,
            )
        }
    }
}
