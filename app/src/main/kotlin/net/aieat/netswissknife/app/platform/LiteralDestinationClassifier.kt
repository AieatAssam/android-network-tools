package net.aieat.netswissknife.app.platform

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.traceroute.ReservedRanges

/** Classifies address literals only. Hostnames and malformed inputs are never resolved. */
object LiteralDestinationClassifier {
    fun classify(input: String): OperationTargetClass {
        val value = input.trim()
        val candidate = when {
            value.startsWith('[') && value.endsWith(']') && value.length > 2 ->
                value.substring(1, value.lastIndex)
            value.startsWith('[') || value.endsWith(']') -> return OperationTargetClass.UNKNOWN
            else -> value
        }
        val bracketed = value.startsWith('[') && value.endsWith(']')
        if (bracketed && ':' !in candidate) return OperationTargetClass.UNKNOWN

        if ('%' in candidate) {
            val zoneParts = candidate.split('%')
            if (zoneParts.size != 2 || zoneParts[1].isEmpty() ||
                !zoneParts[1].matches(Regex("[A-Za-z0-9_.-]+")) ||
                !HostValidator.isValidIpv6(candidate)
            ) return OperationTargetClass.UNKNOWN
            // A zone is meaningful for scoped local addresses. Do not infer that a
            // zone-qualified public or reserved address is Internet-routable.
            return if (ReservedRanges.isLocalNetworkLiteral(zoneParts[0])) {
                OperationTargetClass.LOCAL_ADDRESS
            } else {
                OperationTargetClass.UNKNOWN
            }
        }

        val canonicalIpv4 = HostValidator.normalize(candidate)
            ?.takeIf(HostValidator::isValidIpv4)
        val classifiedLiteral = canonicalIpv4 ?: candidate

        return when {
            ReservedRanges.isLocalNetworkLiteral(classifiedLiteral) -> OperationTargetClass.LOCAL_ADDRESS
            ReservedRanges.isPublicGlobalLiteral(classifiedLiteral) -> OperationTargetClass.INTERNET_ADDRESS
            else -> OperationTargetClass.UNKNOWN
        }
    }

    /** Classifies a destination without resolving hostnames; they use the system resolver. */
    fun target(input: String): OperationTarget {
        val targetClass = classify(input)
        return OperationTarget(
            targetClass = targetClass,
            resolverClass = if (targetClass == OperationTargetClass.UNKNOWN) {
                ResolverClass.SYSTEM_DEFAULT
            } else {
                ResolverClass.NONE
            },
        )
    }
}
