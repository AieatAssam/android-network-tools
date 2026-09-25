package net.aieat.netswissknife.app.ui.screens.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.TlsInspectorParams
import net.aieat.netswissknife.core.domain.TlsInspectorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import net.aieat.netswissknife.core.network.tls.TlsInspectorOperation
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import javax.inject.Inject

data class TlsInspectorUiState(
    val host: String = "",
    val port: String = "443",
    val probeProtocols: Boolean = false,
    val expectedPinSha256: String = "",
    val isLoading: Boolean = false,
    val isCanceling: Boolean = false,
    val isCanceled: Boolean = false,
    val result: TlsInspectorResult? = null,
    val error: String? = null,
    val errorDescriptionKey: String? = null,
)

@HiltViewModel
class TlsInspectorViewModel @Inject constructor(
    private val useCase: TlsInspectorUseCase,
    private val recentHostsRepository: RecentHostsRepository,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val routeHost = savedStateHandle.get<String>("host")
    private val routePort = savedStateHandle.get<String>("port")?.toIntOrNull()?.let(ToolPort::parse)
    private val rawIntentArgument = savedStateHandle.get<String>("intent")
    private val typedIntent = rawIntentArgument?.let(ToolIntentCodec::decode)
    private val handoffTarget = (typedIntent?.destination as? ToolDestination.HostTarget)
        ?.takeIf { target ->
            target.tool == HostTool.TLS && target.port != null && routeHost != null && routePort != null &&
                ToolHost.parse(routeHost)?.canonical == target.host.canonical && routePort == target.port
        }
    private val handoffConsumed = savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true
    private val _hasInvalidHandoff = MutableStateFlow(
        (rawIntentArgument != null || routeHost != null || savedStateHandle.get<String>("port") != null) &&
            handoffTarget == null && savedStateHandle.get<Boolean>(HANDOFF_RECOVERED_KEY) != true,
    )
    val hasInvalidHandoff: StateFlow<Boolean> = _hasInvalidHandoff.asStateFlow()
    private val _sourceContext = MutableStateFlow(
        if (handoffConsumed) {
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
        TlsInspectorUiState(
            host = if (handoffConsumed) {
                savedStateHandle.get<String>(EDITED_HOST_KEY).orEmpty()
            } else {
                savedStateHandle.get<String>(EDITED_HOST_KEY)
                    ?: if (_hasInvalidHandoff.value) "" else handoffTarget?.host?.value.orEmpty()
            },
            port = if (handoffConsumed) {
                savedStateHandle.get<String>(EDITED_PORT_KEY) ?: "443"
            } else {
                savedStateHandle.get<String>(EDITED_PORT_KEY)
                    ?: if (_hasInvalidHandoff.value) "443" else handoffTarget?.port?.value?.toString() ?: "443"
            },
            probeProtocols = savedStateHandle[PROBE_PROTOCOLS_KEY] ?: false,
            expectedPinSha256 = savedStateHandle[EXPECTED_PIN_KEY] ?: "",
        ),
    )
    val uiState: StateFlow<TlsInspectorUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    private var operationSession: OperationSession? = null

    init {
        if (!handoffConsumed) {
            // Route arguments remain present after recreation. Persist the validated initial
            // form snapshot (including intentional empty values) before marking it consumed.
            savedStateHandle[EDITED_HOST_KEY] = _uiState.value.host
            savedStateHandle[EDITED_PORT_KEY] = _uiState.value.port
            _sourceContext.value?.let { source ->
                savedStateHandle[HANDOFF_SOURCE_KEY] = source.wireName
            } ?: savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
            savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        }
        addCloseable(LIFECYCLE_CLOSEABLE_KEY, AutoCloseable {
            cancelInspection(CancellationReason.LIFECYCLE_PAUSE)
        })
    }

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_TLS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onHostChange(value: String) {
        if (_uiState.value.isLoading) return
        savedStateHandle[EDITED_HOST_KEY] = value
        _uiState.value = _uiState.value.copy(host = value, error = null, errorDescriptionKey = null, result = null)
        recoverInvalidHandoffIfReady()
    }

    fun onPortChange(value: String) {
        if (_uiState.value.isLoading) return
        savedStateHandle[EDITED_PORT_KEY] = value
        _uiState.value = _uiState.value.copy(port = value, error = null, errorDescriptionKey = null, result = null)
        recoverInvalidHandoffIfReady()
    }

    fun onProbeProtocolsChange(enabled: Boolean) {
        if (_uiState.value.isLoading) return
        savedStateHandle[PROBE_PROTOCOLS_KEY] = enabled
        _uiState.value = _uiState.value.copy(
            probeProtocols = enabled,
            error = null,
            errorDescriptionKey = null,
            result = null,
        )
    }

    fun onExpectedPinChange(value: String) {
        if (_uiState.value.isLoading) return
        savedStateHandle[EXPECTED_PIN_KEY] = value
        _uiState.value = _uiState.value.copy(
            expectedPinSha256 = value,
            error = null,
            errorDescriptionKey = null,
            result = null,
        )
    }

    /** Clears the incoming TLS handoff as one host/port edit that survives recreation. */
    fun clearPrefill() {
        if (_uiState.value.isLoading) return
        _sourceContext.value = null
        savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
        savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        savedStateHandle[EDITED_HOST_KEY] = ""
        savedStateHandle[EDITED_PORT_KEY] = DEFAULT_PORT
        _uiState.value = _uiState.value.copy(
            host = "",
            port = DEFAULT_PORT,
            isLoading = false,
            result = null,
            error = null,
            errorDescriptionKey = null,
        )
    }

    private fun recoverInvalidHandoffIfReady() {
        val state = _uiState.value
        if (_hasInvalidHandoff.value && HostValidator.normalize(state.host) != null &&
            ToolPort.parse(state.port.toIntOrNull() ?: 0) != null
        ) {
            savedStateHandle[HANDOFF_RECOVERED_KEY] = true
            _hasInvalidHandoff.value = false
        }
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
        if (state.isLoading) return
        val normalizedHost = HostValidator.normalize(state.host)
        if (normalizedHost == null) {
            _uiState.value = state.copy(
                isLoading = false,
                result = null,
                error = if (state.host.isBlank()) "Host must not be blank" else "Invalid hostname or IP address",
                errorDescriptionKey = null,
            )
            return
        }
        val port = state.port.toIntOrNull()?.takeIf { it in 1..65_535 }
        if (port == null) {
            _uiState.value = state.copy(
                isLoading = false,
                result = null,
                error = "Port must be a number from 1 to 65535",
                errorDescriptionKey = null,
            )
            return
        }
        _uiState.value = state.copy(
            host = normalizedHost,
            isLoading = true,
            isCanceling = false,
            isCanceled = false,
            error = null,
            errorDescriptionKey = null,
            result = null,
        )
        val session = TlsInspectorOperation.newSession(INSPECTION_TIMEOUT_MS)
        operationSession = session
        viewModelScope.launch {
            try {
                val params = TlsInspectorParams(
                    host      = normalizedHost,
                    port      = port,
                    timeoutMs = INSPECTION_TIMEOUT_MS,
                    probeProtocols = state.probeProtocols,
                    expectedPinSha256 = state.expectedPinSha256.trim().ifEmpty { null },
                )
                when (val res = useCase(params, session)) {
                    is NetworkResult.Success -> {
                        saveRecentHostAfterSuccess(normalizedHost)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isCanceling = false,
                            isCanceled = false,
                            result    = res.data,
                            error     = null,
                            errorDescriptionKey = null,
                        )
                    }
                    is NetworkResult.Error   -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCanceling = false,
                        isCanceled = false,
                        result    = null,
                        error     = res.message,
                        errorDescriptionKey = res.descriptionKey,
                    )
                }
            } catch (e: CancellationException) {
                when (session.cancellationReason) {
                    CancellationReason.USER_STOP -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCanceling = false,
                        isCanceled = true,
                        result = null,
                    )
                    CancellationReason.LIFECYCLE_PAUSE -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCanceling = false,
                        isCanceled = false,
                    )
                    else -> Unit
                }
                throw e
            } catch (e: Exception) {
                val detail = e.message?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: e::class.simpleName
                    ?: "Unknown TLS inspection error"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isCanceling = false,
                    isCanceled = false,
                    result = null,
                    error = "TLS inspection failed: $detail",
                    errorDescriptionKey = null,
                )
            } finally {
                if (operationSession === session) operationSession = null
            }
        }
    }

    fun stopInspection() {
        if (!_uiState.value.isLoading || _uiState.value.isCanceling || operationSession == null) return
        _uiState.value = _uiState.value.copy(isCanceling = true)
        cancelInspection(CancellationReason.USER_STOP)
    }

    private fun cancelInspection(reason: CancellationReason) {
        operationSession?.let { session ->
            operationSession = null
            runCatching { session.cancel(reason) }
        }
    }

    private suspend fun saveRecentHostAfterSuccess(host: String) {
        try {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, host)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Recents are best-effort and must not replace the inspection result.
        }
    }

    override fun onCleared() {
        cancelInspection(CancellationReason.LIFECYCLE_PAUSE)
    }

    private companion object {
        const val INSPECTION_TIMEOUT_MS = 10_000
        const val LIFECYCLE_CLOSEABLE_KEY = "tls_operation_lifecycle"
        const val EDITED_HOST_KEY = "editedTlsHost"
        const val EDITED_PORT_KEY = "editedTlsPort"
        const val PROBE_PROTOCOLS_KEY = "tlsProbeProtocols"
        const val EXPECTED_PIN_KEY = "tlsExpectedPinSha256"
        const val HANDOFF_CONSUMED_KEY = "tlsHandoffConsumed"
        const val HANDOFF_SOURCE_KEY = "tlsHandoffSource"
        const val HANDOFF_RECOVERED_KEY = "tlsHandoffRecovered"
        const val DEFAULT_PORT = "443"
    }
}
