package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanScanSummary

/**
 * Builds share and copy text from confirmed hosts only; uncertain diagnostics are intentionally excluded.
 * [durationText] is the localised scan duration (see humanDurationText); it is passed in because the
 * formatter needs string resources.
 */
internal fun buildLanShareText(
    summary: LanScanSummary,
    durationText: String,
): String =
    buildString {
        appendLine("LAN scan – ${summary.subnet}")
        appendLine("Confirmed hosts: ${summary.aliveHosts} / ${summary.totalScanned}")
        appendLine("Duration: $durationText")
        appendLine()
        summary.hosts.forEach { host ->
            append(host.ip)
            host.hostname?.let { append(" ($it)") }
            host.vendor?.let { append(" [$it]") }
            append(" ${host.pingTimeMs}ms")
            if (host.openPorts.isNotEmpty()) append(" ports:${host.openPorts.joinToString(",")}")
            appendLine()
        }
    }
