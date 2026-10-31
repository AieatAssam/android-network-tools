package net.aieat.netswissknife.core.network.oui

/** Unicast, multicast (group bit set) or the all-ones broadcast address. */
enum class MacAddressKind { UNICAST, MULTICAST, BROADCAST }

/**
 * The U/L bit as it applies to an address. Only a unicast address has an owner whose
 * assignment can be private or registered; broadcast and multicast are groups, so the bit
 * does not apply to them.
 */
enum class LocalAdministration { LOCAL, UNIVERSAL, NOT_APPLICABLE }

/** Textual notations the MAC Lookup tool offers. */
enum class MacNotation { COLON, HYPHEN, DOTTED, BARE }

/** What the registry says about an address. Each case has its own explanation in the UI. */
sealed interface VendorResult {
    data class Known(
        val name: String,
        val blockSize: OuiBlockSize,
    ) : VendorResult

    /** Universally administered, but no registry block covers it. */
    data object Unknown : VendorResult

    /** Randomised or private address: the first octet's U/L bit is set, so the OUI means nothing. */
    data object LocallyAdministered : VendorResult

    /** Broadcast has no owner. */
    data object NotApplicable : VendorResult
}

/**
 * One analysed address. [hex] is 12 uppercase digits for a MAC or 6 for an OUI prefix on its own.
 * For a prefix, [kind] and [localAdministration] come from its first octet alone.
 */
data class MacAddressInfo(
    val hex: String,
    val kind: MacAddressKind,
    val localAdministration: LocalAdministration,
    val vendor: VendorResult,
) {
    val isPrefixOnly: Boolean get() = hex.length == PREFIX_DIGITS

    /** True only for a unicast address with the U/L bit set. */
    val locallyAdministered: Boolean get() = localAdministration == LocalAdministration.LOCAL

    fun format(
        notation: MacNotation,
        uppercase: Boolean,
    ): String {
        val value = if (uppercase) hex else hex.lowercase()
        return when (notation) {
            MacNotation.COLON -> value.chunked(OCTET_DIGITS).joinToString(":")
            MacNotation.HYPHEN -> value.chunked(OCTET_DIGITS).joinToString("-")
            MacNotation.DOTTED -> value.chunked(DOTTED_GROUP_DIGITS).joinToString(".")
            MacNotation.BARE -> value
        }
    }

    private companion object {
        const val PREFIX_DIGITS = 6
        const val OCTET_DIGITS = 2
        const val DOTTED_GROUP_DIGITS = 4
    }
}

/** A pasted entry after analysis: either an address or the reason it was rejected. */
sealed interface MacLookupEntry {
    val input: String

    data class Address(
        override val input: String,
        val info: MacAddressInfo,
        /** How many pasted entries were this same address in another notation (1 when unique). */
        val occurrences: Int = 1,
    ) : MacLookupEntry

    data class Rejected(
        override val input: String,
        val problem: MacInputProblem,
    ) : MacLookupEntry
}

/** Analyses pasted MAC addresses and OUI prefixes against an [OuiRegistry]. Pure and offline. */
class MacLookupEngine(
    private val registry: OuiRegistry,
) {
    fun lookup(input: String): MacLookupEntry {
        val parsed = MacInputParser.parse(input)
        return when (parsed) {
            is MacParseResult.Invalid -> MacLookupEntry.Rejected(input, parsed.problem)
            is MacParseResult.Parsed -> MacLookupEntry.Address(input, describe(parsed.hex))
        }
    }

    fun lookupAll(text: String): List<MacLookupEntry> = MacInputParser.splitEntries(text).map(::lookup)

    private fun describe(hex: String): MacAddressInfo {
        val firstOctet = hex.take(OCTET_DIGITS).toInt(HEX_RADIX)
        val kind = kindOf(hex, firstOctet)
        val localAdministration = localAdministrationOf(kind, firstOctet)
        val vendor = vendorOf(hex, kind, localAdministration)
        return MacAddressInfo(hex, kind, localAdministration, vendor)
    }

    private fun localAdministrationOf(
        kind: MacAddressKind,
        firstOctet: Int,
    ): LocalAdministration =
        when {
            kind != MacAddressKind.UNICAST -> LocalAdministration.NOT_APPLICABLE
            firstOctet and LOCAL_BIT != 0 -> LocalAdministration.LOCAL
            else -> LocalAdministration.UNIVERSAL
        }

    private fun kindOf(
        hex: String,
        firstOctet: Int,
    ): MacAddressKind =
        when {
            hex.all { it == 'F' } -> MacAddressKind.BROADCAST
            firstOctet and GROUP_BIT != 0 -> MacAddressKind.MULTICAST
            else -> MacAddressKind.UNICAST
        }

    private fun vendorOf(
        hex: String,
        kind: MacAddressKind,
        localAdministration: LocalAdministration,
    ): VendorResult =
        when {
            kind == MacAddressKind.BROADCAST -> VendorResult.NotApplicable
            localAdministration == LocalAdministration.LOCAL -> VendorResult.LocallyAdministered
            else -> registry.match(hex)?.let { VendorResult.Known(it.vendor, it.blockSize) } ?: VendorResult.Unknown
        }

    private companion object {
        const val GROUP_BIT = 0x01
        const val LOCAL_BIT = 0x02
        const val OCTET_DIGITS = 2
        const val HEX_RADIX = 16
    }
}

/**
 * Merges pasted entries that name the same address. The first notation is kept, with the number
 * of entries it stands for. Rejected entries and OUI prefixes keep their place and are not merged.
 */
object MacLookupDedupe {
    fun collapse(entries: List<MacLookupEntry>): List<MacLookupEntry> {
        val result = mutableListOf<MacLookupEntry>()
        val indexByHex = HashMap<String, Int>()
        entries.forEach { entry ->
            if (entry is MacLookupEntry.Address) {
                val existing = indexByHex[entry.info.hex]
                if (existing != null) {
                    val first = result[existing] as MacLookupEntry.Address
                    result[existing] = first.copy(occurrences = first.occurrences + entry.occurrences)
                    return@forEach
                }
                indexByHex[entry.info.hex] = result.size
            }
            result += entry
        }
        return result
    }
}
