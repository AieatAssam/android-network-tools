package net.aieat.netswissknife.core.network.lan

import java.io.File

/** Reads Linux's ARP cache after probes have had a chance to populate it. */
class ArpFileMacResolver(
    private val reader: () -> String = { File("/proc/net/arp").readText() },
) : MacResolver {

    private val table: ArpTableSnapshot by lazy {
        readSnapshot()
    }

    override val supported: Boolean
        get() = table.supported

    override suspend fun resolve(ip: String): String? = table.resolve(ip)

    /** Captures the current kernel table once for the post-probe enrichment pass. */
    override fun snapshot(): MacResolver = readSnapshot()

    private fun readSnapshot(): ArpTableSnapshot = try {
        ArpTableSnapshot(content = reader(), supported = true)
    } catch (_: Exception) {
        ArpTableSnapshot(content = "", supported = false)
    }

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

    private class ArpTableSnapshot(content: String, override val supported: Boolean) : MacResolver {
        private val entries = parseArpTable(content)

        override suspend fun resolve(ip: String): String? = entries[ip]
    }
}
