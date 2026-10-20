package net.aieat.netswissknife.app.platform

import net.aieat.netswissknife.core.network.operation.OperationRequirement
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationAvailabilityTest {
    @Test
    fun `derives requirement from destination and resolver rather than queried name`() {
        val cases =
            listOf(
                CaseTarget(
                    "private literal",
                    OperationTarget(OperationTargetClass.LOCAL_ADDRESS),
                    OperationRequirement.LOCAL_NETWORK,
                ),
                CaseTarget(
                    "public literal",
                    OperationTarget(OperationTargetClass.INTERNET_ADDRESS),
                    OperationRequirement.INTERNET,
                ),
                CaseTarget(
                    "public name queried through LAN DNS",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.LOCAL_NETWORK),
                    OperationRequirement.LOCAL_NETWORK,
                ),
                CaseTarget(
                    "name queried through Internet DNS",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.INTERNET),
                    OperationRequirement.INTERNET,
                ),
                CaseTarget(
                    "unknown destination using system resolver",
                    OperationTarget(OperationTargetClass.UNKNOWN, ResolverClass.SYSTEM_DEFAULT),
                    OperationRequirement.ANY_NETWORK,
                ),
                CaseTarget(
                    "name lookup using system resolver",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.SYSTEM_DEFAULT),
                    OperationRequirement.ANY_NETWORK,
                ),
                CaseTarget(
                    "name lookup with no resolver classification",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.NONE),
                    OperationRequirement.ANY_NETWORK,
                ),
                CaseTarget(
                    "unknown target with no resolver classification",
                    OperationTarget(OperationTargetClass.UNKNOWN, ResolverClass.NONE),
                    OperationRequirement.ANY_NETWORK,
                ),
            )

        assertAll(
            cases.map { case ->
                org.junit.jupiter.api.function.Executable {
                    assertEquals(case.expected, OperationAvailability.requirementFor(case.target), case.label)
                }
            },
        )
    }

    @Test
    fun `classifies validated local and Internet routes as available`() {
        val cases =
            listOf(
                AvailabilityCase(
                    "local Wi-Fi route",
                    OperationTarget(OperationTargetClass.LOCAL_ADDRESS),
                    NetworkStatus(hasLocalNetwork = true, transport = Transport.WIFI),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.LOCAL_NETWORK,
                    allowed = true,
                ),
                AvailabilityCase(
                    "validated cellular Internet",
                    OperationTarget(OperationTargetClass.INTERNET_ADDRESS),
                    NetworkStatus(hasInternet = true, hasValidatedInternet = true, transport = Transport.CELLULAR),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.INTERNET,
                    allowed = true,
                ),
                AvailabilityCase(
                    "VPN route explicitly reaches private target",
                    OperationTarget(OperationTargetClass.LOCAL_ADDRESS),
                    NetworkStatus(vpnActive = true, transport = Transport.VPN),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.LOCAL_NETWORK,
                    allowed = true,
                    advisory = true,
                ),
                AvailabilityCase(
                    "system resolver on validated Internet",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.SYSTEM_DEFAULT),
                    NetworkStatus(hasInternet = true, hasValidatedInternet = true, transport = Transport.WIFI),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.ANY_NETWORK,
                    allowed = true,
                ),
            )

        assertAll(
            cases.map { case ->
                org.junit.jupiter.api.function.Executable {
                    val actual = OperationAvailability.classify(case.target, case.status, case.routePresent)
                    assertEquals(case.expectedRequirement, actual.requirement, case.label)
                    assertEquals(case.allowed, actual.allowed, case.label)
                    assertEquals(case.advisory, actual.advisory, case.label)
                    assertEquals(
                        if (case.advisory) AvailabilityReason.CONNECTIVITY_UNVALIDATED else null,
                        actual.reason,
                        case.label,
                    )
                }
            },
        )
    }

    @Test
    fun `unvalidated connectivity permits bounded attempt with advisory`() {
        val cases =
            listOf(
                AvailabilityCase(
                    "captive portal public target",
                    OperationTarget(OperationTargetClass.INTERNET_ADDRESS),
                    NetworkStatus(hasInternet = true, hasLocalNetwork = true, transport = Transport.WIFI),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.INTERNET,
                    allowed = true,
                    advisory = true,
                ),
                AvailabilityCase(
                    "local DNS for public queried name",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.LOCAL_NETWORK),
                    NetworkStatus(hasLocalNetwork = true, transport = Transport.ETHERNET),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.LOCAL_NETWORK,
                    allowed = true,
                ),
                AvailabilityCase(
                    "local target with only Internet validation",
                    OperationTarget(OperationTargetClass.LOCAL_ADDRESS),
                    NetworkStatus(hasInternet = true, transport = Transport.WIFI),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.LOCAL_NETWORK,
                    allowed = true,
                    advisory = true,
                ),
                AvailabilityCase(
                    "Internet target with only local network validation",
                    OperationTarget(OperationTargetClass.INTERNET_ADDRESS),
                    NetworkStatus(hasLocalNetwork = true, transport = Transport.ETHERNET),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.INTERNET,
                    allowed = true,
                    advisory = true,
                ),
                AvailabilityCase(
                    "Internet requirement over unvalidated but present route",
                    OperationTarget(OperationTargetClass.INTERNET_ADDRESS),
                    NetworkStatus(transport = Transport.VPN, vpnActive = true),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.INTERNET,
                    allowed = true,
                    advisory = true,
                ),
                AvailabilityCase(
                    "system resolver on captive portal Wi-Fi remains advisory",
                    OperationTarget(OperationTargetClass.NAME_LOOKUP, ResolverClass.SYSTEM_DEFAULT),
                    NetworkStatus(hasInternet = true, hasLocalNetwork = true, transport = Transport.WIFI),
                    routePresent = true,
                    expectedRequirement = OperationRequirement.ANY_NETWORK,
                    allowed = true,
                    advisory = true,
                ),
            )

        assertAll(
            cases.map { case ->
                org.junit.jupiter.api.function.Executable {
                    val actual = OperationAvailability.classify(case.target, case.status, case.routePresent)
                    assertEquals(case.expectedRequirement, actual.requirement, case.label)
                    assertTrue(actual.allowed, case.label)
                    assertEquals(case.advisory, actual.advisory, case.label)
                    assertTrue(actual.permitsBoundedAttempt, case.label)
                    assertEquals(
                        if (case.advisory) AvailabilityReason.CONNECTIVITY_UNVALIDATED else null,
                        actual.reason,
                        case.label,
                    )
                }
            },
        )
    }

    @Test
    fun `missing route permission and policy are definitive denials`() {
        val target = OperationTarget(OperationTargetClass.LOCAL_ADDRESS)
        val status = NetworkStatus(hasLocalNetwork = true)
        val cases =
            listOf(
                DenialCase("no route", false, true, true, AvailabilityReason.NO_ROUTE),
                DenialCase(
                    "local network permission denied",
                    true,
                    false,
                    true,
                    AvailabilityReason.LOCAL_NETWORK_PERMISSION_DENIED,
                ),
                DenialCase("explicit policy denial", true, true, false, AvailabilityReason.POLICY_DENIED),
            )

        assertAll(
            cases.map { case ->
                org.junit.jupiter.api.function.Executable {
                    val actual =
                        OperationAvailability.classify(
                            target = target,
                            status = status,
                            routePresent = case.routePresent,
                            localNetworkPermissionAllowed = case.localNetworkPermissionAllowed,
                            policyAllowed = case.policyAllowed,
                        )
                    assertFalse(actual.allowed, case.label)
                    assertFalse(actual.advisory, case.label)
                    assertFalse(actual.permitsBoundedAttempt, case.label)
                    assertEquals(case.reason, actual.reason, case.label)
                }
            },
        )

        val internetOperation =
            OperationAvailability.classify(
                target = OperationTarget(OperationTargetClass.INTERNET_ADDRESS),
                status = NetworkStatus(hasInternet = true, hasValidatedInternet = true),
                routePresent = true,
                localNetworkPermissionAllowed = false,
            )
        assertTrue(internetOperation.allowed, "local-network permission does not gate an Internet target")

        val scopedMulticastTarget =
            OperationTarget(
                LiteralDestinationClassifier.classify("ff02::1%wlan0"),
            )
        val multicastPermissionDenied =
            OperationAvailability.classify(
                target = scopedMulticastTarget,
                status = status,
                routePresent = true,
                localNetworkPermissionAllowed = false,
            )
        assertFalse(multicastPermissionDenied.allowed)
        assertEquals(AvailabilityReason.LOCAL_NETWORK_PERMISSION_DENIED, multicastPermissionDenied.reason)
    }

    @Test
    fun `observed connectivity permits a bounded attempt without claiming target route`() {
        val actual =
            OperationAvailability.classifyObserved(
                target = LiteralDestinationClassifier.target("192.168.1.1"),
                status =
                    NetworkStatus(
                        hasInternet = true,
                        hasValidatedInternet = true,
                        hasLocalNetwork = true,
                        transport = Transport.WIFI,
                    ),
                localNetworkPermissionAllowed = true,
            )

        assertTrue(actual.permitsBoundedAttempt)
        assertTrue(actual.advisory)
        assertEquals(AvailabilityReason.TARGET_ROUTE_UNKNOWN, actual.reason)
    }

    @Test
    fun `observed Internet-only route does not admit a local destination`() {
        val actual =
            OperationAvailability.classifyObserved(
                target = LiteralDestinationClassifier.target("192.168.1.1"),
                status = NetworkStatus(hasInternet = true, transport = Transport.CELLULAR),
            )

        assertFalse(actual.allowed)
        assertEquals(AvailabilityReason.NO_ROUTE, actual.reason)
    }

    private data class CaseTarget(
        val label: String,
        val target: OperationTarget,
        val expected: OperationRequirement,
    )

    private data class AvailabilityCase(
        val label: String,
        val target: OperationTarget,
        val status: NetworkStatus,
        val routePresent: Boolean,
        val expectedRequirement: OperationRequirement,
        val allowed: Boolean,
        val advisory: Boolean = false,
    )

    private data class DenialCase(
        val label: String,
        val routePresent: Boolean,
        val localNetworkPermissionAllowed: Boolean?,
        val policyAllowed: Boolean,
        val reason: AvailabilityReason,
    )
}
