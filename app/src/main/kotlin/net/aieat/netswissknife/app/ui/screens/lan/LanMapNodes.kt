package net.aieat.netswissknife.app.ui.screens.lan

/** Most device nodes the network map draws before it offers "show all". */
internal const val LAN_MAP_NODE_LIMIT = 24

/** Gap between map cells, in dp. */
internal const val LAN_MAP_SPACING_DP = 8

/**
 * Number of map cells that fit side by side in [availableWidthDp]. Always at least one, so a very
 * narrow card still lays out a single column.
 */
internal fun lanMapColumnCount(
    availableWidthDp: Float,
    nodeWidthDp: Float,
    spacingDp: Float,
): Int {
    val fit = ((availableWidthDp + spacingDp) / (nodeWidthDp + spacingDp)).toInt()
    return fit.coerceAtLeast(1)
}

/** Nodes to draw ([visible]) and how many were left out ([hiddenCount]) to keep the map bounded. */
internal data class LanMapPreview<T>(
    val visible: List<T>,
    val hiddenCount: Int,
)

/** Applies the node limit unless the user asked to see every node. */
internal fun <T> lanMapVisibleNodes(
    items: List<T>,
    showAll: Boolean,
    limit: Int = LAN_MAP_NODE_LIMIT,
): LanMapPreview<T> =
    if (showAll || items.size <= limit) {
        LanMapPreview(visible = items, hiddenCount = 0)
    } else {
        LanMapPreview(visible = items.take(limit), hiddenCount = items.size - limit)
    }

/**
 * Caption under a map node: the first label of the hostname, or the last IP octet when no
 * hostname is known. Vendor names are left out because the device list already shows them.
 */
internal fun lanMapNodeCaption(
    hostname: String?,
    ip: String,
): String =
    hostname
        ?.substringBefore('.')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: ip.substringAfterLast('.')
