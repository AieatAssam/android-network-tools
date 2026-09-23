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
        /**
         * Classifies Android's bracketed ScanResult.capabilities value. Missing or
         * unfamiliar capabilities must stay UNKNOWN: only an explicit ESS-only
         * result is enough evidence to call a network open.
         */
        fun fromCapabilities(capabilities: String?): WifiSecurity {
            val tokens = capabilities
                ?.uppercase()
                ?.split(Regex("[\\[\\],\\s]+"))
                ?.filter(String::isNotBlank)
                .orEmpty()
            if (tokens.isEmpty()) return UNKNOWN

            val hasEss = "ESS" in tokens
            val hasLegacyWpa = tokens.any { it == "WPA" || it.startsWith("WPA-") }
            val hasWpa2 = tokens.any { it == "WPA2" || it.startsWith("WPA2-") }
            val hasWpa3 = tokens.any { it == "WPA3" || it.startsWith("WPA3-") }
            val markers = authMarkers(tokens)
            val hasEap = "EAP" in markers
            val hasSae = "SAE" in markers
            val hasPsk = "PSK" in markers
            val hasOwe = "OWE" in markers || "OWE_TRANSITION" in markers
            val hasWep = "WEP" in markers
            val hasSuiteB192 = "SUITE_B_192" in markers

            return when {
                hasSuiteB192 -> WPA3_ENTERPRISE_192
                hasEap && hasSae && (hasWpa3 || tokens.any { it.startsWith("RSN-") }) -> WPA3_ENTERPRISE
                hasEap && hasWpa3 -> WPA3_ENTERPRISE
                hasEap && (hasWpa2 || tokens.any { it.startsWith("RSN-") }) -> WPA2_ENTERPRISE
                hasSae && hasPsk -> WPA2_WPA3
                hasSae -> WPA3
                hasOwe -> OWE
                hasPsk && hasWpa2 && hasLegacyWpa -> WPA_WPA2
                hasPsk && (hasWpa2 || tokens.any { it.startsWith("RSN-") }) -> WPA2
                hasPsk && hasWpa3 -> WPA3
                hasPsk && hasLegacyWpa -> WPA
                hasWep -> WEP
                hasPsk -> WPA2
                hasEap -> WPA2_ENTERPRISE
                hasEss && tokens.all { it == "ESS" } -> OPEN
                else -> UNKNOWN
            }
        }

        /** Only auth markers we understand can turn protocol-shaped future tokens into a type. */
        private fun authMarkers(tokens: List<String>): Set<String> = tokens.flatMap { token ->
            when {
                token == "OWE" || token == "OWE_TRANSITION" -> listOf("OWE")
                token == "WEP" -> listOf("WEP")
                else -> {
                    val authPart = when {
                        token.startsWith("RSN-") -> token.removePrefix("RSN-")
                        token.startsWith("WPA3-") -> token.removePrefix("WPA3-")
                        token.startsWith("WPA2-") -> token.removePrefix("WPA2-")
                        token.startsWith("WPA-") -> token.removePrefix("WPA-")
                        else -> ""
                    }
                    parseQualifiedAuthMarkers(authPart)
                }
            }
        }.toSet()

        /**
         * Auth markers need a WPA/RSN protocol prefix. If a combined `+` segment
         * contains any unknown marker, discard the whole token instead of guessing.
         * Unknown components after a recognized auth segment are treated as cipher
         * metadata and stop auth parsing (for example, CCMP or GCMP-256).
         */
        private fun parseQualifiedAuthMarkers(authPart: String): List<String> {
            if (authPart.isEmpty()) return emptyList()
            val markers = mutableListOf<String>()
            for (component in authPart.split("-")) {
                if (component.contains('+')) {
                    val combined = component.split('+').map(::recognizedAuthMarker)
                    if (combined.any { it == null }) return emptyList()
                    markers += combined.filterNotNull()
                    continue
                }
                val marker = recognizedAuthMarker(component) ?: break
                markers += marker
            }
            return markers
        }

        private fun recognizedAuthMarker(component: String): String? = when {
            component == "PSK" -> "PSK"
            component == "SAE" -> "SAE"
            component == "OWE" || component == "OWE_TRANSITION" -> "OWE"
            component == "WEP" -> "WEP"
            component in setOf("SUITE_B_192", "EAP_SUITE_B_192", "SUITE-B-192") -> "SUITE_B_192"
            component == "EAP" -> "EAP"
            else -> null
        }

    }
}
