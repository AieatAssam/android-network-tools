package net.aieat.netswissknife.app.ui.screens.dns

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** Compact TTL such as "59s", "1m 30s" or "1h 1m". Keeps the remainder so no precision is dropped. */
internal fun dnsTtlLabel(ttlSeconds: Long): String {
    val hours = ttlSeconds / SECONDS_PER_HOUR
    val minutes = (ttlSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = ttlSeconds % SECONDS_PER_MINUTE
    return when {
        ttlSeconds < SECONDS_PER_MINUTE -> "${ttlSeconds}s"
        ttlSeconds < SECONDS_PER_HOUR -> if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
        else -> if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
    }
}
