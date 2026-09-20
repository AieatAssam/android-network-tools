package net.aieat.netswissknife.core.network.topology

import java.io.IOException

/**
 * A request-level SNMP failure with enough context to explain what the user
 * needs to check. SNMP over UDP does not distinguish an unreachable agent from
 * a rejected v1/v2c community when no response is returned, so the message
 * deliberately calls out both possibilities.
 */
class SnmpRequestException(
    val target: SnmpTarget,
    val operation: String,
    val oid: String?,
    reason: String,
    cause: Throwable? = null
) : IOException(
    buildString {
        append("SNMP ")
        append(target.params.snmpVersion.label)
        append(' ')
        append(operation)
        oid?.let {
            append(' ')
            append(it)
        }
        append(" to ")
        append(target.ip)
        append(':')
        append(target.port)
        append(" failed: ")
        append(reason)
    },
    cause
) {
    companion object {
        fun noResponse(
            target: SnmpTarget,
            operation: String,
            oid: String?,
            timeoutMs: Int,
            cause: Throwable? = null
        ): SnmpRequestException {
            val hint = when (target.params.snmpVersion) {
                SnmpVersion.V1, SnmpVersion.V2C ->
                    "no response within ${timeoutMs} ms; verify the host, UDP port, firewall, and community string"
                SnmpVersion.V3 ->
                    "no response within ${timeoutMs} ms; verify the host, UDP port, firewall, username, and SNMPv3 security settings"
            }
            return SnmpRequestException(target, operation, oid, hint, cause)
        }

        fun unresolved(target: SnmpTarget, cause: Throwable): SnmpRequestException =
            SnmpRequestException(
                target = target,
                operation = "transport",
                oid = null,
                reason = "could not resolve the target hostname: ${cause.message ?: cause::class.simpleName}",
                cause = cause
            )

        fun responseError(
            target: SnmpTarget,
            operation: String,
            oid: String?,
            status: String,
            index: Int
        ): SnmpRequestException =
            SnmpRequestException(
                target,
                operation,
                oid,
                "agent returned SNMP error '$status'" + if (index > 0) " at variable $index" else ""
            )
    }
}

object SnmpErrorFormatter {
    fun describe(error: Throwable): String = when (error) {
        is SnmpRequestException -> error.message ?: "SNMP request failed"
        else -> {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName
            "SNMP request failed: $detail"
        }
    }
}

private val SnmpVersion.label: String
    get() = when (this) {
        SnmpVersion.V1 -> "v1"
        SnmpVersion.V2C -> "v2c"
        SnmpVersion.V3 -> "v3"
    }
