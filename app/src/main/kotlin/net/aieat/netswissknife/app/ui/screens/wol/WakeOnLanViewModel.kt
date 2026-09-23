package net.aieat.netswissknife.app.ui.screens.wol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.WakeOnLanParams
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wol.WakeOnLanOperation
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.toNetworkErrorKind
import javax.inject.Inject

sealed interface WolUiState {
    data object Idle : WolUiState
    data object Sending : WolUiState
    data class Success(val report: WolSendReport) : WolUiState
    data class Error(
        val message: String,
        val networkErrorKind: NetworkErrorKind = NetworkErrorKind.GENERAL,
    ) : WolUiState
}

@HiltViewModel
class WakeOnLanViewModel @Inject constructor(
    private val wakeOnLan: WakeOnLanUseCase,
    networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    private val _uiState = MutableStateFlow<WolUiState>(WolUiState.Idle)
    val uiState: StateFlow<WolUiState> = _uiState.asStateFlow()

    private val _macAddress = MutableStateFlow("")
    val macAddress: StateFlow<String> = _macAddress.asStateFlow()

    private val _broadcastAddress = MutableStateFlow(DEFAULT_BROADCAST)
    val broadcastAddress: StateFlow<String> = _broadcastAddress.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT.toString())
    val port: StateFlow<String> = _port.asStateFlow()

    private var operationSession: OperationSession? = null
    private var sendGeneration = 0L

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
        operationSession?.cancel(CancellationReason.USER_STOP)
        val session = WakeOnLanOperation.newSession()
        val generation = ++sendGeneration
        operationSession = session
        _uiState.value = WolUiState.Sending
        viewModelScope.launch {
            try {
                val result = wakeOnLan(params, session)
                if (sendGeneration != generation || operationSession !== session) return@launch
                _uiState.value = when (result) {
                    is NetworkResult.Success -> WolUiState.Success(result.data)
                    is NetworkResult.Error -> WolUiState.Error(result.message, result.cause.toNetworkErrorKind())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (operationSession === session) operationSession = null
            }
        }
    }

    fun stopSending() {
        sendGeneration++
        operationSession?.cancel(CancellationReason.USER_STOP)
        operationSession = null
        _uiState.value = WolUiState.Idle
    }

    fun reset() {
        sendGeneration++
        operationSession?.cancel(CancellationReason.USER_STOP)
        operationSession = null
        _uiState.value = WolUiState.Idle
    }

    override fun onCleared() {
        sendGeneration++
        operationSession?.cancel(CancellationReason.LIFECYCLE_PAUSE)
        operationSession = null
    }

    companion object {
        const val DEFAULT_BROADCAST = "255.255.255.255"
        const val DEFAULT_PORT = 9
    }
}
