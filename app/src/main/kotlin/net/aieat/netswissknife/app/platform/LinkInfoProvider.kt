package net.aieat.netswissknife.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

data class LinkInfo(
    val cidr: String,
    val gatewayIp: String?,
    val dnsServers: List<String>,
    val interfaceName: String?,
    val isWifi: Boolean,
    val isVpnActive: Boolean,
)

/** Pure mapping helpers kept separate from Android framework objects for JVM coverage. */
object LinkInfoMapper {
    fun cidrOf(address: String, prefixLength: Int): String? {
        val octets = address.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        val prefix = prefixLength.coerceIn(16, 30)
        val value = octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = value and mask
        return listOf(
            (network ushr 24) and 0xFF,
            (network ushr 16) and 0xFF,
            (network ushr 8) and 0xFF,
            network and 0xFF,
        ).joinToString(".") + "/$prefix"
    }

    fun defaultGateway(routes: List<Pair<String, String?>>): String? = routes
        .firstOrNull { (destination, gateway) ->
            (destination == "0.0.0.0/0" || destination == "0.0.0.0") &&
                gateway != null &&
                gateway.split('.').size == 4 &&
                gateway.split('.').all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }
        }
        ?.second
}

@Singleton
class LinkInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun getLinkInfo(): LinkInfo? = runCatching {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return null
        val properties = connectivity.getLinkProperties(network) ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network)
        val address = properties.linkAddresses
            .firstOrNull { it.address is Inet4Address }
            ?: return null
        val ipv4 = address.address.hostAddress ?: return null
        val cidr = LinkInfoMapper.cidrOf(ipv4, address.prefixLength) ?: return null
        val gateway = LinkInfoMapper.defaultGateway(
            properties.routes.map { route ->
                route.destination.toString() to route.gateway?.hostAddress
            },
        )
        LinkInfo(
            cidr = cidr,
            gatewayIp = gateway,
            dnsServers = properties.dnsServers.mapNotNull { it.hostAddress },
            interfaceName = properties.interfaceName,
            isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            isVpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        )
    }.getOrNull()
}
