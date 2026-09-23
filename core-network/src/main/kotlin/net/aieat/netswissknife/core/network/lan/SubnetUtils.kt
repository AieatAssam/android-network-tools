package net.aieat.netswissknife.core.network.lan

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utilities for IPv4 subnet manipulation and device-network detection.
 * Pure JVM – no Android SDK imports.
 */
object SubnetUtils {

    internal data class InterfaceAddressCandidate(
        val address: String,
        val prefixLength: Int,
        val interfaceName: String,
        val interfaceIsLoopback: Boolean,
        val interfaceIsUp: Boolean,
        val interfaceIsVirtual: Boolean,
        val addressIsLoopback: Boolean,
    )

    /**
     * Attempts to detect the connected subnet from active network interfaces.
     * Returns CIDR notation like "192.168.1.0/24", or null if unavailable.
     */
    fun getCurrentSubnet(): String? = getCurrentSubnet(::platformInterfaceAddressCandidates)

    /** Injectable interface-address source used to verify detection and prefix forwarding. */
    internal fun getCurrentSubnet(
        interfaceAddressesProvider: () -> Sequence<InterfaceAddressCandidate>,
    ): String? = try {
        interfaceAddressesProvider()
            .filter { !it.interfaceIsLoopback && it.interfaceIsUp && !it.interfaceIsVirtual }
            .filter { candidate ->
                val name = candidate.interfaceName.lowercase()
                !name.contains("dummy") && !name.contains("tun") && !name.contains("p2p")
            }
            .filter { !it.addressIsLoopback }
            .mapNotNull { candidate -> cidrOf(candidate.address, candidate.prefixLength) }
            .firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun platformInterfaceAddressCandidates(): Sequence<InterfaceAddressCandidate> = sequence {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@sequence
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            val eligible = runCatching {
                val name = iface.displayName.lowercase()
                !iface.isLoopback && iface.isUp && !iface.isVirtual &&
                    !name.contains("dummy") && !name.contains("tun") && !name.contains("p2p")
            }
                .getOrDefault(false)
            if (!eligible) continue

            val addresses = runCatching { iface.interfaceAddresses }.getOrNull() ?: continue
            for (ifaceAddr in addresses) {
                val address = ifaceAddr.address as? Inet4Address ?: continue
                yield(
                    InterfaceAddressCandidate(
                        address = address.hostAddress,
                        prefixLength = ifaceAddr.networkPrefixLength.toInt(),
                        interfaceName = iface.displayName,
                        interfaceIsLoopback = false,
                        interfaceIsUp = true,
                        interfaceIsVirtual = false,
                        addressIsLoopback = address.isLoopbackAddress,
                    ),
                )
            }
        }
    }

    /** Normalizes an interface address without changing its platform-provided prefix. */
    internal fun cidrOf(address: String, prefixLength: Int): String? {
        if (prefixLength !in 0..32) return null
        return try {
            val ip = parseIpToLong(address)
            "${longToIp(ip and maskForPrefix(prefixLength))}/$prefixLength"
        } catch (_: IllegalArgumentException) {
            null
        }
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

    /** Returns whether [ip] belongs to the IPv4 network represented by [cidr]. */
    fun contains(cidr: String, ip: String): Boolean {
        return try {
            val parts = cidr.trim().split("/")
            if (parts.size != 2) return false
            val prefix = parts[1].toIntOrNull() ?: return false
            if (prefix !in 0..32) return false
            val mask = maskForPrefix(prefix)
            (parseIpToLong(parts[0]) and mask) == (parseIpToLong(ip) and mask)
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

    internal fun parseIpToLong(ip: String): Long {
        val parts = ip.split(".")
        require(parts.size == 4) { "Invalid IP address: $ip" }
        return parts.fold(0L) { acc, part ->
            val octet = part.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid IP address: $ip")
            require(octet in 0..255) { "Invalid IP address octet $octet in: $ip" }
            (acc shl 8) or octet
        }
    }

    private fun longToIp(ip: Long): String =
        "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"

    private fun maskForPrefix(prefix: Int): Long =
        if (prefix == 0) 0L
        else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
}
