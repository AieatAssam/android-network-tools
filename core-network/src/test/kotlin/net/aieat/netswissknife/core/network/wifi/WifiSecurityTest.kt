package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiSecurityTest {

    @Test
    fun `WPA2 EAP capabilities map to WPA2 Enterprise`() {
        assertEquals(
            WifiSecurity.WPA2_ENTERPRISE,
            WifiSecurity.fromCapabilities("[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]")
        )
    }

    @Test
    fun `Suite B 192 capabilities map to WPA3 Enterprise 192 bit`() {
        assertEquals(
            WifiSecurity.WPA3_ENTERPRISE_192,
            WifiSecurity.fromCapabilities("[RSN-EAP_SUITE_B_192-GCMP-256][ESS]")
        )
    }

    @Test
    fun `WPA3 Enterprise SAE capabilities map to WPA3 Enterprise`() {
        assertEquals(
            WifiSecurity.WPA3_ENTERPRISE,
            WifiSecurity.fromCapabilities("[RSN-EAP-SAE-CCMP][ESS]")
        )
    }

    @Test
    fun `existing security mappings remain unchanged`() {
        assertEquals(WifiSecurity.WPA3, WifiSecurity.fromCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals(
            WifiSecurity.WPA_WPA2,
            WifiSecurity.fromCapabilities("[WPA-PSK-CCMP][WPA2-PSK-CCMP][ESS]")
        )
        assertEquals(WifiSecurity.OPEN, WifiSecurity.fromCapabilities("[ESS]"))
        assertEquals(WifiSecurity.OWE, WifiSecurity.fromCapabilities("[RSN-OWE-CCMP][ESS]"))
    }

    @Test
    fun `capability classification distinguishes explicit open from missing or future data`() {
        val cases = listOf(
            null to WifiSecurity.UNKNOWN,
            "" to WifiSecurity.UNKNOWN,
            "   " to WifiSecurity.UNKNOWN,
            "[ESS]" to WifiSecurity.OPEN,
            "[ESS][WPS]" to WifiSecurity.UNKNOWN,
            "[PSK][ESS]" to WifiSecurity.UNKNOWN,
            "[EAP][ESS]" to WifiSecurity.UNKNOWN,
            "[SAE][ESS]" to WifiSecurity.UNKNOWN,
            "[WEP-FUTURE][ESS]" to WifiSecurity.UNKNOWN,
            "[RSN-EAP_FUTURE-CCMP][ESS]" to WifiSecurity.UNKNOWN,
            "[FUTURE-AUTH-CCMP][ESS]" to WifiSecurity.UNKNOWN,
            "[WPA4-SAE-CCMP][ESS]" to WifiSecurity.UNKNOWN,
            "[RSN-FUTURE-AUTH-CCMP][ESS]" to WifiSecurity.UNKNOWN,
            "[RSN-FUTURE-SAE-CCMP][ESS]" to WifiSecurity.UNKNOWN,
            "[RSN-PSK+FUTURE-CCMP][ESS]" to WifiSecurity.UNKNOWN,
            "[WPA2-PSK+FUTURE-CCMP][ESS]" to WifiSecurity.UNKNOWN
        )

        cases.forEach { (capabilities, expected) ->
            assertEquals(expected, WifiSecurity.fromCapabilities(capabilities), "capabilities=$capabilities")
        }
    }

    @Test
    fun `recognized encrypted capability markers never become open`() {
        val encryptedCapabilities = listOf(
            "[WEP][ESS]",
            "[WPA-PSK-CCMP][ESS]",
            "[WPA2-PSK-CCMP][ESS]",
            "[WPA3-SAE-CCMP][ESS]",
            "[WPA2-PSK+SAE-CCMP][ESS]",
            "[RSN-PSK+SAE-CCMP][ESS]",
            "[RSN-EAP-CCMP][ESS]",
            "[RSN-OWE-CCMP][ESS]"
        )

        encryptedCapabilities.forEach { capabilities ->
            val security = WifiSecurity.fromCapabilities(capabilities)
            assertTrue(security.isEncrypted, "capabilities=$capabilities classified as $security")
            assertFalse(security == WifiSecurity.OPEN, "capabilities=$capabilities classified as OPEN")
        }

        assertEquals(
            WifiSecurity.WPA2_WPA3,
            WifiSecurity.fromCapabilities("[RSN-PSK+SAE-CCMP][ESS]")
        )
        assertEquals(
            WifiSecurity.WPA2_WPA3,
            WifiSecurity.fromCapabilities("[WPA2-PSK+SAE-CCMP][ESS]")
        )
    }

    @Test
    fun `only enterprise values are marked enterprise`() {
        assertTrue(WifiSecurity.WPA2_ENTERPRISE.isEnterprise)
        assertTrue(WifiSecurity.WPA3_ENTERPRISE.isEnterprise)
        assertTrue(WifiSecurity.WPA3_ENTERPRISE_192.isEnterprise)
        assertFalse(WifiSecurity.WPA2.isEnterprise)
        assertFalse(WifiSecurity.WPA3.isEnterprise)
    }
}
