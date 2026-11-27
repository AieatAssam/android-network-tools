package net.aieat.netswissknife.app.ui.screens.subnet

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.util.enumFromSavedName
import net.aieat.netswissknife.core.domain.SubnetCalculatorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import net.aieat.netswissknife.core.network.subnet.SubnetMembership
import net.aieat.netswissknife.core.network.subnet.SubnetSplit
import javax.inject.Inject

/** How the split card interprets its single input: a new prefix length or a number of subnets. */
enum class SubnetSplitMode { BY_PREFIX, BY_COUNT }

data class SubnetCalculatorUiState(
    val input: String = "",
    val result: SubnetInfo? = null,
    /** Typed failure for the last calculation; rendered through ErrorTextMapper. */
    val error: NetworkResult.Error? = null,
    /** Inline problem with the CIDR entry, checked as the user types; Calculate stays disabled while set. */
    val inputError: NetworkResult.Error? = null,
    val isRangeMode: Boolean = false,
    val minIpInput: String = "",
    val maxIpInput: String = "",
    /** Inline problem with the range bounds (minimum above maximum); Find subnet stays disabled while set. */
    val rangeError: NetworkResult.Error? = null,
    val checkIpInput: String = "",
    val membership: SubnetMembership? = null,
    val membershipError: NetworkResult.Error? = null,
    val splitMode: SubnetSplitMode = SubnetSplitMode.BY_PREFIX,
    val splitInput: String = "",
    val split: SubnetSplit? = null,
    val splitError: NetworkResult.Error? = null,
)

/** SavedStateHandle keys for the calculator inputs; the derived result is recalculated, not saved. */
private const val KEY_INPUT = "subnetInput"
private const val KEY_RANGE_MODE = "subnetRangeMode"
private const val KEY_MIN_IP = "subnetMinIp"
private const val KEY_MAX_IP = "subnetMaxIp"
private const val KEY_CHECK_IP = "subnetCheckIp"
private const val KEY_SPLIT_MODE = "subnetSplitMode"
private const val KEY_SPLIT_INPUT = "subnetSplitInput"

/** Keeps the form fields (never the derived result) across process death and rotation. */
private fun SavedStateHandle.writeInputs(state: SubnetCalculatorUiState) {
    this[KEY_INPUT] = state.input
    this[KEY_RANGE_MODE] = state.isRangeMode
    this[KEY_MIN_IP] = state.minIpInput
    this[KEY_MAX_IP] = state.maxIpInput
    this[KEY_CHECK_IP] = state.checkIpInput
    this[KEY_SPLIT_MODE] = state.splitMode.name
    this[KEY_SPLIT_INPUT] = state.splitInput
}

/** [ToolSessionMemory] slot holding the last calculator state (inputs and derived results) when left. */
private const val KEY_SESSION = "subnetSession"
private const val SESSION_CLOSEABLE_KEY = "subnet_session_memory"

