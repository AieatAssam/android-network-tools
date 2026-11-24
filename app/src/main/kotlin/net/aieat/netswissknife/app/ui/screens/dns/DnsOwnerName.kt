package net.aieat.netswissknife.app.ui.screens.dns

/**
 * An answer's owner name is shown only when it differs from the queried name. Comparison ignores
 * letter case and a trailing root dot, so "Example.com." and "example.com" count as the same name.
 */
internal fun dnsShowsOwner(owner: String, queriedName: String): Boolean =
    normalizeDnsName(owner) != normalizeDnsName(queriedName)

private fun normalizeDnsName(name: String): String = name.trim().trimEnd('.').lowercase()
