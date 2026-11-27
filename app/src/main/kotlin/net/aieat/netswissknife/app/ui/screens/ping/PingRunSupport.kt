package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.AvailabilityReason
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.platform.LiteralDestinationClassifier
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.OperationAvailability
import net.aieat.netswissknife.app.platform.denialMessage
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.UiText
import net.aieat.netswissknife.core.domain.ContinuousPingParams
import net.aieat.netswissknife.core.domain.PingParams
import net.aieat.netswissknife.core.network.ErrorInfo
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.ping.PingRequest

/**
 * Whether [host] can be pinged on the current network. The local-network permission is only
 * asked of [linkInfoProvider] when the target needs the local network.
 */
internal fun pingAvailabilityFor(
    host: String,
    linkInfoProvider: LinkInfoProvider,
    networkStatusProvider: NetworkStatusProvider,
): OperationAvailability {
    val target = LiteralDestinationClassifier.target(host)
    val requirement = OperationAvailability.requirementFor(target)
    val localPermission =
        if (
            requirement == OperationRequirement.LOCAL_NETWORK
        ) {
            linkInfoProvider.localNetworkPermissionAllowed()
        } else {
            null
        }
    return OperationAvailability.classifyObserved(
        target = target,
        status = networkStatusProvider.status.value,
        localNetworkPermissionAllowed = localPermission,
    )
}

internal fun pingAvailabilityError(availability: OperationAvailability): PingUiState.Error {
    val text =
        when (availability.reason) {
            AvailabilityReason.NO_ROUTE -> {
                UiText.Res(R.string.ping_error_no_route)
            }

            AvailabilityReason.LOCAL_NETWORK_PERMISSION_DENIED -> {
                UiText.Res(R.string.ping_error_local_permission)
            }

            else -> {
                UiText.Res(R.string.ping_error_unavailable)
            }
        }
    return PingUiState.Error(text, availability.denialMessage())
}

internal fun genericPingError(e: Exception): PingUiState.Error {
    val developerMessage = e.message ?: "Ping failed"
    return PingUiState.Error(UiText.Res(R.string.ping_error_generic), developerMessage)
}

internal fun typedPingError(
    info: ErrorInfo,
    developerMessage: String,
): PingUiState.Error =
    PingUiState.Error(
        text = ErrorTextMapper.map(info, developerMessage),
        message = developerMessage,
    )

/** A bounded run that finished without a single packet. */
internal fun pingNoResponseError(host: String): PingUiState.Error =
    PingUiState.Error(
        UiText.Res(R.string.ping_error_no_response, listOf(host)),
        "No response received from $host",
    )

/** Continuous mode refuses to start on a network whose connectivity is not validated. */
internal fun pingConnectivityUnvalidatedError(): PingUiState.Error =
    PingUiState.Error(
        UiText.Res(R.string.ping_error_connectivity_unvalidated),
        "Continuous ping requires validated connectivity",
    )

/** The continuous session log could not be created, so the run does not start. */
internal fun pingLogUnavailableError(e: Exception): PingUiState.Error {
    val developerMessage = "Could not create the continuous Ping session log: ${e.message}"
    val text =
        UiText.Res(
            R.string.ping_continuous_log_unavailable,
            developerFallback = developerMessage,
        )
    return PingUiState.Error(text, developerMessage)
}

internal fun PingParams.toPingRequest(): PingRequest =
    PingRequest(
        host = host,
        count = count,
        timeoutMs = timeoutMs,
        intervalMs = intervalMs,
        payloadBytes = payloadBytes,
        ttl = ttl,
    )

/** A continuous run has no fixed count; the request carries 0. */
internal fun ContinuousPingParams.toPingRequest(): PingRequest =
    PingRequest(
        host = host,
        count = 0,
        timeoutMs = timeoutMs,
        intervalMs = intervalMs,
        payloadBytes = payloadBytes,
        ttl = ttl,
    )
