package net.aieat.netswissknife.app.ui.screens.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.util.SystemDnsAddressProvider
import net.aieat.netswissknife.core.domain.DnsLookupParams
import net.aieat.netswissknife.core.domain.DnsLookupUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import dagger.hilt.android.lifecycle.HiltViewModel
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
    data class Error(val message: String) : DnsUiState
}

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val dnsLookupUseCase: DnsLookupUseCase,
    private val systemDnsAddressProvider: SystemDnsAddressProvider,
    private val recentHostsRepository: RecentHostsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DnsUiState>(DnsUiState.Idle)
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()

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
        _uiState.value = DnsUiState.Idle
    }

    fun onRetry() {
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
        viewModelScope.launch {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_DNS_HOSTS, _domain.value)
        }
        val server = when (val s = _selectedServer.value) {
            is DnsServer.Custom -> DnsServer.Custom(_customServerAddress.value)
            is DnsServer.System -> DnsServer.System(systemDnsAddressProvider.getAddresses())
            else -> s
        }

        val params = DnsLookupParams(
            domain = _domain.value,
            recordType = _recordType.value,
            server = server
        )

        viewModelScope.launch {
            _uiState.value = DnsUiState.Loading
            _uiState.value = when (val result = dnsLookupUseCase(params)) {
                is NetworkResult.Success -> DnsUiState.Success(result.data)
                is NetworkResult.Error   -> DnsUiState.Error(result.message)
            }
        }
    }
}
