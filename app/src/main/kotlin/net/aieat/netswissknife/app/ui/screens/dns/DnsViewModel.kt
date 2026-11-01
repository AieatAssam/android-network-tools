package net.aieat.netswissknife.app.ui.screens.dns

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.util.SystemDnsAddressProvider
import net.aieat.netswissknife.app.util.enumFromSavedName
import net.aieat.netswissknife.core.domain.DnsLookupParams
import net.aieat.netswissknife.core.domain.DnsLookupUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.dns.DnsLookupOperation
import net.aieat.netswissknife.core.network.dns.DnsNameValidator
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession

/** All possible states for the DNS lookup UI. */
sealed interface DnsUiState {
    object Idle : DnsUiState
    object Loading : DnsUiState
    object Canceling : DnsUiState
    object Canceled : DnsUiState
    data class Success(val result: DnsResult, val showRaw: Boolean = false) : DnsUiState
    data class Error(
        val message: String,
        val canFallbackToCloudflare: Boolean = false
    ) : DnsUiState
}

/** SavedStateHandle keys for the DNS form; they survive process death and rotation. */
private const val DNS_DOMAIN_KEY = "dnsDomain"
private const val DNS_RECORD_TYPE_KEY = "dnsRecordType"
private const val DNS_SERVER_KEY = "dnsServer"
private const val DNS_CUSTOM_SERVER_KEY = "dnsCustomServer"

/** [ToolSessionMemory] slot holding the last DNS form and terminal result. */
private const val DNS_SESSION_KEY = "dnsSession"

/**
 * The DNS form and its last terminal result, kept in [ToolSessionMemory] when the tool is left.
 * The resolver is kept as its form key, so the System resolver is read from the device again.
 */
internal data class DnsSessionSnapshot(
    val domain: String,
    val recordType: DnsRecordType,
    val serverKey: String,
    val customServerAddress: String,
    val finished: DnsUiState?,
)

/** Stable key for a resolver choice. The System resolver's addresses come from the device, not the form. */
private fun DnsServer.formKey(): String =
    when (this) {
        is DnsServer.System -> "system"
        DnsServer.Google -> "google"
        DnsServer.Cloudflare -> "cloudflare"
        DnsServer.OpenDns -> "opendns"
        DnsServer.Quad9 -> "quad9"
        is DnsServer.Custom -> "custom"
    }

