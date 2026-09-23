package net.aieat.netswissknife.app.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkSelectionTest {

    @Test
    fun `selects wifi ahead of ethernet cellular and VPN`() {
        val vpn = network("vpn", Transport.VPN, notVpn = false, hasInternet = true)
        val ethernet = network("ethernet", Transport.ETHERNET)
        val cellular = network("cellular", Transport.CELLULAR, hasInternet = true)
        val wifi = network("wifi", Transport.WIFI)

        assertEquals(wifi, NetworkSelection.selectLocal(listOf(vpn, ethernet, cellular, wifi)))
    }

    @Test
    fun `selects ethernet when wifi is unavailable`() {
        val ethernet = network("ethernet", Transport.ETHERNET)

        assertEquals(
            ethernet,
            NetworkSelection.selectLocal(listOf(network("cellular", Transport.CELLULAR), ethernet)),
        )
    }

    @Test
    fun `does not select a VPN or any network marked as VPN`() {
        val vpnOnly = network("vpn", Transport.VPN, notVpn = false, hasInternet = true)
        val wifiOnVpn = network("wifi", Transport.WIFI, notVpn = false)

        assertNull(NetworkSelection.selectLocal(listOf(vpnOnly)))
        assertNull(NetworkSelection.selectLocal(listOf(wifiOnVpn)))
    }

    @Test
    fun `reports VPN internet while identifying underlying wifi as the local transport`() {
        val vpn = network("vpn", Transport.VPN, notVpn = false, hasInternet = true)
        val wifi = network("wifi", Transport.WIFI)

        val status = NetworkSelection.status(listOf(vpn, wifi), activeNetworkId = "vpn")

        assertTrue(status.hasInternet)
        assertTrue(status.vpnActive)
        assertTrue(status.hasLocalNetwork)
        assertEquals(Transport.WIFI, status.transport)
    }

    @Test
    fun `reports the active transport when no local network exists`() {
        val cellular = network("cellular", Transport.CELLULAR, hasInternet = true)
        val status = NetworkSelection.status(listOf(cellular), activeNetworkId = "cellular")

        assertTrue(status.hasInternet)
        assertFalse(status.vpnActive)
        assertFalse(status.hasLocalNetwork)
        assertEquals(Transport.CELLULAR, status.transport)
    }

    @Test
    fun `empty network state has no connectivity or transport`() {
        assertEquals(NetworkStatus(), NetworkSelection.status(emptyList()))
    }

    @Test
    fun `status falls back to reported network capabilities without an active id`() {
        val other = NetworkSnapshot(
            id = "other",
            capabilities = CapabilitySnapshot(
                transports = setOf(Transport.OTHER),
                hasInternet = true,
                notVpn = true,
            ),
        )

        val status = NetworkSelection.status(listOf(other), activeNetworkId = "unknown")

        assertTrue(status.hasInternet)
        assertFalse(status.hasLocalNetwork)
        assertFalse(status.vpnActive)
        assertEquals(Transport.OTHER, status.transport)
    }

    private fun network(
        id: String,
        transport: Transport,
        notVpn: Boolean = true,
        hasInternet: Boolean = false,
    ) = NetworkSnapshot(
        id = id,
        capabilities = CapabilitySnapshot(
            transports = setOf(transport),
            hasInternet = hasInternet,
            notVpn = notVpn,
        ),
    )
}
