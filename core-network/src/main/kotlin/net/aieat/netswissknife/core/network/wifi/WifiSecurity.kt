package net.aieat.netswissknife.core.network.wifi

enum class WifiSecurity(
    val displayName: String,
    val isEncrypted: Boolean,
    val isEnterprise: Boolean = false
) {
    OPEN("Open", false),
    OWE("Enhanced Open (OWE)", true),
    WEP("WEP", true),
    WPA("WPA", true),
    WPA2("WPA2", true),
    WPA3("WPA3", true),
    WPA2_WPA3("WPA2/WPA3", true),
    WPA_WPA2("WPA/WPA2", true),
    WPA2_ENTERPRISE("WPA2-Enterprise", true, isEnterprise = true),
    WPA3_ENTERPRISE("WPA3-Enterprise", true, isEnterprise = true),
    WPA3_ENTERPRISE_192("WPA3-Enterprise 192-bit", true, isEnterprise = true),
    UNKNOWN("Unknown", false);

    companion object {
        fun fromCapabilities(capabilities: String): WifiSecurity {
            val caps = capabilities.uppercase()
            return when {
                caps.contains("SUITE_B_192") || caps.contains("SUITE-B-192") -> WPA3_ENTERPRISE_192
                caps.contains("EAP") && caps.contains("SAE")              -> WPA3_ENTERPRISE
                caps.contains("SAE") && caps.contains("PSK") -> WPA2_WPA3
                caps.contains("SAE")                          -> WPA3
                caps.contains("OWE")                          -> OWE
                caps.contains("EAP") && (caps.contains("WPA2") || caps.contains("RSN")) -> WPA2_ENTERPRISE
                caps.contains("WPA2") && caps.contains("WPA-") -> WPA_WPA2
                caps.contains("WPA2")                         -> WPA2
                caps.contains("WPA")                          -> WPA
                caps.contains("WEP")                          -> WEP
                caps.contains("ESS") || caps.isEmpty()        -> OPEN
                else                                          -> OPEN
            }
        }
    }
}
