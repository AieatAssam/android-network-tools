package net.aieat.netswissknife.app.ui.screens.wol

import androidx.lifecycle.SavedStateHandle
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
import net.aieat.netswissknife.core.network.wol.WolSendReport
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wol.WakeOnLanOperation
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.toNetworkErrorKind
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolMacAddress
import net.aieat.netswissknife.app.ui.navigation.ToolSource
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val routeMac = savedStateHandle.get<String>("mac")?.let(ToolMacAddress::parse)
    private val rawIntentArgument = savedStateHandle.get<String>("intent")
    private val typedIntent = rawIntentArgument?.let(ToolIntentCodec::decode)
    private val handoffMac = (typedIntent?.destination as? ToolDestination.WakeOnLan)
        ?.mac
        ?.takeIf { routeMac != null && it == routeMac && typedIntent.source == ToolSource.LAN }
    private val handoffConsumed = savedStateHandle.get<Boolean>(HANDOFF_CONSUMED_KEY) == true
    private val _hasInvalidHandoff = MutableStateFlow(
        (rawIntentArgument != null || savedStateHandle.get<String>("mac") != null) &&
            handoffMac == null && savedStateHandle.get<Boolean>(HANDOFF_RECOVERED_KEY) != true,
    )
    val hasInvalidHandoff: StateFlow<Boolean> = _hasInvalidHandoff.asStateFlow()
    private val _sourceContext = MutableStateFlow(
        if (handoffConsumed) {
            savedStateHandle.get<String>(HANDOFF_SOURCE_KEY)?.let { wireName ->
                ToolSource.entries.singleOrNull { it.wireName == wireName }
            }?.takeIf { handoffMac != null && typedIntent?.source == it }
        } else {
            handoffMac?.let { typedIntent?.source }
        },
    )
    val sourceContext: ToolSource? get() = _sourceContext.value
    val sourceContextState: StateFlow<ToolSource?> = _sourceContext.asStateFlow()

    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    private val _uiState = MutableStateFlow<WolUiState>(WolUiState.Idle)
    val uiState: StateFlow<WolUiState> = _uiState.asStateFlow()

    private val _macAddress = MutableStateFlow(
        if (handoffConsumed) {
            savedStateHandle.get<String>(EDITED_MAC_KEY).orEmpty()
        } else {
            savedStateHandle.get<String>(EDITED_MAC_KEY)
                ?: if (_hasInvalidHandoff.value) "" else handoffMac?.value.orEmpty()
        },
    )
    val macAddress: StateFlow<String> = _macAddress.asStateFlow()

    private val _broadcastAddress = MutableStateFlow(DEFAULT_BROADCAST)
    val broadcastAddress: StateFlow<String> = _broadcastAddress.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT.toString())
    val port: StateFlow<String> = _port.asStateFlow()

    private var operationSession: OperationSession? = null
    private var sendGeneration = 0L

    init {
        if (!handoffConsumed) {
            // Snapshot the validated initial prefill once; route arguments remain available
            // after recreation and must not restore a MAC the user has cleared.
            savedStateHandle[EDITED_MAC_KEY] = _macAddress.value
            _sourceContext.value?.let { source ->
                savedStateHandle[HANDOFF_SOURCE_KEY] = source.wireName
            } ?: savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
            savedStateHandle[HANDOFF_CONSUMED_KEY] = true
        }
    }

    /** True when the user has typed something that is not a valid MAC yet. */
    val isMacInvalid: Boolean
        get() = _macAddress.value.isNotBlank() && ToolMacAddress.parse(_macAddress.value) == null

    val canSend: Boolean
        get() = ToolMacAddress.parse(_macAddress.value) != null &&
            _broadcastAddress.value.isNotBlank() &&
            _port.value.toIntOrNull() in WakeOnLanParams.MIN_PORT..WakeOnLanParams.MAX_PORT &&
            _uiState.value !is WolUiState.Sending

    fun onMacAddressChange(value: String) {
        _macAddress.value = value
        savedStateHandle[EDITED_MAC_KEY] = value
        if (_hasInvalidHandoff.value && ToolMacAddress.parse(value) != null) {
            savedStateHandle[HANDOFF_RECOVERED_KEY] = true
            _hasInvalidHandoff.value = false
        }
    }

    /** Clears only the incoming MAC prefill; broadcast and port choices remain untouched. */
    fun clearPrefill() {
        if (_uiState.value is WolUiState.Sending) return
        _macAddress.value = ""
        savedStateHandle[EDITED_MAC_KEY] = ""
        _sourceContext.value = null
        savedStateHandle.remove<String>(HANDOFF_SOURCE_KEY)
        savedStateHandle[HANDOFF_CONSUMED_KEY] = true
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
        private const val EDITED_MAC_KEY = "editedWolMac"
        private const val HANDOFF_CONSUMED_KEY = "wolHandoffConsumed"
        private const val HANDOFF_SOURCE_KEY = "wolHandoffSource"
        private const val HANDOFF_RECOVERED_KEY = "wolHandoffRecovered"
    }
}
