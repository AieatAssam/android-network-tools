package net.aieat.netswissknife.core.network.topology

/**
 * Pure parsers for the LLDP-MIB and CISCO-CDP-MIB table encodings returned by
 * SNMP walks. In particular, LLDP management addresses are row-index data,
 * not the Integer32 value of lldpRemManAddrIfId.
 */
object TopologyMibParser {

    private const val LLDP_REM_PREFIX = "1.0.8802.1.1.2.1.4.1.1."
    private const val LLDP_MAN_ADDR_PREFIX = "1.0.8802.1.1.2.1.4.2.1.4."
    private const val CDP_CACHE_PREFIX = "1.3.6.1.4.1.9.9.23.1.2.1.1."

    fun parseLldpRemTable(walk: Map<String, String>): List<LldpRemEntry> {
        val grouped = linkedMapOf<String, MutableMap<Int, String>>()
        val indexes = mutableMapOf<String, Pair<String, String>>()

        walk.forEach { (oid, value) ->
            val suffix = oid.normalized().removePrefix(LLDP_REM_PREFIX)
            if (suffix == oid.normalized()) return@forEach
            val parts = suffix.split('.')
            if (parts.size < 4) return@forEach

            val column = parts[0].toIntOrNull() ?: return@forEach
            val localPort = parts[2]
            val remIndex = parts[3]
            val key = "$localPort.$remIndex"
            grouped.getOrPut(key) { mutableMapOf() }[column] = value
            indexes[key] = localPort to remIndex
        }

        return grouped.map { (key, columns) ->
            val (localPort, remIndex) = indexes.getValue(key)
            LldpRemEntry(
                key = key,
                localPort = localPort,
                remIndex = remIndex,
                chassisId = columns[5],
                portId = columns[7],
                portDescription = columns[8],
                sysName = columns[9]
            )
        }
    }

    /**
     * The returned object behaves as the IPv4 map for source compatibility and
     * also exposes IPv6 rows separately for callers that want to retain them.
     */
    fun parseLldpManAddrTable(walk: Map<String, String>): LldpManagementAddresses {
        val ipv4 = linkedMapOf<String, String>()
        val ipv6 = linkedMapOf<String, String>()

        walk.keys.forEach { oid ->
            val suffix = oid.normalized().removePrefix(LLDP_MAN_ADDR_PREFIX)
            if (suffix == oid.normalized()) return@forEach
            val parts = suffix.split('.')
            // timeMark.localPort.remIndex.addrSubtype.addrLen.addrOctets...
            if (parts.size < 6) return@forEach

            val localPort = parts[1]
            val remIndex = parts[2]
            val addressType = parts[3].toIntOrNull() ?: return@forEach
            val addressLength = parts[4].toIntOrNull() ?: return@forEach
            val octets = parts.drop(5).mapNotNull { it.toIntOrNull() }
            if (octets.size != addressLength) return@forEach

            val key = "$localPort.$remIndex"
            when {
                addressType == 1 && addressLength == 4 && octets.all { it in 0..255 } -> {
                    ipv4[key] = octets.joinToString(".")
                }
                addressType == 2 && addressLength == 16 && octets.all { it in 0..255 } -> {
                    ipv6[key] = formatIpv6(octets)
                }
            }
        }

        return LldpManagementAddresses(ipv4 = ipv4, ipv6 = ipv6)
    }

    fun parseCdpCache(walk: Map<String, String>): List<CdpEntry> {
        val grouped = linkedMapOf<String, MutableMap<Int, String>>()

        walk.forEach { (oid, value) ->
            val suffix = oid.normalized().removePrefix(CDP_CACHE_PREFIX)
            if (suffix == oid.normalized()) return@forEach
            val parts = suffix.split('.')
            if (parts.size < 3) return@forEach

            val column = parts[0].toIntOrNull() ?: return@forEach
            val ifIndex = parts[1]
            val neighbourIndex = parts[2]
            grouped.getOrPut("$ifIndex.$neighbourIndex") { mutableMapOf() }[column] = value
        }

        return grouped.mapNotNull { (key, columns) ->
            // cdpCacheAddressType: 1 = ipAddress. Unknown/missing types are
            // retained for compatibility with agents that omit this column.
            if (columns[3]?.toIntOrNull()?.let { it != 1 } == true) return@mapNotNull null
            val address = columns[4]?.let(::hexOctetsToIpv4) ?: return@mapNotNull null
            CdpEntry(
                key = key,
                address = address,
                deviceId = columns[6],
                port = columns[7],
                platform = columns[8],
                capabilities = columns[9]
            )
        }
    }

    fun hexOctetsToIpv4(raw: String): String? {
        val value = raw.trim()
        val dotted = value.split('.')
        if (dotted.size == 4) {
            val octets = dotted.map { it.toIntOrNull() ?: return null }
            return octets.takeIf { it.all { octet -> octet in 0..255 } }?.joinToString(".")
        }

        val hex = value.replace(" ", "").split(':')
        if (hex.size != 4) return null
        val octets = hex.map { part ->
            if (part.length !in 1..2) return null
            part.toIntOrNull(16) ?: return null
        }
        return octets.takeIf { it.all { octet -> octet in 0..255 } }?.joinToString(".")
    }

    private fun formatIpv6(octets: List<Int>): String =
        octets.chunked(2).joinToString(":") { pair ->
            pair.joinToString("") { octet -> octet.toString(16).padStart(2, '0') }
        }

    private fun String.normalized(): String = trim().trimStart('.')
}

data class LldpRemEntry(
    val key: String,
    val localPort: String,
    val remIndex: String,
    val chassisId: String?,
    val portId: String?,
    val portDescription: String?,
    val sysName: String?
)

data class LldpManagementAddresses(
    val ipv4: Map<String, String>,
    val ipv6: Map<String, String>
) : Map<String, String> by ipv4

data class CdpEntry(
    val key: String,
    val address: String,
    val deviceId: String?,
    val port: String?,
    val platform: String?,
    val capabilities: String?
)
