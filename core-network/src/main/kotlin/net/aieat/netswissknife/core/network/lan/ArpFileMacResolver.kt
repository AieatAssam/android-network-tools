package net.aieat.netswissknife.core.network.lan

import java.io.File

/** Reads Linux's ARP cache after probes have had a chance to populate it. */
class ArpFileMacResolver(
    private val reader: () -> String = { File("/proc/net/arp").readText() },
) : MacResolver {

    private val table: String by lazy {
        runCatching { reader() }.getOrDefault("")
    }

    override val supported: Boolean
        get() = table.isNotBlank()

    override suspend fun resolve(ip: String): String? = parseArpTable(table)[ip]

    companion object {
        /** Parses Linux /proc/net/arp and ignores incomplete entries. */
        fun parseArpTable(content: String): Map<String, String> {
            return content.lineSequence()
                .dropWhile { it.trimStart().startsWith("IP address", ignoreCase = true) }
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 4) return@mapNotNull null
                    val mac = parts[3].uppercase()
                    if (!mac.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")) ||
                        mac == "00:00:00:00:00:00"
                    ) {
                        null
                    } else {
                        parts[0] to mac
                    }
                }
                .toMap()
        }
    }
}
