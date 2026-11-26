package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.mdns.MdnsNoLocalNetworkException
import net.aieat.netswissknife.app.mdns.MdnsPacketIoException
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.NoOpNetworkStatusProvider
import net.aieat.netswissknife.app.platform.toNetworkErrorKind
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsOperation
import net.aieat.netswissknife.core.network.mdns.MdnsTruncationReason
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.net.LocalNetworkBindingUnavailableException
import net.aieat.netswissknife.core.network.net.containsLocalNetworkPermissionDenied
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.SocketException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Why a scan failed; the screen maps each reason to plain-language text instead of raw socket errors. */
enum class MdnsScanFailure {
    GENERAL,
    PERMISSION_DENIED,
    NO_LOCAL_NETWORK,
    NETWORK_FAILED,
}

internal fun Throwable.toMdnsScanFailure(): MdnsScanFailure =
    when {
        containsLocalNetworkPermissionDenied() -> MdnsScanFailure.PERMISSION_DENIED

        this is MdnsNoLocalNetworkException -> MdnsScanFailure.NO_LOCAL_NETWORK

        this is MdnsPacketIoException ||
            this is LocalNetworkBindingUnavailableException ||
            this is SocketException -> MdnsScanFailure.NETWORK_FAILED

        else -> MdnsScanFailure.GENERAL
    }

data class MdnsDiscoveryUiState(
    val isScanning: Boolean = false,
    val isCanceling: Boolean = false,
    val scanCanceled: Boolean = false,
    val services: List<DiscoveredService> = emptyList(),
    val servicesByType: Map<String, List<DiscoveredService>> = emptyMap(),
    /** Technical detail kept for diagnostics; the screen shows [errorReason] text, never this string. */
    val error: String? = null,
    val errorReason: MdnsScanFailure? = null,
    val elapsedMs: Long = 0,
    val totalFound: Int = 0,
    val truncationReasons: Set<MdnsTruncationReason> = emptySet(),
    val scanComplete: Boolean = false,
    val networkErrorKind: NetworkErrorKind = NetworkErrorKind.GENERAL,
    /** The network changed after services were found; those services are kept and a note is shown. */
    val networkInterrupted: Boolean = false,
    /** Services listed before the network changed were cleared; the screen says so until the next scan or Clear. */
    val resultsClearedForNetworkChange: Boolean = false,
    /** The search text; kept with the results when the tool is left and reopened. */
    val searchQuery: String = "",
    /** Whether the list groups services by device; kept with the results when the tool is left. */
    val groupByDevice: Boolean = false,
)

/** [ToolSessionMemory] slot holding the listed services, the view choices and the network they were found on. */
private const val MDNS_SESSION_KEY = "mdnsSession"

/**
 * What the screen shows when the tool is left and opened again. The scan flags and any error are
 * not kept: a scan that was running when the tool was left has ended.
 */
internal data class MdnsSessionSnapshot(
    val services: List<DiscoveredService>,
    val totalFound: Int,
    val scanComplete: Boolean,
    val truncationReasons: Set<MdnsTruncationReason>,
    val searchQuery: String,
    val groupByDevice: Boolean,
    val networkId: String?,
)

internal fun MdnsDiscoveryUiState.toSessionSnapshot(networkId: String?): MdnsSessionSnapshot =
    MdnsSessionSnapshot(
        services = services,
        totalFound = totalFound,
        scanComplete = scanComplete,
        truncationReasons = truncationReasons,
        searchQuery = searchQuery,
        groupByDevice = groupByDevice,
        networkId = networkId,
    )

/**
 * Rebuilds the listed services when the tool is opened again. Services found on another network are
 * never shown: they are cleared with the same note as a live network change.
 */
internal fun MdnsSessionSnapshot.restoreFor(currentNetworkId: String?): MdnsDiscoveryUiState {
    val restored =
        MdnsDiscoveryUiState(
            services = services,
            servicesByType = services.groupBy { it.serviceType }.toSortedMap(),
            totalFound = totalFound,
            scanComplete = scanComplete,
            truncationReasons = truncationReasons,
            searchQuery = searchQuery,
            groupByDevice = groupByDevice,
        )
    return if (networkId == currentNetworkId) restored else restored.afterNetworkChange()
}