@HiltViewModel
class SubnetCalculatorViewModel
    @Inject
    constructor(
        private val useCase: SubnetCalculatorUseCase,
        // Hilt supplies the owner-backed handle at runtime; this empty default supports direct unit tests.
        @param:SuppressLint("VisibleForTests")
        private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
        private val sessionMemory: ToolSessionMemory = ToolSessionMemory(),
    ) : ViewModel() {
        // The calculator has no running state: its whole state (inputs and the results they produced) is
        // plain data, so the state kept when the tool was left is restored as it was and the handle is reseeded.
        private val _uiState =
            MutableStateFlow(
                sessionMemory.get<SubnetCalculatorUiState>(KEY_SESSION)?.also { savedStateHandle.writeInputs(it) }
                    ?: SubnetCalculatorUiState(
                        input = savedStateHandle.get<String>(KEY_INPUT).orEmpty(),
                        inputError = useCase.cidrProblem(savedStateHandle.get<String>(KEY_INPUT).orEmpty()),
                        isRangeMode = savedStateHandle.get<Boolean>(KEY_RANGE_MODE) ?: false,
                        minIpInput = savedStateHandle.get<String>(KEY_MIN_IP).orEmpty(),
                        maxIpInput = savedStateHandle.get<String>(KEY_MAX_IP).orEmpty(),
                        rangeError =
                            useCase.rangeProblem(
                                savedStateHandle.get<String>(KEY_MIN_IP).orEmpty(),
                                savedStateHandle.get<String>(KEY_MAX_IP).orEmpty(),
                            ),
                        checkIpInput = savedStateHandle.get<String>(KEY_CHECK_IP).orEmpty(),
                        splitMode =
                            enumFromSavedName(savedStateHandle.get<String>(KEY_SPLIT_MODE), SubnetSplitMode.entries)
                                ?: SubnetSplitMode.BY_PREFIX,
                        splitInput = savedStateHandle.get<String>(KEY_SPLIT_INPUT).orEmpty(),
                    ),
            )
        val uiState: StateFlow<SubnetCalculatorUiState> = _uiState.asStateFlow()

        init {
            // Runs when the ViewModel is cleared (Back), so the state is kept for the app session.
            addCloseable(SESSION_CLOSEABLE_KEY, AutoCloseable { sessionMemory.put(KEY_SESSION, _uiState.value) })
        }

        fun onInputChange(value: String) {
            _uiState.value = _uiState.value.withoutResult().copy(input = value, inputError = useCase.cidrProblem(value))
            savedStateHandle.writeInputs(_uiState.value)
        }

        fun calculate() {
            val input = _uiState.value.input.trim()
            if (input.isBlank() || _uiState.value.inputError != null) return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(result = null, error = null)
                when (val res = useCase(input)) {
                    is NetworkResult.Success -> _uiState.value = _uiState.value.copy(result = res.data)
                    is NetworkResult.Error -> _uiState.value = _uiState.value.copy(error = res)
                }
            }
        }

        fun setExample(example: String) {
            _uiState.value = SubnetCalculatorUiState(input = example, inputError = useCase.cidrProblem(example))
            savedStateHandle.writeInputs(_uiState.value)
            calculate()
        }

        fun toggleMode() {
            val state = _uiState.value
            _uiState.value =
                if (!state.isRangeMode) {
                    // CIDR → Range: pre-fill min/max from result or input
                    // An invalid CIDR entry is not carried over, so no unchecked value appears in the range fields.
                    val typedAddress =
                        state.input
                            .substringBefore('/')
                            .trim()
                            .takeIf { state.inputError == null }
                    val minIp = state.result?.networkAddress ?: typedAddress.orEmpty()
                    val maxIp = state.result?.broadcastAddress ?: ""
                    state.withoutResult().copy(
                        isRangeMode = true,
                        minIpInput = minIp,
                        maxIpInput = maxIp,
                        rangeError = useCase.rangeProblem(minIp, maxIp),
                    )
                } else {
                    // Range → CIDR: copy minIp into CIDR input field and check it in the new mode
                    val cidrInput = state.minIpInput.takeIf { it.isNotBlank() } ?: state.input
                    state.withoutResult().copy(
                        isRangeMode = false,
                        input = cidrInput,
                        inputError = useCase.cidrProblem(cidrInput),
                    )
                }
            savedStateHandle.writeInputs(_uiState.value)
        }

        fun onMinIpChange(value: String) {
            val state = _uiState.value
            _uiState.value =
                state.withoutResult().copy(
                    minIpInput = value,
                    rangeError = useCase.rangeProblem(value, state.maxIpInput),
                )
            savedStateHandle.writeInputs(_uiState.value)
        }

        fun onMaxIpChange(value: String) {
            val state = _uiState.value
            _uiState.value =
                state.withoutResult().copy(
                    maxIpInput = value,
                    rangeError = useCase.rangeProblem(state.minIpInput, value),
                )
            savedStateHandle.writeInputs(_uiState.value)
        }

        fun calculateRange() {
            val min = _uiState.value.minIpInput.trim()
            val max = _uiState.value.maxIpInput.trim()
            if (min.isBlank() || max.isBlank() || _uiState.value.rangeError != null) return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(result = null, error = null)
                when (val res = useCase.invokeRange(min, max)) {
                    is NetworkResult.Success -> _uiState.value = _uiState.value.copy(result = res.data)
                    is NetworkResult.Error -> _uiState.value = _uiState.value.copy(error = res)
                }
            }
        }

        // ── Is this address in the subnet? ─────────────────────────────────────────

        /** Editing the address re-checks it against the current result; it never clears the result. */
        fun onCheckIpChange(value: String) {
            _uiState.value = _uiState.value.copy(checkIpInput = value, membership = null, membershipError = null)
            savedStateHandle.writeInputs(_uiState.value)
            val cidr = _uiState.value.result?.cidrNotation ?: return
            val ip = value.trim()
            if (ip.isBlank()) return
            viewModelScope.launch {
                when (val res = useCase.checkMembership(ip, cidr)) {
                    is NetworkResult.Success -> _uiState.value = _uiState.value.copy(membership = res.data)
                    is NetworkResult.Error -> _uiState.value = _uiState.value.copy(membershipError = res)
                }
            }
        }

        // ── Split into equal subnets ───────────────────────────────────────────────

        fun onSplitModeChange(mode: SubnetSplitMode) {
            _uiState.value = _uiState.value.copy(splitMode = mode, splitInput = "", split = null, splitError = null)
            savedStateHandle.writeInputs(_uiState.value)
        }

        fun onSplitInputChange(value: String) {
            _uiState.value = _uiState.value.copy(splitInput = value, split = null, splitError = null)
            savedStateHandle.writeInputs(_uiState.value)
        }

        fun splitNetwork() {
            val state = _uiState.value
            val cidr = state.result?.cidrNotation ?: return
            val text = state.splitInput.trim()
            if (text.isBlank()) return
            viewModelScope.launch {
                // Unparseable text becomes an out-of-range value so the repository produces the typed error.
                val result =
                    if (state.splitMode == SubnetSplitMode.BY_PREFIX) {
                        useCase.splitIntoPrefix(cidr, text.toIntOrNull() ?: -1)
                    } else {
                        useCase.splitIntoCount(cidr, text.toLongOrNull() ?: 0L)
                    }
                when (result) {
                    is NetworkResult.Success -> {
                        _uiState.value = _uiState.value.copy(split = result.data, splitError = null)
                    }

                    is NetworkResult.Error -> {
                        _uiState.value = _uiState.value.copy(split = null, splitError = result)
                    }
                }
            }
        }
    }

/** Clears the result and everything derived from it, so nothing stale stays on screen. */
private fun SubnetCalculatorUiState.withoutResult(): SubnetCalculatorUiState =
    copy(
        result = null,
        error = null,
        membership = null,
        membershipError = null,
        split = null,
        splitError = null,
    )

/** Blank bounds are not an error yet; inverted bounds are reported next to the fields. */
private fun SubnetCalculatorUseCase.rangeProblem(
    min: String,
    max: String,
): NetworkResult.Error? {
    val trimmedMin = min.trim()
    val trimmedMax = max.trim()
    return if (trimmedMin.isBlank() || trimmedMax.isBlank()) null else rangeOrderError(trimmedMin, trimmedMax)
}

/** A blank entry is not an error yet; anything else is checked with the same rules as Calculate. */
private fun SubnetCalculatorUseCase.cidrProblem(value: String): NetworkResult.Error? {
    val trimmed = value.trim()
    return if (trimmed.isBlank()) null else cidrInputError(trimmed)
}
