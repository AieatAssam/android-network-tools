package net.aieat.netswissknife.app.platform

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import net.aieat.netswissknife.core.network.net.LocalDestinationPolicy
import net.aieat.netswissknife.core.network.net.NetworkBinder
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/** Binds local-scope traffic to the preferred non-VPN Wi-Fi or Ethernet network. */
class AndroidNetworkBinder(
    private val connectivityManager: ConnectivityManager,
) : NetworkBinder {

    private data class ObservedNetwork(
        val network: Network,
        val linkProperties: LinkProperties?,
        val snapshot: NetworkSnapshot,
    )

    @Volatile
    private var selectedLocalNetwork: ObservedNetwork? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworks()
        override fun onLost(network: Network) = refreshNetworks()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshNetworks()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refreshNetworks()
    }

    init {
        refreshNetworks()
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .build(),
                networkCallback,
            )
        }
    }

    override val isAvailable: Boolean
        get() = selectedLocalNetwork != null

    override fun localSubnet(): String? {
        val properties = selectedLocalNetwork?.linkProperties ?: return null
        val address = properties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return LinkInfoMapper.cidrOf(address.address.hostAddress ?: return null, address.prefixLength)
    }

    override fun shouldBind(destinationIp: String): Boolean =
        LocalDestinationPolicy(localSubnet()).isLocal(destinationIp)

    override fun bind(socket: Socket) {
        selectedLocalNetwork?.network?.bindSocket(socket)
    }

    override fun bind(socket: DatagramSocket) {
        selectedLocalNetwork?.network?.bindSocket(socket)
    }

    override fun localInterface(): NetworkInterface? = selectedLocalNetwork
        ?.linkProperties
        ?.interfaceName
        ?.let { name -> runCatching { NetworkInterface.getByName(name) }.getOrNull() }

    override fun localAddress(): InetAddress? = selectedLocalNetwork
        ?.linkProperties
        ?.linkAddresses
        ?.firstOrNull { it.address is Inet4Address }
        ?.address

    @Suppress("DEPRECATION")
    private fun refreshNetworks() {
        val refreshed = runCatching {
            connectivityManager.allNetworks.mapNotNull { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
                val snapshot = NetworkSnapshot(network.toString(), capabilities.toCapabilitySnapshot())
                ObservedNetwork(
                    network = network,
                    linkProperties = connectivityManager.getLinkProperties(network),
                    snapshot = snapshot,
                )
            }
        }.getOrDefault(emptyList())
        val selectedId = NetworkSelection.selectLocal(refreshed.map(ObservedNetwork::snapshot))?.id
        selectedLocalNetwork = refreshed.firstOrNull { it.snapshot.id == selectedId }
    }

}

internal fun NetworkCapabilities.toCapabilitySnapshot(): CapabilitySnapshot {
    val transports = buildSet {
        if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(Transport.WIFI)
        if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(Transport.ETHERNET)
        if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(Transport.CELLULAR)
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(Transport.VPN)
    }.ifEmpty { setOf(Transport.OTHER) }
    return CapabilitySnapshot(
        transports = transports,
        hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        notVpn = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        hasValidatedInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
}
