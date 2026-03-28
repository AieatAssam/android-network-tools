package net.aieat.netswissknife.core.network.lan

/**
 * Minimal static OUI (Organizationally Unique Identifier) lookup database.
 * Maps the first three MAC octets (uppercase, colon-separated) to a vendor name.
 */
object OuiDatabase {

    private val oui: Map<String, String> = mapOf(
        // Apple
        "00:17:F2" to "Apple",
        "00:1A:11" to "Apple",
        "8C:85:90" to "Apple",
        "AC:BC:32" to "Apple",
        "BC:54:51" to "Apple",
        "D8:3A:DD" to "Apple",
        "F0:18:98" to "Apple",
        "18:65:90" to "Apple",
        "3C:5A:B4" to "Google",
        // Google / Nest
        "1A:11:FB" to "Google",
        "34:60:F9" to "Google/Nest",
        "54:60:09" to "Google/Nest",
        "18:B4:30" to "Nest Labs",
        // Amazon / Echo
        "40:B4:CD" to "Amazon Echo",
        "44:65:0D" to "Amazon",
        "68:37:E9" to "Amazon",
        "7C:B2:7D" to "Amazon",
        "A0:02:DC" to "Amazon",
        "B4:7C:9C" to "Amazon",
        "FC:65:DE" to "Amazon",
        // Raspberry Pi
        "B8:27:EB" to "Raspberry Pi Foundation",
        "DC:A6:32" to "Raspberry Pi",
        "28:CD:C1" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        // VMware
        "00:0C:29" to "VMware",
        "00:50:56" to "VMware",
        // Cisco
        "00:18:E7" to "Cisco",
        "00:1B:2B" to "Cisco",
        "00:1F:CA" to "Cisco",
        "00:23:69" to "Cisco",
        "00:50:43" to "Cisco",
        "E8:40:F2" to "Cisco",
        "FC:FB:FB" to "Cisco",
        // Cisco-Linksys
        "00:14:BF" to "Cisco-Linksys",
        "00:1D:7E" to "Cisco-Linksys",
        "20:AA:4B" to "Cisco-Linksys",
        // NETGEAR
        "00:26:18" to "NETGEAR",
        "1E:2A:00" to "NETGEAR",
        "20:4E:7F" to "NETGEAR",
        "A0:21:B7" to "NETGEAR",
        "C0:FF:D4" to "NETGEAR",
        // TP-Link
        "18:D6:C7" to "TP-Link",
        "50:FA:84" to "TP-Link",
        "58:6D:8F" to "TP-Link",
        "A0:F3:C1" to "TP-Link",
        "B0:95:75" to "TP-Link",
        "C0:4A:00" to "TP-Link",
        "EC:08:6B" to "TP-Link",
        // Asus
        "00:17:D5" to "Asus",
        "00:1A:92" to "Asus",
        "04:92:26" to "Asus",
        "74:D0:2B" to "Asus",
        "AC:9E:17" to "Asus",
        "E0:3F:49" to "Asus",
        // Belkin
        "00:17:3F" to "Belkin",
        "30:8C:FB" to "Belkin",
        "54:75:D0" to "Belkin",
        "94:10:3E" to "Belkin",
        // Samsung
        "00:0C:E7" to "Samsung",
        "18:67:B0" to "Samsung",
        "50:CC:F8" to "Samsung",
        "78:D6:F0" to "Samsung",
        "A0:07:98" to "Samsung",
        "FC:F1:36" to "Samsung",
        // Dell
        "00:14:22" to "Dell",
        "00:1C:23" to "Dell",
        "00:1E:64" to "Dell",
        "14:18:77" to "Dell",
        "F8:DB:88" to "Dell",
        // HP
        "00:21:5A" to "HP",
        "00:26:55" to "HP",
        "3C:D9:2B" to "HP",
        "FC:15:B4" to "HP",
        // Intel
        "00:0F:F7" to "Intel",
        "00:19:D1" to "Intel",
        "00:23:14" to "Intel",
        "54:27:1E" to "Intel",
        "AC:FD:CE" to "Intel",
        // Synology
        "00:11:32" to "Synology",
        "00:24:1D" to "Synology",
        // Xiaomi
        "28:6C:07" to "Xiaomi",
        "64:16:66" to "Xiaomi",
        // Huawei
        "00:18:82" to "Huawei",
        "00:46:4B" to "Huawei",
        "04:C0:6F" to "Huawei",
        "70:7B:E8" to "Huawei",
        "AC:E8:7B" to "Huawei",
        "CC:34:29" to "Huawei",
        // Realtek
        "00:E0:4C" to "Realtek",
        // Philips Hue
        "00:17:88" to "Philips Hue",
        "EC:B5:FA" to "Philips Hue",
    )

    /**
     * Looks up the vendor for the given [macAddress].
     * The [macAddress] must be uppercase colon-separated (e.g. "AA:BB:CC:DD:EE:FF").
     * Returns null if the OUI is unknown.
     */
    fun lookup(macAddress: String): String? {
        if (macAddress.length < 8) return null
        // Normalise to uppercase and take first 8 chars: "XX:XX:XX"
        val prefix = macAddress.uppercase().take(8)
        return oui[prefix]
    }
}
