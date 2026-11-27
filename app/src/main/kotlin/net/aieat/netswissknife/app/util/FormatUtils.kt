package net.aieat.netswissknife.app.util

fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

private const val SI_KILO = 1_000L
private const val SI_MEGA = 1_000_000L
private const val SI_GIGA = 1_000_000_000L

/**
 * Decimal (SI) byte sizes, matching the decimal Mbps used for throughput. [formatBytes] keeps its
 * binary units for tools that report memory-style sizes.
 */
fun formatBytesSi(bytes: Long): String =
    when {
        bytes < SI_KILO -> "$bytes B"
        bytes < SI_MEGA -> "%.1f KB".format(bytes / SI_KILO.toDouble())
        bytes < SI_GIGA -> "%.1f MB".format(bytes / SI_MEGA.toDouble())
        else -> "%.2f GB".format(bytes / SI_GIGA.toDouble())
    }
