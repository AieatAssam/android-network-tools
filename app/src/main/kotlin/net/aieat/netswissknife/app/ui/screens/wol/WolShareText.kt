package net.aieat.netswissknife.app.ui.screens.wol

import net.aieat.netswissknife.core.network.wol.WolSendReport
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Plain-text Wake-on-LAN report for Copy and Share. It holds only what the send report carries
 * (target MAC, broadcast address, UDP port, packet count) plus the time the text was generated.
 * [generatedAt] is injected so the output is stable under test.
 */
internal fun buildWolShareText(
    report: WolSendReport,
    generatedAt: Instant,
): String =
    buildString {
        appendLine("Wake-on-LAN – magic packet sent")
        appendLine("Target MAC: ${report.macAddress}")
        appendLine("Broadcast: ${report.broadcastAddress}")
        appendLine("UDP port: ${report.port}")
        appendLine("Packets sent: ${report.packetsSent}")
        appendLine("Generated: ${generatedAt.truncatedTo(ChronoUnit.SECONDS)}")
        appendLine()
        appendLine("Wake-on-LAN is fire-and-forget: no delivery confirmation is returned.")
        appendLine()
        appendLine("Net Swiss Knife")
    }
