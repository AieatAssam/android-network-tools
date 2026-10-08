package net.aieat.netswissknife.app.platform

import net.aieat.netswissknife.core.network.operation.OperationRequirement

/** What the operation will actually connect to, after any name has been resolved. */
enum class OperationTargetClass {
    LOCAL_ADDRESS,
    INTERNET_ADDRESS,
    NAME_LOOKUP,
    UNKNOWN,
}

/** The network destination used to resolve a name, not the name being queried. */
enum class ResolverClass {
    LOCAL_NETWORK,
    INTERNET,
    SYSTEM_DEFAULT,
    NONE,
}

data class OperationTarget(
    val targetClass: OperationTargetClass,
    val resolverClass: ResolverClass = ResolverClass.NONE,
)

enum class AvailabilityReason {
    NO_ROUTE,
    TARGET_ROUTE_UNKNOWN,
    LOCAL_NETWORK_PERMISSION_DENIED,
    POLICY_DENIED,
    CONNECTIVITY_UNVALIDATED,
}

/**
 * A denial means the platform has definitive evidence that the attempt cannot start.
 * Advisory results allow one bounded, user-requested attempt whose real result is reported.
 * The local-network permission input is nullable because many operations do not use
 * that permission at all; only an explicit `false` for a local-network operation denies it.
 */
data class OperationAvailability(
    val requirement: OperationRequirement,
    val allowed: Boolean,
    val advisory: Boolean = false,
    val reason: AvailabilityReason? = null,
) {
    val permitsBoundedAttempt: Boolean get() = allowed

    companion object {
        fun requirementFor(target: OperationTarget): OperationRequirement = when (target.targetClass) {
            OperationTargetClass.LOCAL_ADDRESS -> OperationRequirement.LOCAL_NETWORK
            OperationTargetClass.INTERNET_ADDRESS -> OperationRequirement.INTERNET
            OperationTargetClass.NAME_LOOKUP,
            OperationTargetClass.UNKNOWN -> when (target.resolverClass) {
                ResolverClass.LOCAL_NETWORK -> OperationRequirement.LOCAL_NETWORK
                ResolverClass.INTERNET -> OperationRequirement.INTERNET
                ResolverClass.SYSTEM_DEFAULT, ResolverClass.NONE -> OperationRequirement.ANY_NETWORK
            }
        }

        /**
         * `routePresent` is tri-state. `false` is definitive evidence that there is no
         * network route at all; `true` means a target-specific route was observed; `null`
         * means the current platform seam only reports connectivity, not whether this
         * destination is reachable. Unknown routes permit one bounded attempt and remain
         * advisory. `hasInternet` is advertised capability; `hasValidatedInternet` is
         * Android's validation signal.
         */
        fun classify(
            target: OperationTarget,
            status: NetworkStatus,
            routePresent: Boolean?,
            localNetworkPermissionAllowed: Boolean? = null,
            policyAllowed: Boolean = true,
        ): OperationAvailability {
            val requirement = requirementFor(target)
            fun denied(reason: AvailabilityReason) = OperationAvailability(
                requirement = requirement,
                allowed = false,
                reason = reason,
            )

            if (!policyAllowed) return denied(AvailabilityReason.POLICY_DENIED)
            if (requirement == OperationRequirement.LOCAL_NETWORK && localNetworkPermissionAllowed == false) {
                return denied(AvailabilityReason.LOCAL_NETWORK_PERMISSION_DENIED)
            }
            if (routePresent == false) return denied(AvailabilityReason.NO_ROUTE)

            val validated = when (requirement) {
                OperationRequirement.LOCAL_NETWORK -> status.hasLocalNetwork
                OperationRequirement.INTERNET -> status.hasValidatedInternet
                // ANY_NETWORK includes unknown/system-resolver destinations. A local
                // route alone cannot validate an arbitrary destination; permit the
                // bounded attempt with an advisory until Internet is validated.
                OperationRequirement.ANY_NETWORK -> status.hasValidatedInternet
            }
            return if (validated && routePresent == true) {
                OperationAvailability(requirement = requirement, allowed = true)
            } else {
                OperationAvailability(
                    requirement = requirement,
                    allowed = true,
                    advisory = true,
                    reason = if (!validated) {
                        AvailabilityReason.CONNECTIVITY_UNVALIDATED
                    } else {
                        AvailabilityReason.TARGET_ROUTE_UNKNOWN
                    },
                )
            }
        }

        /**
         * Uses only the aggregate connectivity signals currently exposed by
         * [NetworkStatus]. A live network proves a route exists in general, not that it
         * reaches this target, so target-route presence remains unknown. A fully empty
         * status is the only definitive no-route signal this seam can provide.
         */
        fun classifyObserved(
            target: OperationTarget,
            status: NetworkStatus,
            localNetworkPermissionAllowed: Boolean? = null,
            policyAllowed: Boolean = true,
        ): OperationAvailability {
            val requirement = requirementFor(target)
            val hasRouteClassEvidence = when (requirement) {
                OperationRequirement.LOCAL_NETWORK -> status.hasLocalNetwork || status.vpnActive
                OperationRequirement.INTERNET -> status.hasInternet || status.vpnActive
                OperationRequirement.ANY_NETWORK -> status.hasInternet || status.hasLocalNetwork ||
                    status.vpnActive || status.transport != null
            }
            return classify(
                target = target,
                status = status,
                routePresent = if (hasRouteClassEvidence) null else false,
                localNetworkPermissionAllowed = localNetworkPermissionAllowed,
                policyAllowed = policyAllowed,
            )
        }
    }
}

fun OperationAvailability.denialMessage(): String = when (reason) {
    AvailabilityReason.NO_ROUTE -> "No network connection"
    AvailabilityReason.LOCAL_NETWORK_PERMISSION_DENIED -> "Local network permission denied"
    AvailabilityReason.POLICY_DENIED -> "Network operation is unavailable"
    AvailabilityReason.CONNECTIVITY_UNVALIDATED,
    AvailabilityReason.TARGET_ROUTE_UNKNOWN,
    null -> "Network operation is unavailable"
}
