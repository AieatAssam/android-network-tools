package net.aieat.netswissknife.app.ui.screens.lan

/**
 * Text for the Copy and Share actions, resolved from strings.xml by the caller so the builder stays
 * pure. The headline and facts use the same strings as the summary card, so the export reads as the
 * screen does.
 */
internal data class LanShareLabels(
    /** share_subject_lan, for example "LAN scan – 10.0.0.0/24". */
    val title: String,
    /** The summary headline, for example "53 devices found". */
    val headline: String,
    /** Addresses and scan duration, for example "254 addresses · 30.8 s". */
    val facts: String,
    /** A printf pattern for the latency of one device, for example "%d ms". */
    val latencyFormat: String,
    /** A printf pattern for the open ports of one device, for example "ports:%s". */
    val portsFormat: String,
)
