package net.aieat.netswissknife.app.ui.screens.topology

/**
 * The collapsed SNMP Configuration header names the target and version, so the folded card still
 * says what the discovery ran against (PHA-19). Null until a target is entered.
 */
internal fun topologyConfigSubtitle(
    target: String,
    version: String,
): String? {
    val trimmed = target.trim()
    return if (trimmed.isEmpty()) null else "$trimmed · $version"
}
