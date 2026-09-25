package net.aieat.netswissknife.app.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalNetworkPermissionPolicyTest {
    @Test
    fun `selects request and admission permission paths by Android API level`() {
        val cases =
            listOf<Pair<Int, String?>>(
                26 to null,
                35 to null,
                36 to LocalNetworkPermissionPolicy.NEARBY_WIFI_DEVICES,
                37 to LocalNetworkPermissionPolicy.ACCESS_LOCAL_NETWORK,
                40 to LocalNetworkPermissionPolicy.ACCESS_LOCAL_NETWORK,
            )

        cases.forEach { (apiLevel, expectedPermission) ->
            assertEquals(
                expectedPermission,
                LocalNetworkPermissionPolicy.permissionToRequest(apiLevel),
                "request permission on API $apiLevel",
            )
            assertEquals(
                if (apiLevel >= LocalNetworkPermissionPolicy.ANDROID_17_API) {
                    LocalNetworkPermissionPolicy.ACCESS_LOCAL_NETWORK
                } else {
                    null
                },
                LocalNetworkPermissionPolicy.permissionToCheck(apiLevel),
                "admission permission on API $apiLevel",
            )
        }
    }

    @Test
    fun `only ungranted local targets request permission`() {
        val cases =
            listOf(
                RequestCase(35, "192.168.1.1", permissionGranted = false, expected = false),
                RequestCase(36, "192.168.1.1", permissionGranted = false, expected = true),
                RequestCase(37, "192.168.1.1", permissionGranted = false, expected = true),
                RequestCase(37, "1.1.1.1", permissionGranted = false, expected = false),
                RequestCase(37, "example.com", permissionGranted = false, expected = false),
                RequestCase(37, "192.168.1.1", permissionGranted = true, expected = false),
            )

        cases.forEach { case ->
            val target = LiteralDestinationClassifier.target(case.destination)
            val isLocalTarget =
                OperationAvailability.requirementFor(target) ==
                    net.aieat.netswissknife.core.network.operation.OperationRequirement.LOCAL_NETWORK
            assertEquals(
                case.expected,
                LocalNetworkPermissionPolicy.shouldRequestPermission(
                    case.apiLevel,
                    isLocalTarget,
                    case.permissionGranted,
                ),
                "API ${case.apiLevel}, target=${case.destination}, granted=${case.permissionGranted}",
            )
        }
    }

    private data class RequestCase(
        val apiLevel: Int,
        val destination: String,
        val permissionGranted: Boolean,
        val expected: Boolean,
    )
}
