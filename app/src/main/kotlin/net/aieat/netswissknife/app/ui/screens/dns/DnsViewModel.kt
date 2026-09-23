package net.aieat.netswissknife.app.ui.screens.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.util.SystemDnsAddressProvider
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.core.domain.DnsLookupParams
import net.aieat.netswissknife.core.domain.DnsLookupUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All possible states for the DNS lookup UI. */
sealed interface DnsUiState {
    object Idle : DnsUiState
    object Loading : DnsUiState
    data class Success(val result: DnsResult, val showRaw: Boolean = false) : DnsUiState
    data class Error(
        val message: String,
        val canFallbackToCloudflare: Boolean = false
    ) : DnsUiState
}

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val dnsLookupUseCase: DnsLookupUseCase,
    private val systemDnsAddressProvider: SystemDnsAddressProvider,
    private val recentHostsRepository: RecentHostsRepository,
    private val networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DnsUiState>(DnsUiState.Idle)
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()
    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status
    private var lookupJob: Job? = null
    private var lookupGeneration = 0L

    // ── Form field state ─────────────────────────────────────────────────────

    private val _domain = MutableStateFlow("")
    val domain: StateFlow<String> = _domain.asStateFlow()

    private val _recordType = MutableStateFlow(DnsRecordType.A)
    val recordType: StateFlow<DnsRecordType> = _recordType.asStateFlow()

    private val _selectedServer = MutableStateFlow<DnsServer>(DnsServer.System())
    val selectedServer: StateFlow<DnsServer> = _selectedServer.asStateFlow()

    private val _customServerAddress = MutableStateFlow("")
    val customServerAddress: StateFlow<String> = _customServerAddress.asStateFlow()

    val recentHosts: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_DNS_HOSTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── User actions ─────────────────────────────────────────────────────────

    fun onDomainChange(value: String) {
        _domain.value = value
    }

    fun onRecordTypeChange(type: DnsRecordType) {
        _recordType.value = type
    }

    fun onServerChange(server: DnsServer) {
        _selectedServer.value = server
    }

    fun onCustomServerAddressChange(address: String) {
        _customServerAddress.value = address
        _selectedServer.value = DnsServer.Custom(address)
    }

    fun onToggleRawView() {
        val current = _uiState.value
        if (current is DnsUiState.Success) {
            _uiState.value = current.copy(showRaw = !current.showRaw)
        }
    }

    fun onClearResults() {
        lookupGeneration++
        lookupJob?.cancel()
        lookupJob = null
        _uiState.value = DnsUiState.Idle
    }

    fun onRetry() {
        performLookup()
    }

    fun onUseCloudflare() {
        if (_uiState.value is DnsUiState.Loading) return
        _selectedServer.value = DnsServer.Cloudflare
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
        if (_uiState.value is DnsUiState.Loading) return

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
        lookupJob?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = dnsLookupUseCase(params)
                if (requestGeneration != lookupGeneration) return@launch

                try {
                    addRecentIfInputWasValid(params)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Recents are best-effort and must not replace the DNS result.
                }
                if (requestGeneration != lookupGeneration) return@launch

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
                if (requestGeneration == lookupGeneration) lookupJob = null
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
}
