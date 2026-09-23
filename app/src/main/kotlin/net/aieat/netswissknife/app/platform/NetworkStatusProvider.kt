package net.aieat.netswissknife.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.aieat.netswissknife.core.network.net.containsLocalNetworkPermissionDenied

/** Injectable source for live connectivity state, with a harmless default for isolated ViewModel tests. */
interface NetworkStatusProvider {
    val status: StateFlow<NetworkStatus>
}

object NoOpNetworkStatusProvider : NetworkStatusProvider {
    private val state = MutableStateFlow(
        NetworkStatus(hasInternet = true, hasLocalNetwork = true, transport = Transport.OTHER)
    ).asStateFlow()
    override val status: StateFlow<NetworkStatus> = state
}

enum class NetworkErrorKind {
    GENERAL,
    LOCAL_NETWORK_PERMISSION_DENIED,
}

fun Throwable?.toNetworkErrorKind(): NetworkErrorKind =
    if (containsLocalNetworkPermissionDenied()) {
        NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED
    } else {
        NetworkErrorKind.GENERAL
    }
