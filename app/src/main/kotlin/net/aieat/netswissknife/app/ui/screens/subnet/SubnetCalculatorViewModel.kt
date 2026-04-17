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
import javax.inject.Inject

data class SubnetCalculatorUiState(
    val input: String = "",
    val result: SubnetInfo? = null,
    val error: String? = null,
    val isRangeMode: Boolean = false,
    val minIpInput: String = "",
    val maxIpInput: String = "",
)

@HiltViewModel
class SubnetCalculatorViewModel @Inject constructor(
    private val useCase: SubnetCalculatorUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubnetCalculatorUiState())
    val uiState: StateFlow<SubnetCalculatorUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(input = value, error = null)
    }

    fun calculate() {
        val input = _uiState.value.input.trim()
        if (input.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(result = null, error = null)
            when (val res = useCase(input)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(result = res.data)
                is NetworkResult.Error   -> _uiState.value = _uiState.value.copy(error = res.message)
            }
        }
    }

    fun setExample(example: String) {
        _uiState.value = SubnetCalculatorUiState(input = example)
        calculate()
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRangeMode = !_uiState.value.isRangeMode,
            result = null,
            error = null
        )
    }

    fun onMinIpChange(value: String) {
        _uiState.value = _uiState.value.copy(minIpInput = value, error = null)
    }

    fun onMaxIpChange(value: String) {
        _uiState.value = _uiState.value.copy(maxIpInput = value, error = null)
    }

    fun calculateRange() {
        val min = _uiState.value.minIpInput.trim()
        val max = _uiState.value.maxIpInput.trim()
        if (min.isBlank() || max.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(result = null, error = null)
            when (val res = useCase.invokeRange(min, max)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(result = res.data)
                is NetworkResult.Error   -> _uiState.value = _uiState.value.copy(error = res.message)
            }
        }
    }
}
