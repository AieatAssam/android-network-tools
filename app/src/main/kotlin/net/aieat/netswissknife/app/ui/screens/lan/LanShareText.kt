package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanScanSummary

/** Builds share text from confirmed hosts only; uncertain diagnostics are intentionally excluded. */
internal fun buildLanShareText(summary: LanScanSummary): String = buildString {
    appendLine("LAN scan – ${summary.subnet}")
    appendLine("Confirmed hosts: ${summary.aliveHosts} / ${summary.totalScanned}")
    appendLine("Duration: ${summary.scanDurationMs}ms")
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
