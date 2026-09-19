package net.aieat.netswissknife.core.network.lan

import java.util.Locale

/** IEEE OUI registry backed by a lazily loaded classpath resource. */
object OuiDatabase {
    private const val RESOURCE = "/oui/oui-prefixes.tsv"

    private val entries: Map<String, String> by lazy {
        val loaded = OuiDatabase::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2 && parts[0].matches(Regex("[0-9A-F]{6}"))) {
                        parts[0] to parts[1].trim()
                    } else {
                        null
                    }
                }.toMap()
            }
            ?: emptyMap()
        loaded + OVERRIDES
    }

    /** Number of registered 24-bit prefixes available to the lookup. */
    val size: Int
        get() = entries.size

    /** Returns true for locally administered MAC addresses, which have no reliable OUI. */
    fun isLocallyAdministered(macAddress: String): Boolean {
        val first = normalise(macAddress)?.take(2)?.toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    /** Looks up a MAC in a separator-agnostic, case-insensitive form. */
    fun lookup(macAddress: String): String? {
        if (isLocallyAdministered(macAddress)) return null
        val normalised = normalise(macAddress) ?: return null
        if (normalised.startsWith("000000")) return null
        return entries[normalised.take(6)]
    }

    private fun normalise(macAddress: String): String? {
        val value = macAddress
            .filter { it.isLetterOrDigit() }
            .uppercase(Locale.ROOT)
        return value.takeIf { it.length >= 6 && it.take(6).all { char -> char in "0123456789ABCDEF" } }
    }

    // Friendly names and historical behavior retained over the raw IEEE labels.
    private val OVERRIDES = mapOf(
        "3C5AB4" to "Google",
        "B827EB" to "Raspberry Pi Foundation",
        "DCA632" to "Raspberry Pi",
    )
}
