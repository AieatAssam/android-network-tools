package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanScanSummary

/** Builds share and copy text from confirmed devices only; uncertain diagnostics are intentionally excluded. */
internal fun buildLanShareText(
    summary: LanScanSummary,
    labels: LanShareLabels,
): String =
    buildString {
        appendLine(labels.title)
        appendLine(labels.headline)
        appendLine(labels.facts)
        appendLine()
        summary.hosts.forEach { host ->
            append(host.ip)
            host.hostname?.let { append(" ($it)") }
            host.vendor?.let { append(" [$it]") }
            host.pingTimeMs?.let { append(" ").append(labels.latencyFormat.format(it)) }
            if (host.openPorts.isNotEmpty()) {
                append(" ").append(labels.portsFormat.format(host.openPorts.joinToString(",")))
            }
            appendLine()
        }
    }
