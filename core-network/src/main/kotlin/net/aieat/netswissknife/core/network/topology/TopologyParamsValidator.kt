package net.aieat.netswissknife.core.network.topology

import net.aieat.netswissknife.core.network.HostValidator

data class ValidationResult(val isValid: Boolean, val errors: List<String>)

object TopologyParamsValidator {
    private val numericTarget = Regex("^[0-9].*\\..*")

    fun validate(params: TopologyParams): ValidationResult {
        val errors = mutableListOf<String>()

        if (params.targetIp.isBlank()) {
            errors.add("Target IP or hostname must not be blank")
        } else if (!isValidTarget(params.targetIp)) {
            errors.add("Target IP or hostname must be valid")
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
                if (params.v3PrivProtocol != V3PrivProtocol.NONE &&
                    params.v3AuthProtocol == V3AuthProtocol.NONE
                ) {
                    errors.add("SNMP v3 privacy requires an authentication protocol")
                }
                if (params.v3AuthProtocol != V3AuthProtocol.NONE && params.v3AuthPassword.isNullOrBlank()) {
                    errors.add("Authentication password must not be blank when authentication is enabled")
                }
                if (params.v3PrivProtocol != V3PrivProtocol.NONE && params.v3PrivPassword.isNullOrBlank()) {
                    errors.add("Privacy password must not be blank when privacy is enabled")
                }
            }
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    /** SNMP targets may be IPv4 literals or DNS names; reject IPv6 and malformed numeric IPs. */
    private fun isValidTarget(value: String): Boolean {
        val target = value.trim()
        if (target.contains(':')) return false
        if (HostValidator.isValidIpv4(target)) return true
        if (numericTarget.matches(target)) return false
        return HostValidator.isValidHostname(target)
    }
}
