package net.aieat.netswissknife.core.network.netinfo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class NetInfoSnapshotIdentityTest {
    private val base =
        NetInfoSnapshot(
            hasActiveNetwork = true,
            transports = listOf(NetworkTransport.WIFI),
            metered = false,
            roaming = false,
            wifi = null,
            interfaceName = "wlan0",
            addresses = listOf(LocalAddress("192.168.1.20", IpFamily.IPV4, 24, IpAddressScope.PRIVATE)),
            gateways = listOf("192.168.1.1"),
            dnsServers = emptyList(),
            mtuBytes = 1500,
            mac = null,
            searchDomains = emptyList(),
            proxy = null,
        )

    @Test
    fun `signal strength changes do not change the network identity`() {
        val fresherSignal = base.copy(wifi = WifiLinkDetails(SsidState.Known("Home"), -40, 866, 5180))
        assertEquals(base.networkIdentity(), fresherSignal.networkIdentity())
    }

    @Test
    fun `a new address or gateway changes the network identity`() {
        val newAddress =
            base.copy(addresses = listOf(LocalAddress("10.0.0.5", IpFamily.IPV4, 24, IpAddressScope.PRIVATE)))
        val newGateway = base.copy(gateways = listOf("192.168.1.254"))
        assertNotEquals(base.networkIdentity(), newAddress.networkIdentity())
        assertNotEquals(base.networkIdentity(), newGateway.networkIdentity())
    }

    @Test
    fun `no active network has its own identity`() {
        assertNotEquals(base.networkIdentity(), NetInfoSnapshot.noNetwork().networkIdentity())
    }
}
