package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val isRangeMode: Boolean = false,
    val minIpInput: String = "",
    val maxIpInput: String = "",
    val checkIpInput: String = "",
    val membership: SubnetMembership? = null,
    val membershipError: NetworkResult.Error? = null,
    val splitMode: SubnetSplitMode = SubnetSplitMode.BY_PREFIX,
    val splitInput: String = "",
    val split: SubnetSplit? = null,
    val splitError: NetworkResult.Error? = null,
)

@HiltViewModel
class SubnetCalculatorViewModel @Inject constructor(
    private val useCase: SubnetCalculatorUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubnetCalculatorUiState())
    val uiState: StateFlow<SubnetCalculatorUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.withoutResult().copy(input = value)
    }

    fun calculate() {
        val input = _uiState.value.input.trim()
        if (input.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(result = null, error = null)
            when (val res = useCase(input)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(result = res.data)
                is NetworkResult.Error -> _uiState.value = _uiState.value.copy(error = res)
            }
        }
    }

    fun setExample(example: String) {
        _uiState.value = SubnetCalculatorUiState(input = example)
        calculate()
    }

    fun toggleMode() {
        val state = _uiState.value
        _uiState.value =
            if (!state.isRangeMode) {
                // CIDR → Range: pre-fill min/max from result or input
                val minIp = state.result?.networkAddress
                    ?: state.input.substringBefore('/').trim().takeIf { it.isNotBlank() }
                    ?: ""
                val maxIp = state.result?.broadcastAddress ?: ""
                state.withoutResult().copy(isRangeMode = true, minIpInput = minIp, maxIpInput = maxIp)
            } else {
                // Range → CIDR: copy minIp into CIDR input field
                val cidrInput = state.minIpInput.takeIf { it.isNotBlank() } ?: state.input
                state.withoutResult().copy(isRangeMode = false, input = cidrInput)
            }
    }

    fun onMinIpChange(value: String) {
        _uiState.value = _uiState.value.withoutResult().copy(minIpInput = value)
    }

    fun onMaxIpChange(value: String) {
        _uiState.value = _uiState.value.withoutResult().copy(maxIpInput = value)
    }

    fun calculateRange() {
        val min = _uiState.value.minIpInput.trim()
        val max = _uiState.value.maxIpInput.trim()
        if (min.isBlank() || max.isBlank()) return
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
    }

    fun onSplitInputChange(value: String) {
        _uiState.value = _uiState.value.copy(splitInput = value, split = null, splitError = null)
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
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(split = result.data, splitError = null)
                is NetworkResult.Error -> _uiState.value = _uiState.value.copy(split = null, splitError = result)
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
