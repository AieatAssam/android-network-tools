package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.platform.NetInfoLocalReader
import net.aieat.netswissknife.core.domain.PublicIpLookupUseCase
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.PublicIpReport
import javax.inject.Inject

/** Public IP is either not yet requested, in flight, or the report from the last explicit check. */
sealed interface PublicIpUiState {
    data object Idle : PublicIpUiState

    data object Loading : PublicIpUiState

    data class Loaded(
        val report: PublicIpReport,
    ) : PublicIpUiState
}

data class NetInfoUiState(
    /** Null only before the first read. */
    val local: NetInfoSnapshot? = null,
    val publicIp: PublicIpUiState = PublicIpUiState.Idle,
)

private const val CHANGE_DEBOUNCE_MILLIS = 300L

@HiltViewModel
class NetInfoViewModel
    @Inject
    constructor(
        private val localReader: NetInfoLocalReader,
        private val publicIpLookup: PublicIpLookupUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NetInfoUiState())
        val uiState: StateFlow<NetInfoUiState> = _uiState.asStateFlow()

        init {
            refresh()
            viewModelScope.launch {
                localReader.changes.debounce(CHANGE_DEBOUNCE_MILLIS).collect { refresh() }
            }
        }

        /** Re-reads the local network. Never contacts the internet. */
        fun refresh() {
            val snapshot = localReader.read()
            _uiState.update { state ->
                val movedNetwork = state.local?.let { it.networkIdentity() != snapshot.networkIdentity() } ?: false
                state.copy(
                    local = snapshot,
                    publicIp = if (movedNetwork) PublicIpUiState.Idle else state.publicIp,
                )
            }
        }

        /** Explicit user action only. A second tap while a lookup runs is ignored. */
        fun checkPublicIp() {
            val started = _uiState.value
            if (started.publicIp == PublicIpUiState.Loading) return
            val identity = started.local?.networkIdentity()
            _uiState.update { it.copy(publicIp = PublicIpUiState.Loading) }
            viewModelScope.launch {
                val report = publicIpLookup(Unit)
                _uiState.update { state ->
                    // A report is only valid for the network it was measured on.
                    if (state.local?.networkIdentity() == identity) {
                        state.copy(publicIp = PublicIpUiState.Loaded(report))
                    } else {
                        state
                    }
                }
            }
        }
    }
