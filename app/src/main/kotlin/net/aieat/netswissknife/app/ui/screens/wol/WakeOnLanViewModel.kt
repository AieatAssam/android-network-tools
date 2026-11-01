package net.aieat.netswissknife.app.ui.screens.wol

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.toNetworkErrorKind
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolMacAddress
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.WakeOnLanParams
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.ErrorInfo
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wol.WakeOnLanOperation
import net.aieat.netswissknife.core.network.wol.WolBroadcastProblem
import net.aieat.netswissknife.core.network.wol.WolMacProblem
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport
import javax.inject.Inject

sealed interface WolUiState {
    data object Idle : WolUiState
    data object Sending : WolUiState
    data class Success(val report: WolSendReport) : WolUiState
    data class Error(
        val message: String,
        /** Typed failure, rendered through ErrorTextMapper so the text is localized. */
        val info: ErrorInfo? = null,
        val networkErrorKind: NetworkErrorKind = NetworkErrorKind.GENERAL,
    ) : WolUiState
}

/**
 * The Wake-on-LAN form, the saved-device name being typed, and the last finished send (Success or
 * Error), kept in [ToolSessionMemory] when the tool is left. No secret is involved: the MAC, broadcast
 * address, port and device name are the only fields kept.
 */
internal data class WolSessionSnapshot(
    val mac: String,
    val broadcastAddress: String,
    val port: String,
    val deviceName: String,
    val finished: WolUiState?,
)

