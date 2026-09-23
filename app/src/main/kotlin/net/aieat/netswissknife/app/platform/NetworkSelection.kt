package net.aieat.netswissknife.app.platform

enum class Transport {
    WIFI,
    ETHERNET,
    CELLULAR,
    VPN,
    OTHER,
}

data class CapabilitySnapshot(
    val transports: Set<Transport>,
    val hasInternet: Boolean,
    val notVpn: Boolean,
)

data class NetworkSnapshot(
    val id: String,
    val capabilities: CapabilitySnapshot,
)

data class NetworkStatus(
    val hasInternet: Boolean = false,
    val hasLocalNetwork: Boolean = false,
    val vpnActive: Boolean = false,
    val transport: Transport? = null,
)

/** Pure network choice and status mapping, independent of Android framework objects. */
object NetworkSelection {
    private val localTransportPreference = listOf(Transport.WIFI, Transport.ETHERNET)
    private val transportPreference = listOf(
        Transport.WIFI,
        Transport.ETHERNET,
        Transport.CELLULAR,
        Transport.VPN,
        Transport.OTHER,
    )

    fun selectLocal(networks: List<NetworkSnapshot>): NetworkSnapshot? =
        localTransportPreference.firstNotNullOfOrNull { preferred ->
            networks.firstOrNull { network ->
                network.capabilities.notVpn && preferred in network.capabilities.transports
            }
        }

    fun status(
        networks: List<NetworkSnapshot>,
        activeNetworkId: String? = null,
    ): NetworkStatus {
        val active = activeNetworkId?.let { id -> networks.firstOrNull { it.id == id } }
        val localNetwork = selectLocal(networks)
        return NetworkStatus(
            hasInternet = active?.capabilities?.hasInternet
                ?: networks.any { it.capabilities.hasInternet },
            hasLocalNetwork = localNetwork != null,
            vpnActive = networks.any { Transport.VPN in it.capabilities.transports },
            transport = localNetwork?.capabilities?.let(::preferredTransport)
                ?: active?.capabilities?.let(::preferredTransport)
                ?: networks.firstOrNull()?.capabilities?.let(::preferredTransport),
        )
    }

    private fun preferredTransport(capabilities: CapabilitySnapshot): Transport? =
        transportPreference.firstOrNull { it in capabilities.transports }
}
