package net.aieat.netswissknife.core.network.lan

import java.util.Locale

/** IEEE OUI registry backed by a lazily loaded classpath resource. */
object OuiDatabase {
    private const val RESOURCE = "/oui/oui-prefixes.tsv"
    private val PLAIN_MAC = Regex("[0-9A-F]{12}")
    private val COLON_MAC = Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
    private val HYPHEN_MAC = Regex("(?:[0-9A-F]{2}-){5}[0-9A-F]{2}")
    private val DOTTED_MAC = Regex("[0-9A-F]{4}(?:\\.[0-9A-F]{4}){2}")
    private val PREFIX_LENGTHS = listOf(9, 7, 6)
    private val VALID_PREFIX_LENGTHS = PREFIX_LENGTHS.toSet()

    private val entries: Map<String, String> by lazy {
        val loaded = OuiDatabase::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2 && parts[0].length in VALID_PREFIX_LENGTHS &&
                        parts[0].all { it in "0123456789ABCDEF" }
                    ) {
                        parts[0] to parts[1].trim()
                    } else {
                        null
                    }
                }.toMap()
            }
            ?: emptyMap()
        loaded + OVERRIDES
    }

    /** Number of registered 24-, 28-, and 36-bit prefixes available to the lookup. */
    val size: Int
        get() = entries.size

    /** Returns true for locally administered MAC addresses, which have no reliable OUI. */
    fun isLocallyAdministered(macAddress: String): Boolean {
        val first = normalise(macAddress)?.take(2)?.toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    /** Looks up a complete MAC in plain, colon, hyphen, or Cisco dotted notation. */
    fun lookup(macAddress: String): String? {
        if (isLocallyAdministered(macAddress)) return null
        val normalised = normalise(macAddress) ?: return null
        if (normalised.startsWith("000000")) return null
        for (length in PREFIX_LENGTHS) {
            entries[normalised.take(length)]?.let { return it }
        }
        return null
    }

    private fun normalise(macAddress: String): String? {
        val value = macAddress.trim().uppercase(Locale.ROOT)
        return when {
            PLAIN_MAC.matches(value) -> value
            COLON_MAC.matches(value) -> value.replace(":", "")
            HYPHEN_MAC.matches(value) -> value.replace("-", "")
            DOTTED_MAC.matches(value) -> value.replace(".", "")
            else -> null
        }
    }

    // Friendly names and historical behavior retained over the raw IEEE labels.
    private val OVERRIDES = mapOf(
        "3C5AB4" to "Google",
        "B827EB" to "Raspberry Pi Foundation",
        "DCA632" to "Raspberry Pi",
    )
}
