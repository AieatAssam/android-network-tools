package net.aieat.netswissknife.core.network.topology

data class ValidationResult(val isValid: Boolean, val errors: List<String>)

object TopologyParamsValidator {
    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun validate(params: TopologyParams): ValidationResult {
        val errors = mutableListOf<String>()

        if (params.targetIp.isBlank()) {
            errors.add("Target IP must not be blank")
        } else {
            val match = IPV4_REGEX.matchEntire(params.targetIp.trim())
            if (match == null) {
                errors.add("Target IP must be a valid IPv4 address")
            } else {
                val octets = match.groupValues.drop(1).map { it.toInt() }
                if (octets.any { it > 255 }) {
                    errors.add("Target IP must be a valid IPv4 address")
                }
            }
        }

        when (params.snmpVersion) {
            SnmpVersion.V1, SnmpVersion.V2C -> {
                if (params.communityString.isBlank()) {
                    errors.add("Community string must not be blank for SNMP v1/v2c")
                }
            }
            SnmpVersion.V3 -> {
                if (params.v3Username.isNullOrBlank()) {
                    errors.add("Username must not be blank for SNMP v3")
                }
            }
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }
}
