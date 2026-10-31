package net.aieat.netswissknife.core.network.lan

import net.aieat.netswissknife.core.network.oui.OuiBlockSize
import net.aieat.netswissknife.core.network.oui.OuiMatch
import net.aieat.netswissknife.core.network.oui.OuiRegistry
import java.util.Locale

/** IEEE OUI registry backed by a lazily loaded classpath resource. */
object OuiDatabase : OuiRegistry {
    private const val RESOURCE = "/oui/oui-prefixes.tsv"
    private val PLAIN_MAC = Regex("[0-9A-F]{12}")
    private val COLON_MAC = Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
    private val HYPHEN_MAC = Regex("(?:[0-9A-F]{2}-){5}[0-9A-F]{2}")
    private val DOTTED_MAC = Regex("[0-9A-F]{4}(?:\\.[0-9A-F]{4}){2}")
    private const val OUI_DIGITS = 6
    private const val MA_M_DIGITS = 7
    private const val MAC_DIGITS = 12
    private const val LOCAL_ADMIN_BIT = 0x02
    private const val FIRST_OCTET_DIGITS = 2
    private const val HEX_RADIX = 16
    private const val ZERO_OUI = "000000"
    private const val HEX_DIGITS = "0123456789ABCDEF"
    private val PREFIX_LENGTHS = listOf(9, MA_M_DIGITS, OUI_DIGITS)
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
        val hex = normalise(macAddress) ?: return false
        return isLocallyAdministeredHex(hex)
    }

    /** Looks up a complete MAC in plain, colon, hyphen, or Cisco dotted notation. */
    fun lookup(macAddress: String): String? = normalise(macAddress)?.let { match(it)?.vendor }

    /**
     * Longest registered block for [hexDigits]: hex without separators (either case), 6 digits for an
     * OUI prefix or 12 for a MAC. Locally administered and all-zero prefixes never match.
     */
    override fun match(hexDigits: String): OuiMatch? {
        val hex = hexDigits.uppercase(Locale.ROOT)
        if (!isLookupable(hex)) return null
        val length = PREFIX_LENGTHS.firstOrNull { it <= hex.length && entries.containsKey(hex.take(it)) }
        return length?.let { OuiMatch(entries.getValue(hex.take(it)), blockSizeFor(it)) }
    }

    private fun isLookupable(hex: String): Boolean {
        val validLength = hex.length in OUI_DIGITS..MAC_DIGITS
        val allHex = hex.all { it in HEX_DIGITS }
        return validLength && allHex && !isLocallyAdministeredHex(hex) && !hex.startsWith(ZERO_OUI)
    }

    private fun blockSizeFor(prefixLength: Int): OuiBlockSize =
        when (prefixLength) {
            OUI_DIGITS -> OuiBlockSize.MA_L
            MA_M_DIGITS -> OuiBlockSize.MA_M
            else -> OuiBlockSize.MA_S
        }

    private fun isLocallyAdministeredHex(hexDigits: String): Boolean {
        val firstOctet = hexDigits.take(FIRST_OCTET_DIGITS).toInt(HEX_RADIX)
        return firstOctet and LOCAL_ADMIN_BIT != 0
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
