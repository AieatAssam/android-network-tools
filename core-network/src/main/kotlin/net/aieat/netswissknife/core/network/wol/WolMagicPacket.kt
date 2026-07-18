package net.aieat.netswissknife.core.network.wol

/**
 * Builder and validator for Wake-on-LAN magic packets.
 *
 * A magic packet is 6 bytes of 0xFF followed by the target MAC address
 * repeated 16 times (102 bytes total), sent as a UDP broadcast.
 */
object WolMagicPacket {

    const val PACKET_SIZE: Int = 6 + 16 * 6

    private val COLON_OR_HYPHEN = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")
    private val DOT_QUARTETS    = Regex("^([0-9A-Fa-f]{4}\\.){2}[0-9A-Fa-f]{4}$")
    private val BARE_HEX        = Regex("^[0-9A-Fa-f]{12}$")

    /**
     * Accepts the common MAC notations:
     * `AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`, `AABB.CCDD.EEFF`, `AABBCCDDEEFF`.
     */
    fun isValidMac(mac: String): Boolean {
        val trimmed = mac.trim()
        return COLON_OR_HYPHEN.matches(trimmed) ||
            DOT_QUARTETS.matches(trimmed) ||
            BARE_HEX.matches(trimmed)
    }

    /** Canonical uppercase colon-separated form, e.g. `AA:BB:CC:DD:EE:FF`. */
    fun normalizeMac(mac: String): String =
        parseMac(mac).joinToString(":") { "%02X".format(it) }

    /**
     * Parses [mac] into its 6 raw bytes.
     * @throws IllegalArgumentException if the address is not a valid MAC.
     */
    fun parseMac(mac: String): ByteArray {
        val trimmed = mac.trim()
        require(isValidMac(trimmed)) { "Invalid MAC address: $mac" }
        val hex = trimmed.replace(Regex("[:.\\-]"), "")
        return ByteArray(6) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Builds the 102-byte magic packet payload for [mac].
     * @throws IllegalArgumentException if the address is not a valid MAC.
     */
    fun build(mac: String): ByteArray {
        val macBytes = parseMac(mac)
        val packet = ByteArray(PACKET_SIZE)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (rep in 0 until 16) {
            macBytes.copyInto(packet, destinationOffset = 6 + rep * 6)
        }
        return packet
    }
}