@HiltViewModel
class WakeOnLanViewModel @Inject constructor(
    private val wakeOnLan: WakeOnLanUseCase,
    networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
    private val savedDeviceStore: WolSavedDeviceStore = InMemoryWolSavedDeviceStore(),
    // Hilt supplies the owner-backed handle at runtime; this empty default supports direct unit tests.
    @param:SuppressLint("VisibleForTests")
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val sessionMemory: ToolSessionMemory = ToolSessionMemory(),
) : ViewModel() {

    // A route or intent handoff wins over the snapshot kept when the tool was left.
    private val restoredSession: WolSessionSnapshot? = sessionMemory.get<WolSessionSnapshot>(WOL_SESSION_KEY)

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

    private val _macAddress = MutableStateFlow(
        if (handoffConsumed) {
            savedStateHandle.get<String>(EDITED_MAC_KEY).orEmpty()
        } else {
            savedStateHandle.get<String>(EDITED_MAC_KEY)
                ?: if (_hasInvalidHandoff.value) "" else handoffMac?.value ?: restoredSession?.mac.orEmpty()
        },
    )
    val macAddress: StateFlow<String> = _macAddress.asStateFlow()

    private val _broadcastAddress =
        MutableStateFlow(
            savedStateHandle.get<String>(BROADCAST_KEY) ?: restoredSession?.broadcastAddress ?: DEFAULT_BROADCAST,
        )
    val broadcastAddress: StateFlow<String> = _broadcastAddress.asStateFlow()

    private val _port =
        MutableStateFlow(
            savedStateHandle.get<String>(PORT_KEY) ?: restoredSession?.port ?: DEFAULT_PORT.toString(),
        )
    val port: StateFlow<String> = _port.asStateFlow()

    // Declared after the MAC: a finished send is shown again only for the MAC it was sent to.
    private val _uiState =
        MutableStateFlow<WolUiState>(
            restoredSession?.takeIf { it.mac == _macAddress.value }?.finished ?: WolUiState.Idle,
        )
    val uiState: StateFlow<WolUiState> = _uiState.asStateFlow()

    /** Inline problem for the MAC field; BLANK means "nothing entered yet" and is not an error to show. */
    val macProblem: StateFlow<WolMacProblem?> =
        _macAddress
            .map { WolMagicPacket.macProblem(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WolMagicPacket.macProblem(_macAddress.value))

    val broadcastProblem: StateFlow<WolBroadcastProblem?> =
        _broadcastAddress
            .map { WolMagicPacket.broadcastProblem(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WolMagicPacket.broadcastProblem(_broadcastAddress.value))

    val portInvalid: StateFlow<Boolean> =
        _port
            .map { isPortOutOfRange(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, isPortOutOfRange(_port.value))

    // ── Saved devices ────────────────────────────────────────────────────────
    // The list and its add, edit, use and delete actions live in WolSavedDeviceEditor. The screen
    // calls those actions through savedDeviceEditor; the flows are re-exposed here for collection.

    val savedDeviceEditor: WolSavedDeviceEditor =
        WolSavedDeviceEditor(
            store = savedDeviceStore,
            scope = viewModelScope,
            currentMac = { _macAddress.value },
            fillMac = { mac -> onMacAddressChange(mac) },
            initialDeviceName = restoredSession?.deviceName.orEmpty(),
        )
    val savedDevices: StateFlow<List<WolSavedDevice>> = savedDeviceEditor.savedDevices
    val deviceName: StateFlow<String> = savedDeviceEditor.deviceName
    val editingMac: StateFlow<String?> = savedDeviceEditor.editingMac
    val saveConflict: StateFlow<Boolean> = savedDeviceEditor.saveConflict
    val saveNotice: StateFlow<WolSaveNotice?> = savedDeviceEditor.saveNotice

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
        get() = macProblem.value.let { it != null && it != WolMacProblem.BLANK }

    val canSend: Boolean
        get() =
            macProblem.value == null &&
                broadcastProblem.value == null &&
                !portInvalid.value &&
                _uiState.value !is WolUiState.Sending

    fun onMacAddressChange(value: String) {
        _macAddress.value = value
        savedStateHandle[EDITED_MAC_KEY] = value
        savedDeviceEditor.clearSaveConflict()
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
        savedStateHandle[BROADCAST_KEY] = value
    }

    fun onPortChange(value: String) {
        if (value.isEmpty() || (value.length <= 5 && value.all(Char::isDigit))) {
            _port.value = value
            savedStateHandle[PORT_KEY] = value
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
                    is NetworkResult.Error -> errorState(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (operationSession === session) operationSession = null
            }
        }
    }

    private fun errorState(result: NetworkResult.Error): WolUiState.Error =
        WolUiState.Error(
            message = result.message,
            info = result.info,
            networkErrorKind = result.cause.toNetworkErrorKind(),
        )

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
        // A send still in progress is not kept as running; only a finished Success or Error is.
        val state = _uiState.value
        sessionMemory.put(
            WOL_SESSION_KEY,
            WolSessionSnapshot(
                mac = _macAddress.value,
                broadcastAddress = _broadcastAddress.value,
                port = _port.value,
                deviceName = savedDeviceEditor.deviceName.value,
                finished = state.takeIf { it is WolUiState.Success || it is WolUiState.Error },
            ),
        )
    }

    companion object {
        const val DEFAULT_BROADCAST = "255.255.255.255"
        const val DEFAULT_PORT = 9

        private val PORT_RANGE = WakeOnLanParams.MIN_PORT..WakeOnLanParams.MAX_PORT

        private fun isPortOutOfRange(value: String): Boolean = value.toIntOrNull() !in PORT_RANGE

        private const val EDITED_MAC_KEY = "editedWolMac"
        private const val HANDOFF_CONSUMED_KEY = "wolHandoffConsumed"
        private const val HANDOFF_SOURCE_KEY = "wolHandoffSource"
        private const val HANDOFF_RECOVERED_KEY = "wolHandoffRecovered"

        // Advanced-options values; they survive rotation and process death, not leaving the tool.
        private const val BROADCAST_KEY = "wolBroadcastAddress"
        private const val PORT_KEY = "wolPort"

        /** [ToolSessionMemory] slot for the form, device name and last finished send when the tool is left. */
        private const val WOL_SESSION_KEY = "wolSession"
    }
}
