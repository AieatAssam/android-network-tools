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
    fun `only enterprise values are marked enterprise`() {
        assertTrue(WifiSecurity.WPA2_ENTERPRISE.isEnterprise)
        assertTrue(WifiSecurity.WPA3_ENTERPRISE.isEnterprise)
        assertTrue(WifiSecurity.WPA3_ENTERPRISE_192.isEnterprise)
        assertFalse(WifiSecurity.WPA2.isEnterprise)
        assertFalse(WifiSecurity.WPA3.isEnterprise)
    }
}
