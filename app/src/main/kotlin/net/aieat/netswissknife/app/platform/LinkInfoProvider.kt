package net.aieat.netswissknife.app.platform

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
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
    fun isValidatedNetwork(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    fun cidrOf(address: String, prefixLength: Int): String? {
        if (prefixLength !in 0..32) return null
        val octets = address.split('.')
        if (octets.size != 4) return null
        val parsedOctets = octets.map { it.toIntOrNull() ?: return null }
        if (parsedOctets.any { it !in 0..255 }) return null
        val value = parsedOctets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
        val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        val network = value and mask
        return listOf(
            (network ushr 24) and 0xFF,
            (network ushr 16) and 0xFF,
            (network ushr 8) and 0xFF,
            network and 0xFF,
        ).joinToString(".") + "/$prefixLength"
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
class LinkInfoProvider private constructor(
    private val context: Context?,
    private val validatedNetworkOverride: (() -> Boolean)?,
    private val localNetworkPermissionOverride: (() -> Boolean)?,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, null, null)

    /**
     * Test-only construction hook. The production constructor always uses the
     * process ConnectivityManager; tests can provide a deterministic answer
     * without needing an Android network stack.
     */
    constructor(hasValidatedNetwork: () -> Boolean) : this(null, hasValidatedNetwork, null)

    /** Test seam for API 36 local-network permission admission. */
    constructor(
        hasValidatedNetwork: () -> Boolean,
        localNetworkPermissionAllowed: () -> Boolean,
    ) : this(null, hasValidatedNetwork, localNetworkPermissionAllowed)

    /** True only when the active network has validated Internet connectivity. */
    fun hasValidatedNetwork(): Boolean = runCatching {
        validatedNetworkOverride?.let { return@runCatching it() }
        val appContext = context ?: return@runCatching false
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
            ?: return@runCatching false
        val network = connectivity.activeNetwork ?: return@runCatching false
        LinkInfoMapper.isValidatedNetwork(connectivity.getNetworkCapabilities(network))
    }.getOrDefault(false)

    /**
     * Reports mandatory permission state on Android 17+. Android 16 uses an opt-in compat
     * restriction whose state is not visible to apps, so missing `NEARBY_WIFI_DEVICES` cannot
     * be treated as a definitive denial there; let the attempted operation report socket errors.
     */
    fun localNetworkPermissionAllowed(): Boolean = runCatching {
        localNetworkPermissionOverride?.let { return@runCatching it() }
        val permission = LocalNetworkPermissionPolicy.permissionToCheck(Build.VERSION.SDK_INT)
            ?: return@runCatching true
        val appContext = context ?: return@runCatching false
        ContextCompat.checkSelfPermission(
            appContext,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun getLinkInfo(): LinkInfo? = runCatching {
        val appContext = context ?: return@runCatching null
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
            ?: return@runCatching null
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

/** OS-version mapping for local-network protections used by both admission and UI prompts. */
internal object LocalNetworkPermissionPolicy {
    const val ANDROID_16_API = 36
    const val ANDROID_17_API = 37
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    fun permissionToRequest(apiLevel: Int): String? = when {
        apiLevel >= ANDROID_17_API -> ACCESS_LOCAL_NETWORK
        apiLevel >= ANDROID_16_API -> NEARBY_WIFI_DEVICES
        else -> null
    }

    /** API 36 enforcement is opt-in, and the app cannot observe that compat state. */
    fun permissionToCheck(apiLevel: Int): String? =
        if (apiLevel >= ANDROID_17_API) ACCESS_LOCAL_NETWORK else null

    fun shouldRequestPermission(apiLevel: Int, isLocalTarget: Boolean, permissionGranted: Boolean): Boolean =
        isLocalTarget && permissionToRequest(apiLevel) != null && !permissionGranted
}
