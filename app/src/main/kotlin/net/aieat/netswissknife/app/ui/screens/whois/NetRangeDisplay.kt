package net.aieat.netswissknife.app.ui.screens.whois

/**
 * Shows a registry range once when both ends are the same (a single ASN or a single address),
 * instead of "15169-15169". Ranges with distinct ends are returned unchanged.
 */
fun netRangeDisplay(range: String): String {
    val parts = range.split("-", limit = 2)
    return if (parts.size == 2 && parts[0].trim() == parts[1].trim()) parts[0].trim() else range
}
