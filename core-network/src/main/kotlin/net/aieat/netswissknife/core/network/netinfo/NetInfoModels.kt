package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiChannelHelper

enum class NetworkTransport { WIFI, CELLULAR, ETHERNET, VPN, BLUETOOTH, OTHER }

/** Network name as Android reports it, or why it is not shown. */
sealed interface SsidState {
    data class Known(
        val ssid: String,
    ) : SsidState

    data class Unavailable(
        val reason: SsidUnavailableReason,
    ) : SsidState
}

enum class SsidUnavailableReason {
    /** Location permission has not been granted, so Android redacts the name. */
    LOCATION_PERMISSION_REQUIRED,

    /** Android did not report a name, for example because Location Services are off. */
    NOT_REPORTED,
}

/** Hardware address of the active Wi-Fi or Ethernet interface. */
sealed interface MacAddressState {
    /** [randomized] is true when the locally-administered bit is set (a private or randomised MAC). */
    data class Known(
        val mac: String,
        val randomized: Boolean,
    ) : MacAddressState

    data class Unavailable(
        val reason: MacUnavailableReason,
    ) : MacAddressState
}

enum class MacUnavailableReason {
    /** The system returned nothing usable (Android 8-9 or a malformed value). */
    NOT_REPORTED,

    /** Android 10 and newer hide the hardware address from apps. */
    HIDDEN_BY_ANDROID,
}

data class WifiLinkDetails(
    val ssid: SsidState,
    val rssiDbm: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
) {
    val band: WifiBand?
        get() = frequencyMhz?.let { WifiBand.fromFrequency(it) }?.takeUnless { it == WifiBand.UNKNOWN }

    val channel: Int?
        get() = frequencyMhz?.let { frequency -> band?.let { WifiChannelHelper.frequencyToChannel(frequency, it) } }
}

data class ProxySettings(
    val host: String?,
    val port: Int?,
    val pacUrl: String?,
) {
    val isSet: Boolean
        get() = !host.isNullOrBlank() || !pacUrl.isNullOrBlank()
}

/** Everything the device reports about its active network. Built from Android reads in :app. */
data class NetInfoSnapshot(
    val hasActiveNetwork: Boolean,
    val transports: List<NetworkTransport>,
    val metered: Boolean,
    val roaming: Boolean,
    val wifi: WifiLinkDetails?,
    val interfaceName: String?,
    val addresses: List<LocalAddress>,
    val gateways: List<String>,
    val dnsServers: List<String>,
    val mtuBytes: Int?,
    val mac: MacAddressState?,
    val searchDomains: List<String>,
    val proxy: ProxySettings?,
) {
    /**
     * Identifies the network the device is on. Public IP results are only valid for the
     * network they were measured on, so a change here invalidates them. Signal and speed
     * changes are deliberately excluded.
     */
    fun networkIdentity(): String =
        if (!hasActiveNetwork) {
            NO_NETWORK_IDENTITY
        } else {
            listOf(
                interfaceName.orEmpty(),
                addresses.joinToString(",") { it.address },
                gateways.joinToString(","),
            ).joinToString("|")
        }

    companion object {
        private const val NO_NETWORK_IDENTITY = "none"

        fun noNetwork(): NetInfoSnapshot =
            NetInfoSnapshot(
                hasActiveNetwork = false,
                transports = emptyList(),
                metered = false,
                roaming = false,
                wifi = null,
                interfaceName = null,
                addresses = emptyList(),
                gateways = emptyList(),
                dnsServers = emptyList(),
                mtuBytes = null,
                mac = null,
                searchDomains = emptyList(),
                proxy = null,
            )
    }
}

/** A public address and whatever optional detail could be found for it. */
data class PublicAddressInfo(
    val address: String,
    val family: IpFamily,
    val hostname: String?,
    val geo: HopGeoLocation?,
)

/** Public IPv4 and IPv6 results, each independent: a missing IPv6 route is a normal outcome. */
data class PublicIpReport(
    val ipv4: NetworkResult<PublicAddressInfo>,
    val ipv6: NetworkResult<PublicAddressInfo>,
)
