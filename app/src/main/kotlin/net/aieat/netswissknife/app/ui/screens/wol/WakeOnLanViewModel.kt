package net.aieat.netswissknife.app.ui.screens.wol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.WakeOnLanParams
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport
import javax.inject.Inject

sealed interface WolUiState {
    data object Idle : WolUiState
    data object Sending : WolUiState
    data class Success(val report: WolSendReport) : WolUiState
    data class Error(val message: String) : WolUiState
}

@HiltViewModel
class WakeOnLanViewModel @Inject constructor(
    private val wakeOnLan: WakeOnLanUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WolUiState>(WolUiState.Idle)
    val uiState: StateFlow<WolUiState> = _uiState.asStateFlow()

    private val _macAddress = MutableStateFlow("")
    val macAddress: StateFlow<String> = _macAddress.asStateFlow()

    private val _broadcastAddress = MutableStateFlow(DEFAULT_BROADCAST)
    val broadcastAddress: StateFlow<String> = _broadcastAddress.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT.toString())
    val port: StateFlow<String> = _port.asStateFlow()

    /** True when the user has typed something that is not a valid MAC yet. */
    val isMacInvalid: Boolean
        get() = _macAddress.value.isNotBlank() && !WolMagicPacket.isValidMac(_macAddress.value)

    val canSend: Boolean
        get() = WolMagicPacket.isValidMac(_macAddress.value) &&
            _broadcastAddress.value.isNotBlank() &&
            _port.value.toIntOrNull() in 0..65_535 &&
            _uiState.value !is WolUiState.Sending

    fun onMacAddressChange(value: String) {
        _macAddress.value = value
    }

    fun onBroadcastAddressChange(value: String) {
        _broadcastAddress.value = value
    }

    fun onPortChange(value: String) {
        if (value.isEmpty() || (value.length <= 5 && value.all(Char::isDigit))) {
            _port.value = value
        }
    }

    fun send() {
        if (!canSend) return
        val params = WakeOnLanParams(
            macAddress = _macAddress.value,
            broadcastAddress = _broadcastAddress.value,
            port = _port.value.toIntOrNull() ?: DEFAULT_PORT,
        )
        _uiState.value = WolUiState.Sending
        viewModelScope.launch {
            _uiState.value = when (val result = wakeOnLan(params)) {
                is NetworkResult.Success -> WolUiState.Success(result.data)
                is NetworkResult.Error -> WolUiState.Error(result.message)
            }
        }
    }

    fun reset() {
        _uiState.value = WolUiState.Idle
    }

    companion object {
        const val DEFAULT_BROADCAST = "255.255.255.255"
        const val DEFAULT_PORT = 9
    }
}
