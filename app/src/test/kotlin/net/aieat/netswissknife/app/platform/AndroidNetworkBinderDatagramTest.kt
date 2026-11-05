package net.aieat.netswissknife.app.platform

import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import net.aieat.netswissknife.core.network.net.LocalNetworkBindingUnavailableException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress

class AndroidNetworkBinderDatagramTest {
    @Test
    fun `local datagram bind uses the selected network`() {
        val fixture = fixture()
        DatagramSocket(null as SocketAddress?).use { socket ->
            every { fixture.network.bindSocket(socket) } just Runs

            assertTrue(fixture.binder.bindIfLocal(socket, "192.168.1.70"))

            verify(exactly = 1) { fixture.network.bindSocket(socket) }
        }
    }

    @Test
    fun `link properties callback invalidates a destination on the former subnet`() {
        var properties = linkProperties("192.168.1.5")
        val fixture = fixture(linkPropertiesProvider = { properties })
        properties = linkProperties("192.168.2.5")
        fixture.callback.onLinkPropertiesChanged(fixture.network, properties)

        DatagramSocket(null as SocketAddress?).use { socket ->
            assertFalse(fixture.binder.shouldBind("192.168.1.70"))
            assertFalse(fixture.binder.bindIfLocal(socket, "192.168.1.70"))
            verify(exactly = 0) { fixture.network.bindSocket(any<DatagramSocket>()) }
        }
    }

    @Test
    fun `lost selected network makes required datagram binding fail closed`() {
        val fixture = fixture()
        fixture.updateNetworks(emptyArray())
        fixture.callback.onLost(fixture.network)

        DatagramSocket(null as SocketAddress?).use { socket ->
            assertFalse(fixture.binder.bindIfLocal(socket, "192.168.1.70"))
            assertThrows(LocalNetworkBindingUnavailableException::class.java) {
                fixture.binder.bind(socket)
            }
            verify(exactly = 0) { fixture.network.bindSocket(any<DatagramSocket>()) }
        }
    }

    @Test
    fun `platform bind failure is translated and retains its cause`() {
        val fixture = fixture()
        val bindError = IOException("network vanished")
        DatagramSocket(null as SocketAddress?).use { socket ->
            every { fixture.network.bindSocket(socket) } throws bindError

            val failure =
                assertThrows(LocalNetworkBindingUnavailableException::class.java) {
                    fixture.binder.bindIfLocal(socket, "192.168.1.70")
                }

            assertSame(bindError, failure.cause)
        }
    }

    @Test
    fun `wifi selection supports local multicast discovery`() {
        assertTrue(fixture().binder.supportsLocalMulticast())
    }

    @Test
    fun `cellular-only selection does not support local multicast discovery`() {
        val fixture = fixture(transport = NetworkCapabilities.TRANSPORT_CELLULAR)

        assertFalse(fixture.binder.supportsLocalMulticast())
    }

    @Test
    fun `lost local network does not support local multicast discovery`() {
        val fixture = fixture()
        fixture.updateNetworks(emptyArray())
        fixture.callback.onLost(fixture.network)

        assertFalse(fixture.binder.supportsLocalMulticast())
    }

    @Test
    fun `tailscale vpn network listed first does not displace the wifi selection`() {
        val wifi = mockk<Network>()
        val vpn = mockk<Network>()
        val connectivityManager = mockk<ConnectivityManager>()
        val wifiCapabilities = mockk<NetworkCapabilities>()
        val vpnCapabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.allNetworks } returns arrayOf(vpn, wifi)
        every { connectivityManager.getNetworkCapabilities(wifi) } returns wifiCapabilities
        every { connectivityManager.getNetworkCapabilities(vpn) } returns vpnCapabilities
        every { connectivityManager.getLinkProperties(wifi) } returns linkProperties("192.168.1.5")
        every { connectivityManager.getLinkProperties(vpn) } returns linkProperties("100.101.102.103")
        every { wifiCapabilities.hasTransport(any()) } answers { firstArg<Int>() == NetworkCapabilities.TRANSPORT_WIFI }
        every { wifiCapabilities.hasCapability(any()) } answers {
            firstArg<Int>() == NetworkCapabilities.NET_CAPABILITY_NOT_VPN
        }
        every { vpnCapabilities.hasTransport(any()) } answers { firstArg<Int>() == NetworkCapabilities.TRANSPORT_VPN }
        every { vpnCapabilities.hasCapability(any()) } returns false
        val binder = AndroidNetworkBinder(connectivityManager) {}

        assertTrue(binder.supportsLocalMulticast())
        DatagramSocket(null as SocketAddress?).use { socket ->
            every { wifi.bindSocket(socket) } just Runs

            binder.bind(socket)

            verify(exactly = 1) { wifi.bindSocket(socket) }
            verify(exactly = 0) { vpn.bindSocket(any<DatagramSocket>()) }
        }
    }

    @Test
    fun `vpn network with an address never supplies the multicast selection alone`() {
        val vpn = mockk<Network>()
        val connectivityManager = mockk<ConnectivityManager>()
        val vpnCapabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.allNetworks } returns arrayOf(vpn)
        every { connectivityManager.getNetworkCapabilities(vpn) } returns vpnCapabilities
        every { connectivityManager.getLinkProperties(vpn) } returns linkProperties("100.101.102.103")
        every { vpnCapabilities.hasTransport(any()) } answers { firstArg<Int>() == NetworkCapabilities.TRANSPORT_VPN }
        every { vpnCapabilities.hasCapability(any()) } returns false
        val binder = AndroidNetworkBinder(connectivityManager) {}

        assertFalse(binder.supportsLocalMulticast())
        DatagramSocket(null as SocketAddress?).use { socket ->
            assertThrows(LocalNetworkBindingUnavailableException::class.java) { binder.bind(socket) }
            verify(exactly = 0) { vpn.bindSocket(any<DatagramSocket>()) }
        }
    }

    private data class Fixture(
        val binder: AndroidNetworkBinder,
        val network: Network,
        val callback: ConnectivityManager.NetworkCallback,
        val updateNetworks: (Array<Network>) -> Unit,
    )

    private fun fixture(
        linkPropertiesProvider: () -> LinkProperties = { linkProperties("192.168.1.5") },
        transport: Int = NetworkCapabilities.TRANSPORT_WIFI,
    ): Fixture {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        var networks = arrayOf(network)
        every { connectivityManager.allNetworks } answers { networks }
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } answers { linkPropertiesProvider() }
        every { capabilities.hasTransport(any()) } answers {
            firstArg<Int>() == transport
        }
        every { capabilities.hasCapability(any()) } answers {
            firstArg<Int>() == NetworkCapabilities.NET_CAPABILITY_NOT_VPN
        }
        var callback: ConnectivityManager.NetworkCallback? = null
        val binder = AndroidNetworkBinder(connectivityManager) { callback = it }
        return Fixture(
            binder = binder,
            network = network,
            callback = requireNotNull(callback),
            updateNetworks = { current -> networks = current },
        )
    }

    private fun linkProperties(address: String): LinkProperties {
        val linkAddress = mockk<LinkAddress>()
        every { linkAddress.address } returns InetAddress.getByName(address)
        every { linkAddress.prefixLength } returns 24
        return mockk<LinkProperties> {
            every { linkAddresses } returns listOf(linkAddress)
        }
    }
}
