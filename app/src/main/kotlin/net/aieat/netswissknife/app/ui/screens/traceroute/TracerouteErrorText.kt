package net.aieat.netswissknife.app.ui.screens.traceroute

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.AvailabilityReason
import net.aieat.netswissknife.app.platform.OperationAvailability
import net.aieat.netswissknife.app.platform.denialMessage
import net.aieat.netswissknife.app.traceroute.NativeTracerouteUnavailableException
import net.aieat.netswissknife.app.traceroute.TracerouteHostResolutionTimeoutException
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.UiText
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.resolverErrorMessage
import java.net.UnknownHostException

/** The user-facing copy for each way a trace can fail, with the English developer message kept alongside. */
internal object TracerouteErrorText {
    private const val TOO_LONG_MESSAGE =
        "Requested trace exceeds the 20-minute time limit; reduce max hops, probes per hop, or timeout"

    fun unavailable(availability: OperationAvailability): TracerouteUiState.Error {
        val id =
            when (availability.reason) {
                AvailabilityReason.NO_ROUTE -> R.string.traceroute_error_no_route
                AvailabilityReason.LOCAL_NETWORK_PERMISSION_DENIED -> R.string.traceroute_error_local_permission
                else -> R.string.traceroute_error_unavailable
            }
        return TracerouteUiState.Error(UiText.Res(id), availability.denialMessage())
    }

    fun tooLong(): TracerouteUiState.Error {
        val text = UiText.Res(R.string.traceroute_error_too_long)
        return TracerouteUiState.Error(text, TOO_LONG_MESSAGE)
    }

    fun validation(result: TracerouteFlowResult.ValidationError): TracerouteUiState.Error {
        val text = ErrorTextMapper.map(result.info, result.message)
        return TracerouteUiState.Error(text, result.message)
    }

    fun failure(
        error: Throwable,
        host: String,
    ): TracerouteUiState.Error {
        val message = resolverErrorMessage(error, host) ?: error.message ?: "Traceroute failed"
        val text =
            when (error) {
                is UnknownHostException -> {
                    UiText.Res(R.string.traceroute_error_host_not_found, listOf(host))
                }

                is TracerouteHostResolutionTimeoutException -> {
                    UiText.Res(R.string.traceroute_error_lookup_timeout, listOf(host))
                }

                is NativeTracerouteUnavailableException -> {
                    UiText.Res(R.string.traceroute_error_engine_unavailable)
                }

                else -> {
                    UiText.Res(R.string.traceroute_error_generic)
                }
            }
        return TracerouteUiState.Error(text, message)
    }

    /** The error for a trace that ended for a reason other than the user or the time limit, or null. */
    fun interrupted(reason: CancellationReason?): TracerouteUiState.Error? =
        when (reason) {
            null, CancellationReason.USER_STOP, CancellationReason.LIFECYCLE_PAUSE -> {
                null
            }

            CancellationReason.DEADLINE_EXCEEDED -> {
                TracerouteUiState.Error(UiText.Res(R.string.traceroute_error_timed_out), "Traceroute timed out")
            }

            else -> {
                TracerouteUiState.Error(UiText.Res(R.string.traceroute_error_interrupted), "Traceroute was interrupted")
            }
        }

    fun noHops(host: String): TracerouteUiState.Error =
        TracerouteUiState.Error(
            UiText.Res(R.string.traceroute_error_no_hops, listOf(host)),
            "No route found to $host",
        )
}
