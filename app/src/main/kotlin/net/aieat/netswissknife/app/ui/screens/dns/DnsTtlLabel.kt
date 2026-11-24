package net.aieat.netswissknife.app.ui.screens.dns

/** Compact TTL such as "59s", "1m 30s" or "1h 1m". Keeps the remainder so no precision is dropped. */
internal fun dnsTtlLabel(ttlSeconds: Long): String {
    val hours = ttlSeconds / 3_600
    val minutes = (ttlSeconds % 3_600) / 60
    val seconds = ttlSeconds % 60
    return when {
        ttlSeconds < 60 -> "${ttlSeconds}s"
        ttlSeconds < 3_600 -> if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
        else -> if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
    }
}
