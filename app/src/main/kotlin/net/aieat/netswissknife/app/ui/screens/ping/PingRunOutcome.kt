package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.i18n.UiText
import net.aieat.netswissknife.core.network.ping.PingPacketResult

/**
 * True when the run never reached a resolved address: every packet is the resolver failure.
 * Such a run is an error (the name is wrong or unknown), not a 100% loss result.
 */
internal fun isUnresolvedHostRun(packets: List<PingPacketResult>): Boolean =
    packets.isNotEmpty() && packets.all { it.unresolvedHost }

/**
 * The error state for a name that does not resolve: a plain reason with Retry, not a loss figure.
 * The resolver text is kept as the developer message.
 */
internal fun unresolvedHostState(
    host: String,
    packets: List<PingPacketResult>,
): PingUiState.Error =
    PingUiState.Error(
        text =
            UiText.Res(
                R.string.ping_unresolved_message,
                args = listOf(host),
                developerFallback = resolverMessage(host, packets),
            ),
        message = resolverMessage(host, packets),
    )

private fun resolverMessage(
    host: String,
    packets: List<PingPacketResult>,
): String = packets.firstNotNullOfOrNull { it.errorMessage } ?: "Unknown host: $host"

/** A failed name lookup is not a host the user reached, so it is not kept in recents. */
internal fun shouldRememberPingHost(packet: PingPacketResult): Boolean = !packet.unresolvedHost
