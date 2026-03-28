package net.aieat.netswissknife.core.network.lan

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utilities for IPv4 subnet manipulation and device-network detection.
 * Pure JVM – no Android SDK imports.
 */
object SubnetUtils {

    /**
     * Attempts to detect the connected subnet from active network interfaces.
     * Returns CIDR notation like "192.168.1.0/24", or null if unavailable.
     */
    fun getCurrentSubnet(): String? = try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        interfaces
            .asSequence()
            .filter { !it.isLoopback && it.isUp && !it.isVirtual }
            .filter { iface ->
                val name = iface.displayName.lowercase()
                !name.contains("dummy") && !name.contains("tun") && !name.contains("p2p")
            }
            .flatMap { iface -> iface.interfaceAddresses.asSequence().map { iface to it } }
            .firstOrNull { (_, addr) ->
                addr.address is Inet4Address && !addr.address.isLoopbackAddress
            }
            ?.let { (_, ifaceAddr) ->
                val ip = ifaceAddr.address as Inet4Address
                // Enforce a minimum prefix of /16 and maximum of /30
                val prefixLen = ifaceAddr.networkPrefixLength.toInt().coerceIn(16, 30)
                val networkIp = networkAddress(ip, prefixLen)
                "$networkIp/$prefixLen"
            }
    } catch (_: Exception) {
        null
    }

    /**
     * Returns true if [cidr] is a valid IPv4 CIDR string with prefix in [16..30].
     * Prefix ≥ 31 has no usable hosts; prefix < 16 would generate > 65534 IPs.
     */
    fun isValidCidr(cidr: String): Boolean {
        return try {
            val trimmed = cidr.trim()
            val slashIdx = trimmed.indexOf('/')
            if (slashIdx < 0) return false
            val ipPart = trimmed.substring(0, slashIdx)
            val prefixPart = trimmed.substring(slashIdx + 1)
            val prefix = prefixPart.toIntOrNull() ?: return false
            if (prefix !in 16..30) return false
            val octets = ipPart.split(".")
            if (octets.size != 4) return false
            octets.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parses [cidr] (e.g. "192.168.1.0/24") and returns all **host** IPs in the subnet
     * (excludes network address and broadcast address).
     *
     * @throws IllegalArgumentException for invalid or unsupported CIDR.
     */
    fun parseSubnet(cidr: String): List<String> {
        val trimmed = cidr.trim()
        val parts = trimmed.split("/")
        require(parts.size == 2) { "Expected CIDR notation, got: $cidr" }
        val prefix = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid prefix: ${parts[1]}")
        require(prefix in 1..30) { "Prefix must be 1..30, got $prefix" }

        val baseIp = parseIpToLong(parts[0].trim())
        val mask = maskForPrefix(prefix)
        val network = baseIp and mask
        val broadcast = network or (mask xor 0xFFFFFFFFL)

        val hostCount = broadcast - network - 1
        require(hostCount > 0) { "Subnet too small (no usable hosts)" }
        require(hostCount <= 65534) { "Subnet too large – max /16 supported" }

        return (1L until (broadcast - network)).map { offset ->
            longToIp(network + offset)
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun networkAddress(addr: Inet4Address, prefixLen: Int): String {
        val ipLong = parseIpToLong(addr.hostAddress!!)
        val mask = maskForPrefix(prefixLen)
        return longToIp(ipLong and mask)
    }

    internal fun parseIpToLong(ip: String): Long =
        ip.split(".").fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun longToIp(ip: Long): String =
        "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"

    private fun maskForPrefix(prefix: Int): Long =
        if (prefix == 0) 0L
        else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
}
