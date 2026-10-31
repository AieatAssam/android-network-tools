package net.aieat.netswissknife.core.network.oui

/** Unicast, multicast (group bit set) or the all-ones broadcast address. */
enum class MacAddressKind { UNICAST, MULTICAST, BROADCAST }

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
 * For a prefix, [kind] and [locallyAdministered] come from its first octet alone.
 */
data class MacAddressInfo(
    val hex: String,
    val kind: MacAddressKind,
    val locallyAdministered: Boolean,
    val vendor: VendorResult,
) {
    val isPrefixOnly: Boolean get() = hex.length == PREFIX_DIGITS

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
        val locallyAdministered = firstOctet and LOCAL_BIT != 0
        val vendor = vendorOf(hex, kind, locallyAdministered)
        return MacAddressInfo(hex, kind, locallyAdministered, vendor)
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
        locallyAdministered: Boolean,
    ): VendorResult =
        when {
            kind == MacAddressKind.BROADCAST -> VendorResult.NotApplicable
            locallyAdministered -> VendorResult.LocallyAdministered
            else -> registry.match(hex)?.let { VendorResult.Known(it.vendor, it.blockSize) } ?: VendorResult.Unknown
        }

    private companion object {
        const val GROUP_BIT = 0x01
        const val LOCAL_BIT = 0x02
        const val OCTET_DIGITS = 2
        const val HEX_RADIX = 16
    }
}
