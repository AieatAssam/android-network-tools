package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.HttpProbeParams
import net.aieat.netswissknife.core.domain.HttpProbeUseCase
import net.aieat.netswissknife.core.domain.validateHttpProbeUrl
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeOperation
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.CrossOriginEntityReplay
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.util.UUID
import javax.inject.Inject

data class HeaderEntry(val key: String = "", val value: String = "")

data class PendingEntityReplayApproval(
    val runId: String,
    val approvalId: String,
    val destinationUrl: String,
    val method: HttpMethod,
    val statusCode: Int
)

data class HttpProbeUiState(
    val url: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val customHeaders: List<HeaderEntry> = emptyList(),
    val body: String = "",
    val followRedirects: Boolean = true,
    val isLoading: Boolean = false,
    val result: HttpProbeResult? = null,
    val error: String? = null,
    val pendingEntityReplayApproval: PendingEntityReplayApproval? = null,
    val selectedTab: Int = 0,
    val headersExpanded: Boolean = false
)

@HiltViewModel
class HttpProbeViewModel @Inject constructor(
    private val useCase: HttpProbeUseCase,
    private val recentHostsRepository: RecentHostsRepository,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val routeHost = savedStateHandle.get<String>("host")
    private val rawIntentArgument = savedStateHandle.get<String>("intent")
    private val typedIntent = rawIntentArgument?.let(ToolIntentCodec::decode)
    private val handoffTarget = (typedIntent?.destination as? ToolDestination.HostTarget)
        ?.takeIf { target ->
            target.tool == HostTool.HTTP && target.port != null &&
            routeHost != null && ToolHost.parse(routeHost)?.canonical == target.host.canonical
        }
    private val _hasInvalidHandoff = MutableStateFlow(
        (rawIntentArgument != null || routeHost != null) && handoffTarget == null &&
            savedStateHandle.get<Boolean>(HANDOFF_RECOVERED_KEY) != true,
    )
    val hasInvalidHandoff: StateFlow<Boolean> = _hasInvalidHandoff.asStateFlow()
    private val _sourceContext = MutableStateFlow(
        if (savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true) {
            savedStateHandle.get<String>(HANDOFF_SOURCE_KEY)?.let { wireName ->
                ToolSource.entries.singleOrNull { it.wireName == wireName }
            }?.takeIf { handoffTarget != null && typedIntent?.source == it }
        } else {
            handoffTarget?.let { typedIntent?.source }
        },
    )
    val sourceContext: ToolSource? get() = _sourceContext.value
    val sourceContextState: StateFlow<ToolSource?> = _sourceContext.asStateFlow()

    private val _uiState = MutableStateFlow(
        HttpProbeUiState(
            url = if (savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true) {
                savedStateHandle.get<String>(EDITED_URL_KEY).orEmpty()
            } else {
                savedStateHandle.get<String>(EDITED_URL_KEY)
                    ?: if (_hasInvalidHandoff.value) "" else handoffTarget?.let(::httpUrlForTarget).orEmpty()
            },
        ),
    )
    val uiState: StateFlow<HttpProbeUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status
    private data class ActiveReplayDecision(
        val runId: String,
        val approvalId: String,
        val decision: CompletableDeferred<Boolean>
    )

    private var activeReplayDecision: ActiveReplayDecision? = null
    private var operationSession: OperationSession? = null

    init {
        if (savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) != true) {
            // NavBackStackEntry arguments remain present after recreation. Snapshot the
            // initial form and provenance once so a later clear/edit cannot be replaced
            // by the original handoff route.
            savedStateHandle[EDITED_URL_KEY] = _uiState.value.url
            _sourceContext.value?.let { source ->
                savedStateHandle[HANDOFF_SOURCE_KEY] = source.wireName
            } ?: savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
            savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        }
        addCloseable(LIFECYCLE_CLOSEABLE_KEY, AutoCloseable {
            cancelRequest(CancellationReason.LIFECYCLE_PAUSE)
        })
    }

    val recentHosts: StateFlow<List<String>> = flow {
        recentHostsRepository.sanitizeRecents(
            AppPreferenceKeys.RECENT_HTTP_HOSTS,
            ::safeHttpRecentOrigin
        )
        emitAll(recentHostsRepository.getRecents(AppPreferenceKeys.RECENT_HTTP_HOSTS))
    }
        .map { entries -> entries.mapNotNull(::safeHttpRecentOrigin).distinct().take(5) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(url = url) }
        savedStateHandle[EDITED_URL_KEY] = url
        if (_hasInvalidHandoff.value && validateHttpProbeUrl(url) == null) {
            savedStateHandle[HANDOFF_RECOVERED_KEY] = true
            _hasInvalidHandoff.value = false
        }
    }

    /** Clears the incoming handoff and its provenance; the blank URL survives recreation. */
    fun clearPrefill() {
        if (_uiState.value.isLoading) return
        _sourceContext.value = null
        savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
        savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        onUrlChange("")
    }

    fun onMethodChange(method: HttpMethod) = _uiState.update { it.copy(method = method) }

    fun onBodyChange(body: String) = _uiState.update { it.copy(body = body) }

    fun onFollowRedirectsToggle() =
        _uiState.update { it.copy(followRedirects = !it.followRedirects) }

    fun onTabSelected(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    fun respondToEntityReplayApproval(runId: String, approvalId: String, approved: Boolean) {
        val active = activeReplayDecision ?: return
        val pending = _uiState.value.pendingEntityReplayApproval
        if (active.runId != runId || active.approvalId != approvalId || pending == null ||
            pending.runId != runId || pending.approvalId != approvalId
        ) return
        active.decision.complete(approved)
    }

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

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_HTTP_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_HTTP_HOSTS)
        }
    }

    fun send() {
        val state = _uiState.value
        if (state.url.isBlank() || state.isLoading) return

        safeHttpRecentOrigin(state.url)?.let { safeOrigin ->
            viewModelScope.launch {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_HTTP_HOSTS, safeOrigin)
            }
        }
        _uiState.update { it.copy(isLoading = true, result = null, error = null, selectedTab = 0) }
        val runId = UUID.randomUUID().toString()
        val session = HttpProbeOperation.newSession()
        operationSession = session

        viewModelScope.launch {
            val headers = state.customHeaders
                .filter { it.key.isNotBlank() }
                .map { it.key.trim() to it.value.trim() }

            try {
                val result = useCase(
                    HttpProbeParams(
                        url = state.url.trim(),
                        method = state.method,
                        headers = headers,
                        body = state.body.takeIf { it.isNotBlank() && state.method.supportsBody },
                        followRedirects = state.followRedirects,
                        approveCrossOriginEntityReplay = { replay ->
                            val approvalId = UUID.randomUUID().toString()
                            val decision = CompletableDeferred<Boolean>()
                            val active = ActiveReplayDecision(runId, approvalId, decision)
                            activeReplayDecision = active
                            _uiState.update { current ->
                                current.copy(
                                    pendingEntityReplayApproval = PendingEntityReplayApproval(
                                        runId = runId,
                                        approvalId = approvalId,
                                        destinationUrl = replay.destinationUrl,
                                        method = replay.method,
                                        statusCode = replay.statusCode
                                    )
                                )
                            }
                            try {
                                decision.await()
                            } finally {
                                if (activeReplayDecision == active) activeReplayDecision = null
                                _uiState.update { current ->
                                    val pending = current.pendingEntityReplayApproval
                                    if (pending?.runId == runId && pending.approvalId == approvalId) {
                                        current.copy(pendingEntityReplayApproval = null)
                                    } else current
                                }
                            }
                        }
                    ),
                    session
                )

                _uiState.update { current ->
                    when (result) {
                        is NetworkResult.Success -> current.copy(
                            isLoading = false,
                            result = result.data,
                            pendingEntityReplayApproval = null,
                            selectedTab = 0
                        )
                        is NetworkResult.Error -> current.copy(
                            isLoading = false,
                            pendingEntityReplayApproval = null,
                            error = result.message
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = e.message?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: e::class.simpleName
                    ?: "Unknown request error"
                _uiState.update { it.copy(isLoading = false, pendingEntityReplayApproval = null, error = "Request failed: $detail") }
            } finally {
                if (operationSession === session) operationSession = null
            }
        }
    }

    private fun cancelRequest(reason: CancellationReason) {
        operationSession?.let { session ->
            operationSession = null
            runCatching { session.cancel(reason) }
        }
    }

    override fun onCleared() {
        cancelRequest(CancellationReason.LIFECYCLE_PAUSE)
    }

    private companion object {
        const val LIFECYCLE_CLOSEABLE_KEY = "http_probe_operation_lifecycle"
        const val EDITED_URL_KEY = "editedHttpUrl"
        const val HANDOFF_CONSUMED_KEY = "httpHandoffConsumed"
        const val HANDOFF_SOURCE_KEY = "httpHandoffSource"
        const val HANDOFF_RECOVERED_KEY = "handoffRecovered"
    }
}

internal fun httpUrlForTarget(target: ToolDestination.HostTarget): String {
    val host = target.host.canonical.let { canonical ->
        val address = canonical.removeSurrounding("[", "]")
        if (':' in address) "[${address.replace("%", "%25")}]" else address
    }
    return "http://$host:${requireNotNull(target.port).value}/"
}