private fun dnsServerFromFormKey(
    key: String?,
    customAddress: String,
): DnsServer? =
    when (key) {
        "system" -> DnsServer.System()
        "google" -> DnsServer.Google
        "cloudflare" -> DnsServer.Cloudflare
        "opendns" -> DnsServer.OpenDns
        "quad9" -> DnsServer.Quad9
        "custom" -> DnsServer.Custom(customAddress)
        else -> null
    }

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val dnsLookupUseCase: DnsLookupUseCase,
    private val systemDnsAddressProvider: SystemDnsAddressProvider,
    private val recentHostsRepository: RecentHostsRepository,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
    // Hilt supplies the owner-backed handle at runtime; this empty default supports direct unit tests.
    @param:SuppressLint("VisibleForTests")
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val sessionMemory: ToolSessionMemory = ToolSessionMemory(),
) : ViewModel() {

    // Restore order: a SavedStateHandle value (rotation) wins over the snapshot kept when the tool
    // was left, which in turn wins over the defaults. A kept result is shown only for the same domain.
    private val restoredSession: DnsSessionSnapshot? = sessionMemory.get<DnsSessionSnapshot>(DNS_SESSION_KEY)
    private val initialDomain: String =
        savedStateHandle.get<String>(DNS_DOMAIN_KEY) ?: restoredSession?.domain.orEmpty()

    private val _uiState =
        MutableStateFlow<DnsUiState>(
            restoredSession?.takeIf { it.domain == initialDomain }?.finished ?: DnsUiState.Idle,
        )
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status
    private var lookupJob: Job? = null
    private var lookupGeneration = 0L
    private var operationSession: OperationSession? = null
    private var cancelRequestGeneration = 0L

    init {
        addCloseable(LIFECYCLE_CLOSEABLE_KEY, AutoCloseable {
            cancelLookup(CancellationReason.LIFECYCLE_PAUSE, terminalState = null)
        })
    }

    // ── Form field state ─────────────────────────────────────────────────────

    private val _domain = MutableStateFlow(initialDomain)
    val domain: StateFlow<String> = _domain.asStateFlow()

    private val _recordType =
        MutableStateFlow(
            enumFromSavedName(
                savedStateHandle.get<String>(DNS_RECORD_TYPE_KEY) ?: restoredSession?.recordType?.name,
                DnsRecordType.entries,
            )
                ?: DnsRecordType.A,
        )
    val recordType: StateFlow<DnsRecordType> = _recordType.asStateFlow()

    // The custom address is declared first: a saved "custom" choice reads it.
    private val _customServerAddress =
        MutableStateFlow(
            savedStateHandle.get<String>(DNS_CUSTOM_SERVER_KEY) ?: restoredSession?.customServerAddress.orEmpty(),
        )
    val customServerAddress: StateFlow<String> = _customServerAddress.asStateFlow()

    private val _selectedServer =
        MutableStateFlow<DnsServer>(
            dnsServerFromFormKey(
                savedStateHandle.get<String>(DNS_SERVER_KEY) ?: restoredSession?.serverKey,
                _customServerAddress.value,
            )
                ?: DnsServer.System(),
        )
    val selectedServer: StateFlow<DnsServer> = _selectedServer.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_DNS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── User actions ─────────────────────────────────────────────────────────

    fun onDomainChange(value: String) {
        _domain.value = value
        savedStateHandle[DNS_DOMAIN_KEY] = value
        clearStaleResult()
    }

    fun onRecordTypeChange(type: DnsRecordType) {
        _recordType.value = type
        savedStateHandle[DNS_RECORD_TYPE_KEY] = type.name
        clearStaleResult()
    }

    fun onServerChange(server: DnsServer) {
        _selectedServer.value = server
        savedStateHandle[DNS_SERVER_KEY] = server.formKey()
        clearStaleResult()
    }

    fun onCustomServerAddressChange(address: String) {
        _customServerAddress.value = address
        _selectedServer.value = DnsServer.Custom(address)
        savedStateHandle[DNS_CUSTOM_SERVER_KEY] = address
        savedStateHandle[DNS_SERVER_KEY] = _selectedServer.value.formKey()
        clearStaleResult()
    }

    /**
     * A finished result or error describes the previous query and server. Once the
     * user edits the query or changes the server, it no longer applies, so it is
     * dropped. Loading and canceling states belong to a running request and are kept.
     */
    private fun clearStaleResult() {
        val current = _uiState.value
        if (current is DnsUiState.Success || current is DnsUiState.Error) {
            _uiState.value = DnsUiState.Idle
        }
    }

    fun onToggleRawView() {
        val current = _uiState.value
        if (current is DnsUiState.Success) {
            _uiState.value = current.copy(showRaw = !current.showRaw)
        }
    }

    fun onClearResults() {
        cancelLookup(CancellationReason.USER_STOP, terminalState = DnsUiState.Idle)
    }

    fun onStopLookup() {
        if (_uiState.value !is DnsUiState.Loading) return
        cancelLookup(CancellationReason.USER_STOP, terminalState = DnsUiState.Canceled)
    }

    fun onRetry() {
        performLookup()
    }

    fun onUseCloudflare() {
        if (lookupJob != null || _uiState.value is DnsUiState.Loading ||
            _uiState.value is DnsUiState.Canceling
        ) return
        _selectedServer.value = DnsServer.Cloudflare
        savedStateHandle[DNS_SERVER_KEY] = DnsServer.Cloudflare.formKey()
        performLookup()
    }

    fun removeRecentHost(host: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_DNS_HOSTS, host)
        }
    }

    fun clearRecentHosts() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_DNS_HOSTS)
        }
    }

    fun performLookup() {
        if (lookupJob != null || _uiState.value is DnsUiState.Loading ||
            _uiState.value is DnsUiState.Canceling
        ) return
        // Invalid names are reported inline by the screen; they never reach the resolver.
        if (DnsNameValidator.problemOf(_domain.value, _recordType.value) != null) return

        val server = when (val s = _selectedServer.value) {
            is DnsServer.Custom -> DnsServer.Custom(_customServerAddress.value)
            is DnsServer.System -> {
                val info = runCatching { systemDnsAddressProvider.getInfo() }
                    .getOrElse {
                        SystemDnsAddressProvider.SystemDnsInfo(
                            addresses = systemDnsAddressProvider.getAddresses(),
                            privateDnsActive = false,
                            privateDnsHost = null
                        )
                    }
                DnsServer.System(info.addresses, info.privateDnsActive, info.privateDnsHost)
            }
            else -> s
        }

        val params = DnsLookupParams(
            domain = _domain.value,
            recordType = _recordType.value,
            server = server
        )

        val requestGeneration = ++lookupGeneration
        _uiState.value = DnsUiState.Loading
        operationSession?.cancel(CancellationReason.USER_STOP)
        val session = DnsLookupOperation.newSession()
        operationSession = session
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = dnsLookupUseCase(params, session)
                if (requestGeneration != lookupGeneration) return@launch

                if (result is NetworkResult.Success) {
                    try {
                        addRecentIfInputWasValid(params)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Recents are best-effort and must not replace the DNS result.
                    }
                    if (requestGeneration != lookupGeneration) return@launch
                }

                val canFallbackToCloudflare =
                    (params.server as? DnsServer.System)?.serverAddresses?.isEmpty() == true
                _uiState.value = when (result) {
                    is NetworkResult.Success -> DnsUiState.Success(result.data)
                    is NetworkResult.Error -> DnsUiState.Error(
                        message = result.message,
                        canFallbackToCloudflare = canFallbackToCloudflare
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == lookupGeneration) {
                    _uiState.value = DnsUiState.Error(e.message ?: "DNS lookup failed")
                }
            } finally {
                if (requestGeneration == lookupGeneration) {
                    lookupJob = null
                    if (operationSession === session) operationSession = null
                }
            }
        }
        lookupJob = job
        job.start()
    }

    private suspend fun addRecentIfInputWasValid(params: DnsLookupParams) {
        val domain = params.domain.trim()
        val customValid = (params.server as? DnsServer.Custom)?.address?.isNotBlank() ?: true
        if (domain.isNotBlank() && domain.length <= 253 && customValid) {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_DNS_HOSTS, domain)
        }
    }

    private fun cancelLookup(reason: CancellationReason, terminalState: DnsUiState?) {
        val job = lookupJob
        if (job == null) {
            if (terminalState != null) _uiState.value = terminalState
            return
        }

        val requestGeneration = ++lookupGeneration
        val cancellationGeneration = ++cancelRequestGeneration
        val session = operationSession
        operationSession = null
        if (terminalState === DnsUiState.Canceled) _uiState.value = DnsUiState.Canceling
        else if (terminalState != null) _uiState.value = terminalState

        runCatching { session?.cancel(reason) }
        job.cancel()

        // Keep lookupJob populated until the canceled operation's finally blocks and resource
        // cleanup have completed. This also prevents a new lookup from racing that cleanup.
        if (terminalState != null) {
            viewModelScope.launch {
                job.join()
                if (lookupJob === job) lookupJob = null
                if (cancellationGeneration == cancelRequestGeneration &&
                    requestGeneration == lookupGeneration
                ) {
                    _uiState.value = terminalState
                }
            }
        }
    }

    override fun onCleared() {
        // A running lookup is cancelled here and is not kept as running; only a finished result is kept.
        cancelLookup(CancellationReason.LIFECYCLE_PAUSE, terminalState = null)
        val current = _uiState.value
        sessionMemory.put(
            DNS_SESSION_KEY,
            DnsSessionSnapshot(
                domain = _domain.value,
                recordType = _recordType.value,
                serverKey = _selectedServer.value.formKey(),
                customServerAddress = _customServerAddress.value,
                finished = current.takeIf { it is DnsUiState.Success || it is DnsUiState.Error },
            ),
        )
    }

    private companion object {
        const val LIFECYCLE_CLOSEABLE_KEY = "dns_operation_lifecycle"
    }
}
