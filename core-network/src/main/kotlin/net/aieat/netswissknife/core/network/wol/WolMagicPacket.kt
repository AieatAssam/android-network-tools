package net.aieat.netswissknife.core.network.wol

/** Why a MAC string cannot be used as a Wake-on-LAN target. */
enum class WolMacProblem {
    /** Nothing but whitespace was entered. */
    BLANK,

    /** The address does not contain exactly 12 hexadecimal digits. */
    LENGTH,

    /** A character other than a hexadecimal digit or a separator was used. */
    CHARACTERS,

    /** Separators are mixed, misplaced, or not one of the accepted notations. */
    SEPARATORS,

    /** The group bit of the first octet is set, so this is a multicast address. */
    MULTICAST,

    /** FF:FF:FF:FF:FF:FF, the link-layer broadcast address. */
    BROADCAST,

    /** 00:00:00:00:00:00 is not a device address. */
    ZERO,
}

/** Why a broadcast or target address cannot be used as a Wake-on-LAN destination. */
enum class WolBroadcastProblem {
    /** Nothing but whitespace was entered. */
    BLANK,

    /** Not a dotted-decimal IPv4 literal: a host name, IPv6, a malformed or out-of-range address. */
    NOT_IPV4,
}

/**
 * Builder and validator for Wake-on-LAN magic packets.
 *
 * A magic packet is 6 bytes of 0xFF followed by the target MAC address
 * repeated 16 times (102 bytes total), sent as a UDP broadcast.
 *
 * This object is the single source of truth for MAC and destination validity; the
 * domain use case, the ViewModel and the screen all delegate here.
 */
object WolMagicPacket {

    private const val MAC_BYTES = 6
    private const val HEADER_BYTES = 6
    private const val MAC_REPETITIONS = 16
    private const val MAC_HEX_DIGITS = MAC_BYTES * 2
    private const val IPV4_OCTETS = 4
    private const val MAX_OCTET = 255
    private const val MAX_OCTET_DIGITS = 3
    private const val MULTICAST_BIT = 0x01
    private const val HEX_DIGITS = "0123456789abcdefABCDEF"

    const val PACKET_SIZE: Int = HEADER_BYTES + MAC_REPETITIONS * MAC_BYTES

    /**
     * One notation per pattern. The colon/hyphen alternative uses a back-reference so that the
     * separator cannot change mid-address (`AA:BB-CC:…` is rejected).
     */
    private val ACCEPTED_NOTATION =
        Regex(
            "^(?:[0-9A-Fa-f]{2}([:-])(?:[0-9A-Fa-f]{2}\\1){4}[0-9A-Fa-f]{2}" +
                "|(?:[0-9A-Fa-f]{4}\\.){2}[0-9A-Fa-f]{4}" +
                "|[0-9A-Fa-f]{12})$",
        )

    /**
     * Returns the first reason [mac] is not a unicast Wake-on-LAN target, or null when it is.
     * Accepted notations are `AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`, `AABB.CCDD.EEFF` and
     * `AABBCCDDEEFF`, each optionally surrounded by whitespace.
     */
    fun macProblem(mac: String): WolMacProblem? {
        val trimmed = mac.trim()
        val digits = trimmed.filterNot { it == ':' || it == '-' || it == '.' }
        return when {
            trimmed.isEmpty() -> WolMacProblem.BLANK
            digits.any { it !in HEX_DIGITS } -> WolMacProblem.CHARACTERS
            digits.length != MAC_HEX_DIGITS -> WolMacProblem.LENGTH
            !ACCEPTED_NOTATION.matches(trimmed) -> WolMacProblem.SEPARATORS
            digits.all { it == 'f' || it == 'F' } -> WolMacProblem.BROADCAST
            digits.all { it == '0' } -> WolMacProblem.ZERO
            (digits.substring(0, 2).toInt(16) and MULTICAST_BIT) == MULTICAST_BIT -> WolMacProblem.MULTICAST
            else -> null
        }
    }

    /** True when [mac] is a unicast MAC in an accepted notation (see [macProblem]). */
    fun isValidMac(mac: String): Boolean = macProblem(mac) == null

    /** Canonical uppercase colon-separated form for a valid [mac], or null when it is invalid. */
    fun canonicalMacOrNull(mac: String): String? = if (isValidMac(mac)) canonical(parseMac(mac)) else null

    /** Canonical uppercase colon-separated form, e.g. `AA:BB:CC:DD:EE:FF`. */
    fun normalizeMac(mac: String): String = requireNotNull(canonicalMacOrNull(mac)) { "Invalid MAC address: $mac" }

    /**
     * Parses [mac] into its 6 raw bytes.
     * @throws IllegalArgumentException if the address is not a valid MAC.
     */
    fun parseMac(mac: String): ByteArray {
        require(isValidMac(mac)) { "Invalid MAC address: $mac" }
        val hex = mac.trim().filterNot { it == ':' || it == '-' || it == '.' }
        return ByteArray(MAC_BYTES) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** Reason a broadcast or target address is unusable, or null for a dotted-decimal IPv4 literal. */
    fun broadcastProblem(address: String): WolBroadcastProblem? =
        when {
            address.isBlank() -> WolBroadcastProblem.BLANK
            parseIpv4(address) == null -> WolBroadcastProblem.NOT_IPV4
            else -> null
        }

    /**
     * Parses a dotted-decimal IPv4 literal such as `192.168.1.255` into its 4 octets.
     * Host names, IPv6, leading-zero octets (ambiguous across resolvers) and values above 255
     * return null. The input is trimmed first.
     */
    fun parseIpv4(address: String): ByteArray? {
        val parts = address.trim().split('.')
        val values = parts.mapNotNull(::parseOctet)
        return if (parts.size == IPV4_OCTETS && values.size == IPV4_OCTETS) {
            ByteArray(IPV4_OCTETS) { values[it].toByte() }
        } else {
            null
        }
    }

    /**
     * Builds the 102-byte magic packet payload for [mac].
     * @throws IllegalArgumentException if the address is not a valid MAC.
     */
    fun build(mac: String): ByteArray {
        val macBytes = parseMac(mac)
        val packet = ByteArray(PACKET_SIZE)
        for (i in 0 until HEADER_BYTES) packet[i] = 0xFF.toByte()
        for (rep in 0 until MAC_REPETITIONS) {
            macBytes.copyInto(packet, destinationOffset = HEADER_BYTES + rep * MAC_BYTES)
        }
        return packet
    }

    private fun parseOctet(part: String): Int? {
        val digitsOnly = part.isNotEmpty() && part.length <= MAX_OCTET_DIGITS && part.all { it in '0'..'9' }
        val value = if (digitsOnly) part.toInt() else null
        return value?.takeIf { (part.length == 1 || part[0] != '0') && it <= MAX_OCTET }
    }

    private fun canonical(bytes: ByteArray): String = bytes.joinToString(":") { "%02X".format(it) }
}
