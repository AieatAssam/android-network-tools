package net.aieat.netswissknife.app.ui.screens.oui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.OuiLookupUseCase
import net.aieat.netswissknife.core.network.oui.MacInputParser
import net.aieat.netswissknife.core.network.oui.MacLookupEntry
import net.aieat.netswissknife.core.network.oui.MacParseResult
import javax.inject.Inject

/** Why a lookup produced no result to show. The screen maps each to a string. */
enum class OuiLookupFailure {
    /** Every pasted entry was malformed. */
    NO_VALID_ENTRY,

    /** The clipboard held no text. */
    CLIPBOARD_EMPTY,
}

sealed interface OuiLookupPhase {
    data object Idle : OuiLookupPhase

    data object Loading : OuiLookupPhase

    data class Results(
        val entries: List<MacLookupEntry>,
        val truncated: Boolean,
    ) : OuiLookupPhase

    data class Failed(
        val reason: OuiLookupFailure,
    ) : OuiLookupPhase
}

data class OuiLookupUiState(
    val input: String = "",
    val phase: OuiLookupPhase = OuiLookupPhase.Idle,
    /** Pasted entries that are not a MAC address or OUI prefix, shown as inline validation. */
    val invalidEntryCount: Int = 0,
)

@HiltViewModel
class OuiLookupViewModel
    @Inject
    constructor(
        private val lookup: OuiLookupUseCase,
        private val recents: RecentHostsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OuiLookupUiState())
        val uiState: StateFlow<OuiLookupUiState> = _uiState.asStateFlow()

        val recentLookups: StateFlow<List<String>> =
            recents
                .getRecents(AppPreferenceKeys.RECENT_OUI_LOOKUPS)
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private var lookupJob: Job? = null

        fun onInputChange(value: String) {
            _uiState.update { it.copy(input = value, invalidEntryCount = countInvalid(value)) }
        }

        /** Looks up whatever is in the field. A blank field does nothing. */
        fun onLookup() {
            val text = _uiState.value.input
            if (text.isBlank()) return
            runLookup(text)
        }

        /** The paste-from-clipboard action: fills the field with the clipboard text and looks it up. */
        fun onClipboardText(text: String?) {
            if (text.isNullOrBlank()) {
                _uiState.update { it.copy(phase = OuiLookupPhase.Failed(OuiLookupFailure.CLIPBOARD_EMPTY)) }
                return
            }
            onInputChange(text)
            runLookup(text)
        }

        /** Choosing a recent fills the field and looks it up straight away. */
        fun onRecentSelected(entry: String) {
            onInputChange(entry)
            runLookup(entry)
        }

        fun onRecentRemoved(entry: String) {
            viewModelScope.launch { recents.removeRecent(AppPreferenceKeys.RECENT_OUI_LOOKUPS, entry) }
        }

        fun onRecentsCleared() {
            viewModelScope.launch { recents.clearAll(AppPreferenceKeys.RECENT_OUI_LOOKUPS) }
        }

        fun onClear() {
            lookupJob?.cancel()
            _uiState.value = OuiLookupUiState()
        }

        private fun runLookup(text: String) {
            lookupJob?.cancel()
            _uiState.update { it.copy(phase = OuiLookupPhase.Loading) }
            lookupJob =
                viewModelScope.launch {
                    val report = lookup(text)
                    val valid = report.entries.filterIsInstance<MacLookupEntry.Address>()
                    if (valid.isEmpty()) {
                        _uiState.update { it.copy(phase = OuiLookupPhase.Failed(OuiLookupFailure.NO_VALID_ENTRY)) }
                        return@launch
                    }
                    valid.forEach { recents.addRecent(AppPreferenceKeys.RECENT_OUI_LOOKUPS, it.input) }
                    _uiState.update {
                        it.copy(phase = OuiLookupPhase.Results(report.entries, report.truncated))
                    }
                }
        }

        private fun countInvalid(text: String): Int {
            val pieces = MacInputParser.splitEntries(text)
            return pieces.count { MacInputParser.parse(it) is MacParseResult.Invalid }
        }
    }
