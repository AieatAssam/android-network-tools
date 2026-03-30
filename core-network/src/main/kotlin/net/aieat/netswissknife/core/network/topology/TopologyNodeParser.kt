package net.aieat.netswissknife.core.network.topology

object TopologyNodeParser {

    private val VENDOR_KEYWORDS = listOf(
        "Cisco", "Juniper", "Aruba", "MikroTik", "TP-Link", "TP-LINK",
        "Netgear", "D-Link", "HPE", "HP", "Ubiquiti", "Ruckus", "Extreme",
        "Brocade", "Alcatel", "Huawei", "ZTE", "Palo Alto", "Fortinet",
        "SonicWall", "WatchGuard"
    )

    fun parseVendor(sysDescr: String): String? {
        if (sysDescr.isBlank()) return null
        for (keyword in VENDOR_KEYWORDS) {
            if (sysDescr.contains(keyword, ignoreCase = true)) {
                return when {
                    keyword.equals("TP-LINK", ignoreCase = true) -> "TP-Link"
                    else -> keyword
                }
            }
        }
        return null
    }

    fun parseModel(sysDescr: String?, cdpPlatform: String?): String? {
        if (!cdpPlatform.isNullOrBlank()) return cdpPlatform.trim()
        if (sysDescr.isNullOrBlank()) return null
        // Try to extract model from common patterns: "Cisco <Model>", "Juniper <model>"
        val catalystMatch = Regex("""Catalyst\s+(\S+)""").find(sysDescr)
        if (catalystMatch != null) return "Catalyst ${catalystMatch.groupValues[1]}"
        return null
    }

    fun parseFirmwareVersion(sysDescr: String?, cdpVersion: String?): String? {
        if (!cdpVersion.isNullOrBlank()) return cdpVersion.trim()
        if (sysDescr.isNullOrBlank()) return null
        // Try common patterns: "Version X.Y.Z", "JUNOS X.Y"
        val versionMatch = Regex("""[Vv]ersion\s+(\S+)""").find(sysDescr)
        return versionMatch?.groupValues?.get(1)
    }

    fun timeticksToHuman(timeticks: Long): String {
        val totalSeconds = timeticks / 100
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (days > 0) "${days}d ${hours}h ${minutes}m ${seconds}s"
        else "${hours}h ${minutes}m ${seconds}s"
    }

    fun ifHighSpeedToSpeedBps(ifHighSpeedMbps: Long): Long {
        return ifHighSpeedMbps * 1_000_000L
    }

    fun parseMacAddress(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return bytes.joinToString(":") { "%02x".format(it) }
    }

    fun parseCapabilities(cdpCapabilities: String?): Set<DeviceCapability> {
        if (cdpCapabilities.isNullOrBlank()) return emptySet()
        val caps = mutableSetOf<DeviceCapability>()
        val value = cdpCapabilities.toLongOrNull() ?: return emptySet()
        if (value and 0x01L != 0L) caps.add(DeviceCapability.OTHER)    // repeater
        if (value and 0x02L != 0L) caps.add(DeviceCapability.OTHER)    // transparent bridge
        if (value and 0x04L != 0L) caps.add(DeviceCapability.SWITCH)   // source route bridge
        if (value and 0x08L != 0L) caps.add(DeviceCapability.SWITCH)   // switch
        if (value and 0x10L != 0L) caps.add(DeviceCapability.ROUTER)   // router
        if (value and 0x20L != 0L) caps.add(DeviceCapability.PHONE)    // phone
        if (value and 0x40L != 0L) caps.add(DeviceCapability.OTHER)    // DOCSIS cable device
        if (value and 0x80L != 0L) caps.add(DeviceCapability.AP)       // station only
        return caps.ifEmpty { setOf(DeviceCapability.OTHER) }
    }
}
