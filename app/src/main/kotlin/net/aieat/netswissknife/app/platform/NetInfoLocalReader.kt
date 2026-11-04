package net.aieat.netswissknife.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import net.aieat.netswissknife.core.network.netinfo.IpAddressClassifier
import net.aieat.netswissknife.core.network.netinfo.LinkValueSentinels
import net.aieat.netswissknife.core.network.netinfo.LocalAddress
import net.aieat.netswissknife.core.network.netinfo.MacAddressPolicy
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.NetworkTransport
import net.aieat.netswissknife.core.network.netinfo.ProxySettings
import net.aieat.netswissknife.core.network.netinfo.WifiLinkDetails
import net.aieat.netswissknife.core.network.netinfo.WifiSsidPolicy
import java.net.NetworkInterface

/** Reads the device's active network. Implementations never throw; unreadable fields are null. */
interface NetInfoLocalReader {
    fun read(): NetInfoSnapshot

    /** Emits when the default network or its addresses, routes or capabilities change. */
    val changes: Flow<Unit>
}

/** Android implementation: ConnectivityManager, LinkProperties and the Wi-Fi link, read defensively. */
class AndroidNetInfoReader(
    private val context: Context,
) : NetInfoLocalReader {
    private val connectivity: ConnectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiReader = AndroidWifiLinkReader(context)

    override fun read(): NetInfoSnapshot = runCatching { readActiveNetwork() }.getOrDefault(NetInfoSnapshot.noNetwork())

    override val changes: Flow<Unit> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(Unit)
                    }

                    override fun onLost(network: Network) {
                        trySend(Unit)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(Unit)
                    }

                    override fun onLinkPropertiesChanged(
                        network: Network,
                        linkProperties: LinkProperties,
                    ) {
                        trySend(Unit)
                    }
                }
            runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
        }

    private fun readActiveNetwork(): NetInfoSnapshot {
        val network = connectivity.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        return if (network != null && capabilities != null) {
            snapshotOf(network, capabilities)
        } else {
            NetInfoSnapshot.noNetwork()
        }
    }

    private fun snapshotOf(
        network: Network,
        capabilities: NetworkCapabilities,
    ): NetInfoSnapshot {
        val properties = connectivity.getLinkProperties(network)
        val transports = transportsOf(capabilities)
        val interfaceName = properties?.interfaceName
        val wiredOrWireless = transports.any { it == NetworkTransport.WIFI || it == NetworkTransport.ETHERNET }
        return NetInfoSnapshot(
            hasActiveNetwork = true,
            transports = transports,
            metered = !capabilities.hasCapability(NET_CAPABILITY_NOT_METERED),
            roaming = !capabilities.hasCapability(NET_CAPABILITY_NOT_ROAMING),
            wifi = if (transports.contains(NetworkTransport.WIFI)) wifiReader.read(capabilities) else null,
            interfaceName = interfaceName,
            addresses = properties?.linkAddresses.orEmpty().mapNotNull(::localAddressOf),
            gateways = gatewaysOf(properties),
            dnsServers = properties?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') },
            mtuBytes = mtuOf(properties, interfaceName),
            mac =
                if (wiredOrWireless && interfaceName != null) {
                    MacAddressPolicy.resolve(hardwareAddress(interfaceName), Build.VERSION.SDK_INT)
                } else {
                    null
                },
            searchDomains = searchDomainsOf(properties),
            proxy = proxyOf(properties),
        )
    }

    private fun transportsOf(capabilities: NetworkCapabilities): List<NetworkTransport> {
        val mapped =
            listOf(
                TRANSPORT_WIFI to NetworkTransport.WIFI,
                TRANSPORT_CELLULAR to NetworkTransport.CELLULAR,
                TRANSPORT_ETHERNET to NetworkTransport.ETHERNET,
                TRANSPORT_VPN to NetworkTransport.VPN,
                TRANSPORT_BLUETOOTH to NetworkTransport.BLUETOOTH,
            ).filter { (transport, _) -> capabilities.hasTransport(transport) }
                .map { (_, value) -> value }
        return mapped.ifEmpty { listOf(NetworkTransport.OTHER) }
    }

    private fun localAddressOf(link: LinkAddress): LocalAddress? {
        val display = link.address.hostAddress?.let(IpAddressClassifier::displayAddress)
        val family = display?.let(IpAddressClassifier::familyOf)
        val scope = display?.let(IpAddressClassifier::scopeOf)
        return if (display != null && family != null && scope != null) {
            LocalAddress(display, family, link.prefixLength, scope)
        } else {
            null
        }
    }

    private fun searchDomainsOf(properties: LinkProperties?): List<String> {
        val domains = properties?.domains.orEmpty()
        return domains.split(' ').filter { it.isNotBlank() }
    }

    private fun gatewaysOf(properties: LinkProperties?): List<String> =
        properties
            ?.routes
            .orEmpty()
            .filter { route -> route.isDefaultRoute && route.gateway?.isAnyLocalAddress == false }
            .mapNotNull { it.gateway?.hostAddress?.substringBefore('%') }
            .distinct()

    private fun mtuOf(
        properties: LinkProperties?,
        interfaceName: String?,
    ): Int? {
        val fromLink =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) properties?.mtu else null
        val fromInterface =
            interfaceName?.let { name ->
                runCatching { NetworkInterface.getByName(name)?.mtu }.getOrNull()
            }
        return (fromLink ?: fromInterface)?.let(LinkValueSentinels::mtuBytes)
    }

    private fun hardwareAddress(interfaceName: String): String? =
        runCatching {
            NetworkInterface.getByName(interfaceName)?.hardwareAddress?.joinToString(":") { "%02X".format(it) }
        }.getOrNull()

    private fun proxyOf(properties: LinkProperties?): ProxySettings? {
        val proxy = properties?.httpProxy ?: return null
        val settings =
            ProxySettings(
                host = proxy.host?.takeIf { it.isNotBlank() },
                port = proxy.port.takeIf { it > 0 },
                pacUrl = proxy.pacFileUrl?.toString()?.takeIf { it.isNotBlank() },
            )
        return settings.takeIf { it.isSet }
    }
}

/** Reads the Wi-Fi link. The SSID is passed through [WifiSsidPolicy]; signal values through [LinkValueSentinels]. */
internal class AndroidWifiLinkReader(
    private val context: Context,
) {
    @Suppress("DEPRECATION")
    fun read(capabilities: NetworkCapabilities): WifiLinkDetails {
        val info: WifiInfo? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                capabilities.transportInfo as? WifiInfo
            } else {
                wifiManager.connectionInfo
            }
        return WifiLinkDetails(
            ssid = WifiSsidPolicy.resolve(info?.ssid, hasLocationPermission()),
            rssiDbm = info?.let { LinkValueSentinels.rssiDbm(it.rssi) },
            linkSpeedMbps = info?.let { LinkValueSentinels.linkSpeedMbps(it.linkSpeed) },
            frequencyMhz = info?.let { LinkValueSentinels.frequencyMhz(it.frequency) },
        )
    }

    /** The same permission the Wi-Fi Scanner requests. Android redacts the SSID without it. */
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}