/**
 * Services accumulate across scans until Clear, so a rescan starts from what is already listed.
 * Everything else describes one scan and is reset here: completion, cancellation, network
 * interruption and errors. Truncation notes are kept as a union because they also describe
 * services that are still listed.
 */
internal fun MdnsDiscoveryUiState.startingScan(): MdnsDiscoveryUiState =
    MdnsDiscoveryUiState(
        isScanning = true,
        services = services,
        servicesByType = servicesByType,
        truncationReasons = truncationReasons,
        searchQuery = searchQuery,
        groupByDevice = groupByDevice,
    )

/**
 * A change of network drops every listed service, because they were found on the old network.
 * The new state is built from scratch, so a running scan's state is dropped with them.
 */
internal fun MdnsDiscoveryUiState.afterNetworkChange(): MdnsDiscoveryUiState =
    MdnsDiscoveryUiState(
        resultsClearedForNetworkChange = services.isNotEmpty(),
        searchQuery = searchQuery,
        groupByDevice = groupByDevice,
    )

@HiltViewModel
class MdnsDiscoveryViewModel
    @Inject
    constructor(
        private val useCase: MdnsDiscoveryUseCase,
        networkStatusProvider: NetworkStatusProvider = NoOpNetworkStatusProvider,
        private val sessionMemory: ToolSessionMemory = ToolSessionMemory(),
    ) : ViewModel() {
        companion object {
            const val DEFAULT_SCAN_DURATION_MS = 8_000L
            const val ELAPSED_TICK_MS = 1_000L
            private const val LIFECYCLE_CLOSEABLE_KEY = "mdns_operation_lifecycle"
        }

        val networkStatus: StateFlow<NetworkStatus> = networkStatusProvider.status

        private val _uiState = MutableStateFlow(MdnsDiscoveryUiState())
        val uiState: StateFlow<MdnsDiscoveryUiState> = _uiState

        private var scanJob: Job? = null
        private var timerJob: Job? = null
        private var operationSession: OperationSession? = null
        private var scanGeneration = 0L
        private var activeScanGeneration: Long? = null
        private var timerGeneration: Long? = null

        /** The network the listed services were found on; a change of network clears them. */
        private var resultsNetworkId: String? = networkStatus.value.networkId

        init {
            sessionMemory.get<MdnsSessionSnapshot>(MDNS_SESSION_KEY)?.let { snapshot ->
                _uiState.value = snapshot.restoreFor(networkStatus.value.networkId)
            }
            // ViewModel closes registered resources before cancelling viewModelScope. Record the
            // lifecycle reason at that boundary so the operation owner sees it before parent cancel.
            addCloseable(
                LIFECYCLE_CLOSEABLE_KEY,
                AutoCloseable {
                    cancelScan(CancellationReason.LIFECYCLE_PAUSE)
                    stopTimer()
                },
            )
            // Persist after the lifecycle closeable is registered, so the pause reason is recorded first.
            viewModelScope.launch {
                _uiState
                    .map { it.toSessionSnapshot(resultsNetworkId) }
                    .distinctUntilChanged()
                    .collect { sessionMemory.put(MDNS_SESSION_KEY, it) }
            }
            // The first value is the network already in use when the screen opens, so it is not a change.
            viewModelScope.launch {
                networkStatus
                    .map { it.networkId }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { onNetworkChanged() }
            }
        }

        fun startScan(timeoutMs: Long = DEFAULT_SCAN_DURATION_MS) {
            if (_uiState.value.isScanning) return

            val generation = ++scanGeneration
            activeScanGeneration = generation
            val scanWindowMs = MdnsOperation.clampScanDuration(timeoutMs)
            val session = MdnsOperation.newSession(timeoutMs = scanWindowMs)
            operationSession = session

            _uiState.update { previous -> previous.startingScan() }

            val startTime = SystemMonotonicClock.nowNanos()

            timerGeneration = generation
            timerJob =
                viewModelScope.launch {
                    while (true) {
                        // The screen shows whole seconds, so a 1 s tick is enough and avoids
                        // recomposing 10 times a second.
                        delay(ELAPSED_TICK_MS)
                        val elapsedMs = ((SystemMonotonicClock.nowNanos() - startTime).coerceAtLeast(0L) / 1_000_000L)
                        if (activeScanGeneration == generation) {
                            _uiState.update { state ->
                                if (activeScanGeneration == generation && state.isScanning) state.copy(elapsedMs = elapsedMs) else state
                            }
                        } else {
                            break
                        }
                    }
                }

            scanJob =
                viewModelScope.launch {
                    val runningJob = coroutineContext.job
                    try {
                        useCase(scanWindowMs, session).collect { update ->
                            when (update) {
                                is MdnsUpdate.ServiceFound -> {
                                    _uiState.update { state ->
                                        if (activeScanGeneration != generation || state.isCanceling) return@update state
                                        val existing = state.services.indexOfFirst { it.instanceName == update.service.instanceName }
                                        val updated =
                                            if (existing >= 0) {
                                                state.services.toMutableList().also { it[existing] = update.service }
                                            } else {
                                                state.services + update.service
                                            }
                                        val byType = updated.groupBy { it.serviceType }.toSortedMap()
                                        state.copy(services = updated, servicesByType = byType)
                                    }
                                }

                                is MdnsUpdate.DiscoveryComplete -> {
                                    _uiState.update { state ->
                                        if (activeScanGeneration != generation || state.isCanceling) {
                                            state
                                        } else {
                                            state.copy(
                                                scanComplete = true,
                                                totalFound = update.totalFound,
                                                truncationReasons = state.truncationReasons + update.truncationReasons,
                                                networkInterrupted = update.networkInterrupted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { state ->
                            if (activeScanGeneration != generation || state.isCanceling) {
                                state
                            } else {
                                state.copy(
                                    error = e.message ?: "Discovery failed",
                                    errorReason = e.toMdnsScanFailure(),
                                    networkErrorKind = e.toNetworkErrorKind(),
                                )
                            }
                        }
                    } finally {
                        if (operationSession === session) operationSession = null
                        if (scanJob === runningJob) scanJob = null
                        if (activeScanGeneration == generation) {
                            _uiState.update { state ->
                                if (activeScanGeneration != generation) {
                                    state
                                } else {
                                    state.copy(
                                        isScanning = false,
                                        isCanceling = false,
                                        scanCanceled = state.scanCanceled || state.isCanceling,
                                    )
                                }
                            }
                            activeScanGeneration = null
                            stopTimer(generation)
                        }
                    }
                }
        }

        fun stopScan() {
            val generation = activeScanGeneration ?: return
            if (!_uiState.value.isScanning || _uiState.value.isCanceling) return
            _uiState.update { state ->
                if (activeScanGeneration == generation && state.isScanning) state.copy(isCanceling = true) else state
            }
            cancelScan(CancellationReason.USER_STOP)
            stopTimer(generation)
        }

        private fun cancelScan(reason: CancellationReason) {
            operationSession?.let { session ->
                operationSession = null
                runCatching { session.cancel(reason) }
            }
            scanJob?.cancel(OperationCancellationException(reason))
            scanJob = null
        }

        fun onSearchChange(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
        }

        fun onGroupByDeviceChange(enabled: Boolean) {
            _uiState.update { it.copy(groupByDevice = enabled) }
        }

        /** Stops a scan that was running on the old network and clears everything it listed. */
        private fun onNetworkChanged() {
            resultsNetworkId = networkStatus.value.networkId
            activeScanGeneration = null
            cancelScan(CancellationReason.NETWORK_LOST)
            stopTimer()
            _uiState.update { previous -> previous.afterNetworkChange() }
        }

        /** Clears a failed scan's message only; the services found so far stay listed. */
        fun dismissError() {
            _uiState.update { state ->
                state.copy(error = null, errorReason = null, networkErrorKind = NetworkErrorKind.GENERAL)
            }
        }

        fun reset() {
            scanGeneration++
            activeScanGeneration = null
            cancelScan(CancellationReason.USER_STOP)
            stopTimer()
            _uiState.update { previous ->
                MdnsDiscoveryUiState(searchQuery = previous.searchQuery, groupByDevice = previous.groupByDevice)
            }
        }

        private fun stopTimer(generation: Long? = null) {
            if (generation != null && timerGeneration != generation) return
            timerJob?.cancel()
            timerJob = null
            timerGeneration = null
        }

        override fun onCleared() {
            cancelScan(CancellationReason.LIFECYCLE_PAUSE)
            stopTimer()
        }
    }
