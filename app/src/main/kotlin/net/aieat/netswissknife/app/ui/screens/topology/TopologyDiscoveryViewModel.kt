package net.aieat.netswissknife.app.ui.screens.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.toNetworkErrorKind
import net.aieat.netswissknife.core.domain.TopologyDiscoveryUseCase
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.topology.*
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import javax.inject.Inject

sealed class TopologyUiState {
    object Idle : TopologyUiState()
    data class Discovering(
        val nodes: List<TopologyNode>,
        val links: List<TopologyLink>,
        val progressMessage: String,
        val nodesDone: Int,
        val selectedNodeIp: String? = null
    ) : TopologyUiState()
    data class Done(
        val graph: TopologyGraph,
        val selectedNodeIp: String?
    ) : TopologyUiState()
    data class Failure(
        val message: String,
        val networkErrorKind: NetworkErrorKind = NetworkErrorKind.GENERAL,
    ) : TopologyUiState()
}

@HiltViewModel
class TopologyDiscoveryViewModel @Inject constructor(
    private val useCase: TopologyDiscoveryUseCase,
    private val recentHostsRepository: RecentHostsRepository,
    networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
) : ViewModel() {

    val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

    private val _uiState = MutableStateFlow<TopologyUiState>(TopologyUiState.Idle)
    val uiState: StateFlow<TopologyUiState> = _uiState.asStateFlow()

    val recentSeeds: StateFlow<List<String>> = recentHostsRepository
        .getRecents(AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var discoveryJob: Job? = null
    private var operationSession: OperationSession? = null

    init {
        addCloseable(LIFECYCLE_CLOSEABLE_KEY, AutoCloseable {
            cancelDiscovery(CancellationReason.LIFECYCLE_PAUSE)
        })
    }

    fun startDiscovery(params: TopologyParams) {
        // Cancel any scan already in flight — without this, calling startDiscovery
        // twice (double-tap, or a fresh scan started before the prior one finished)
        // runs two collectors against the same _uiState concurrently, and the older
        // job's own locally-accumulated node/link lists can overwrite the newer
        // job's progress whenever it wakes up.
        cancelDiscovery(CancellationReason.USER_STOP)
        val session = OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                timeoutMillis = DEFAULT_OPERATION_TIMEOUT_MILLIS,
            )
        )
        operationSession = session
        // Normalize valid input at the ViewModel boundary as well as in the form.
        // Keep invalid raw input so the domain use case can report its usual error.
        val normalizedTargetIp = HostValidator.normalize(params.targetIp)
        val normalizedParams = params.copy(targetIp = normalizedTargetIp ?: params.targetIp)
        discoveryJob = viewModelScope.launch {
            val nodes = mutableListOf<TopologyNode>()
            val links = mutableListOf<TopologyLink>()
            var savedSeed = false
            _uiState.value = TopologyUiState.Discovering(emptyList(), emptyList(), "Starting...", 0)

            try {
                useCase.invoke(normalizedParams, session).collect { event ->
                    if (operationSession !== session || session.cancellationReason != null) return@collect
                    when (event) {
                        is TopologyDiscoveryEvent.NodeDiscovered -> {
                            if (!savedSeed) {
                                savedSeed = true
                                recentHostsRepository.addRecent(
                                    AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS,
                                    normalizedParams.targetIp
                                )
                            }
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
                            if (operationSession === session && session.cancellationReason == null) {
                                _uiState.value = TopologyUiState.Done(graph = event.graph, selectedNodeIp = null)
                            }
                        }
                        is TopologyDiscoveryEvent.Error -> {
                            _uiState.value = TopologyUiState.Failure(
                                event.message,
                                event.cause.toNetworkErrorKind(),
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationSession === session && session.cancellationReason == null) {
                    _uiState.value = TopologyUiState.Failure(
                        e.message ?: "Topology discovery failed",
                        e.toNetworkErrorKind(),
                    )
                }
            } finally {
                if (operationSession === session) operationSession = null
            }
        }
    }

    fun removeRecentSeed(seed: String) {
        viewModelScope.launch {
            recentHostsRepository.removeRecent(AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS, seed)
        }
    }

    fun clearRecentSeeds() {
        viewModelScope.launch {
            recentHostsRepository.clearAll(AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS)
        }
    }

    /** Selection works both mid-scan and once discovery finishes, so users can inspect
     *  nodes as they stream in instead of waiting for [TopologyUiState.Done]. */
    fun selectNode(ip: String) {
        when (val current = _uiState.value) {
            is TopologyUiState.Discovering -> _uiState.value = current.copy(selectedNodeIp = ip)
            is TopologyUiState.Done -> _uiState.value = current.copy(selectedNodeIp = ip)
            else -> Unit
        }
    }

    fun deselectNode() {
        when (val current = _uiState.value) {
            is TopologyUiState.Discovering -> _uiState.value = current.copy(selectedNodeIp = null)
            is TopologyUiState.Done -> _uiState.value = current.copy(selectedNodeIp = null)
            else -> Unit
        }
    }

    fun reset() {
        cancelDiscovery(CancellationReason.USER_STOP)
        _uiState.value = TopologyUiState.Idle
    }

    private fun cancelDiscovery(reason: CancellationReason) {
        val session = operationSession
        operationSession = null
        if (session != null) {
            // OperationSession cancellation closes registered transports synchronously. Keep
            // that potentially blocking work away from UI and ViewModel lifecycle callers.
            TopologyOperationCancellationScope.cancel(session, reason, discoveryJob)
        } else {
            discoveryJob?.cancel()
        }
        discoveryJob = null
    }

    /** Runs the current form parameters only after an explicit retry action. */
    fun retryDiscovery(params: TopologyParams) {
        startDiscovery(params)
    }

    private companion object {
        const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 120_000L
        const val LIFECYCLE_CLOSEABLE_KEY = "topology-discovery-operation"
    }
}

/** Process-lifetime cancellation dispatcher so ViewModel teardown cannot cancel this work. */
private object TopologyOperationCancellationScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cancel(session: OperationSession, reason: CancellationReason, discoveryJob: Job?) {
        scope.launch {
            session.cancel(reason)
            // A validation or mocked flow may not attach the session to OperationRunner, so
            // it cannot cancel its collector through the session. Cancel any remainder here.
            discoveryJob?.cancel()
        }
    }
}
