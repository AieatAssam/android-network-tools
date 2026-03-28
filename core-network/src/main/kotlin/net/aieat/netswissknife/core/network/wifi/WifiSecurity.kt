package net.aieat.netswissknife.core.network.wifi

enum class WifiSecurity(val displayName: String, val isEncrypted: Boolean) {
    OPEN("Open", false),
    OWE("Enhanced Open (OWE)", true),
    WEP("WEP", true),
    WPA("WPA", true),
    WPA2("WPA2", true),
    WPA3("WPA3", true),
    WPA2_WPA3("WPA2/WPA3", true),
    WPA_WPA2("WPA/WPA2", true),
    UNKNOWN("Unknown", false);

    companion object {
        fun fromCapabilities(capabilities: String): WifiSecurity {
            val caps = capabilities.uppercase()
            return when {
                caps.contains("SAE") && caps.contains("PSK") -> WPA2_WPA3
                caps.contains("SAE")                          -> WPA3
                caps.contains("OWE")                          -> OWE
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
