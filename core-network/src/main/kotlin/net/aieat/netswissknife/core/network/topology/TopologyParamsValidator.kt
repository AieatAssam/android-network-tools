package net.aieat.netswissknife.core.network.topology

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

data class ValidationResult(val isValid: Boolean, val errors: List<ErrorInfo>) {
    /** Existing developer-facing copy retained for callers that still render/log validation text. */
    val messages: List<String> get() = errors.map { it.developerMessage ?: it.code.name.lowercase() }
}

object TopologyParamsValidator {
    const val MIN_MAX_HOPS = 1
    const val MAX_MAX_HOPS = 10
    const val MIN_TIMEOUT_MS = 500
    const val MAX_TIMEOUT_MS = 30_000
    const val MIN_RETRIES = 0
    const val MAX_RETRIES = 5

    private val numericDottedTarget = Regex("^\\d+(?:\\.\\d+)+$")
    private val malformedIpv4LikeTarget = Regex("^\\d{1,3}(?:\\.\\d{1,3}){2}\\.[A-Za-z]$")

    fun validate(params: TopologyParams): ValidationResult {
        val errors = mutableListOf<ErrorInfo>()

        if (params.targetIp.isBlank()) {
            errors.add(error(ErrorCode.HOST_BLANK, "Target IP or hostname must not be blank"))
        } else if (!isValidTarget(params.targetIp)) {
            errors.add(error(ErrorCode.HOST_INVALID, "Target IP or hostname must be valid", params.targetIp))
        }

        if (params.maxHops !in MIN_MAX_HOPS..MAX_MAX_HOPS) {
            errors.add(error(
                ErrorCode.MAX_HOPS_OUT_OF_RANGE,
                "Max hops must be between $MIN_MAX_HOPS and $MAX_MAX_HOPS",
                MIN_MAX_HOPS,
                MAX_MAX_HOPS,
            ))
        }
        if (params.timeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            errors.add(error(
                ErrorCode.TIMEOUT_OUT_OF_RANGE,
                "Timeout must be between $MIN_TIMEOUT_MS ms and $MAX_TIMEOUT_MS ms",
                MIN_TIMEOUT_MS,
                MAX_TIMEOUT_MS,
            ))
        }
        if (params.retries !in MIN_RETRIES..MAX_RETRIES) {
            errors.add(error(
                ErrorCode.RETRIES_OUT_OF_RANGE,
                "Retries must be between $MIN_RETRIES and $MAX_RETRIES",
                MIN_RETRIES,
                MAX_RETRIES,
            ))
        }
        if (params.maxHops in MIN_MAX_HOPS..MAX_MAX_HOPS &&
            params.timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS &&
            params.retries in MIN_RETRIES..MAX_RETRIES &&
            TopologyOperationBudget.timeoutMillisOrNull(params) == null
        ) {
            errors.add(error(ErrorCode.OPERATION_DEADLINE_EXCEEDED, TopologyOperationBudget.OVER_CEILING_MESSAGE))
        }

        when (params.snmpVersion) {
            SnmpVersion.V1, SnmpVersion.V2C -> {
                if (params.communityString.isBlank()) {
                    errors.add(error(ErrorCode.SNMP_COMMUNITY_BLANK, "Community string must not be blank for SNMP v1/v2c"))
                }
            }
            SnmpVersion.V3 -> {
                if (params.v3Username.isNullOrBlank()) {
                    errors.add(error(ErrorCode.SNMP_V3_USER_BLANK, "Username must not be blank for SNMP v3"))
                }
                if (params.v3PrivProtocol != V3PrivProtocol.NONE &&
                    params.v3AuthProtocol == V3AuthProtocol.NONE
                ) {
                    errors.add(error(ErrorCode.SNMP_V3_PRIV_WITHOUT_AUTH, "SNMP v3 privacy requires an authentication protocol"))
                }
                if (params.v3AuthProtocol != V3AuthProtocol.NONE && params.v3AuthPassword.isNullOrBlank()) {
                    errors.add(error(ErrorCode.SNMP_V3_AUTH_PASSWORD_BLANK, "Authentication password must not be blank when authentication is enabled"))
                }
                if (params.v3PrivProtocol != V3PrivProtocol.NONE && params.v3PrivPassword.isNullOrBlank()) {
                    errors.add(error(ErrorCode.SNMP_V3_PRIV_PASSWORD_BLANK, "Privacy password must not be blank when privacy is enabled"))
                }
            }
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    private fun error(code: ErrorCode, message: String, vararg args: Any?): ErrorInfo =
        ErrorInfo(code = code, args = args.toList(), developerMessage = message)

    /** SNMP targets may be IPv4 literals or DNS names; reject IPv6 and malformed numeric IPs. */
    private fun isValidTarget(value: String): Boolean {
        val target = value.trim()
        if (target.contains(':')) return false
        if (HostValidator.isValidIpv4(target)) return true
        if (numericDottedTarget.matches(target)) return false
        if (malformedIpv4LikeTarget.matches(target)) return false
        return HostValidator.isValidHostname(target)
    }
}
