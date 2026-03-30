package net.aieat.netswissknife.app.ui.screens.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.domain.TopologyDiscoveryUseCase
import net.aieat.netswissknife.core.network.topology.*
import javax.inject.Inject

sealed class TopologyUiState {
    object Idle : TopologyUiState()
    data class Discovering(
        val nodes: List<TopologyNode>,
        val links: List<TopologyLink>,
        val progressMessage: String,
        val nodesDone: Int
    ) : TopologyUiState()
    data class Done(
        val graph: TopologyGraph,
        val selectedNodeIp: String?
    ) : TopologyUiState()
    data class Failure(val message: String) : TopologyUiState()
}

@HiltViewModel
class TopologyDiscoveryViewModel @Inject constructor(
    private val useCase: TopologyDiscoveryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TopologyUiState>(TopologyUiState.Idle)
    val uiState: StateFlow<TopologyUiState> = _uiState.asStateFlow()

    fun startDiscovery(params: TopologyParams) {
        viewModelScope.launch {
            val nodes = mutableListOf<TopologyNode>()
            val links = mutableListOf<TopologyLink>()
            _uiState.value = TopologyUiState.Discovering(emptyList(), emptyList(), "Starting...", 0)

            useCase.invoke(params).collect { event ->
                when (event) {
                    is TopologyDiscoveryEvent.NodeDiscovered -> {
                        nodes.add(event.node)
                        val current = _uiState.value
                        if (current is TopologyUiState.Discovering) {
                            _uiState.value = current.copy(
                                nodes = nodes.toList(),
                                nodesDone = nodes.size
                            )
                        }
                    }
                    is TopologyDiscoveryEvent.LinkDiscovered -> {
                        links.add(event.link)
                        val current = _uiState.value
                        if (current is TopologyUiState.Discovering) {
                            _uiState.value = current.copy(links = links.toList())
                        }
                    }
                    is TopologyDiscoveryEvent.Progress -> {
                        val current = _uiState.value
                        if (current is TopologyUiState.Discovering) {
                            _uiState.value = current.copy(
                                progressMessage = event.message,
                                nodesDone = event.nodesDone
                            )
                        }
                    }
                    is TopologyDiscoveryEvent.Complete -> {
                        _uiState.value = TopologyUiState.Done(graph = event.graph, selectedNodeIp = null)
                    }
                    is TopologyDiscoveryEvent.Error -> {
                        _uiState.value = TopologyUiState.Failure(event.message)
                    }
                }
            }
        }
    }

    fun selectNode(ip: String) {
        val current = _uiState.value
        if (current is TopologyUiState.Done) {
            _uiState.value = current.copy(selectedNodeIp = ip)
        }
    }

    fun deselectNode() {
        val current = _uiState.value
        if (current is TopologyUiState.Done) {
            _uiState.value = current.copy(selectedNodeIp = null)
        }
    }

    fun reset() {
        _uiState.value = TopologyUiState.Idle
    }
}
