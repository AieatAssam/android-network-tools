package net.aieat.netswissknife.app.platform

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Singleton

/** Publishes internet, local-network, VPN, and preferred-transport state. */
@Singleton
class ConnectivityObserver(
    private val connectivityManager: ConnectivityManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @SuppressLint("MissingPermission")
    val status: StateFlow<NetworkStatus> = callbackFlow {
        val publish = { trySend(readStatus()) }
        val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { publish() }
            override fun onLost(network: Network) { publish() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { publish() }
            override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                publish()
            }
        }
        val vpnCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { publish() }
            override fun onLost(network: Network) { publish() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { publish() }
        }
        var defaultRegistered = false
        var vpnRegistered = false
        try {
            connectivityManager.registerDefaultNetworkCallback(defaultCallback)
            defaultRegistered = true
        } catch (_: RuntimeException) {
            // Keep the last readable snapshot if callback registration is unavailable.
        }
        try {
            val vpnRequest = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(vpnRequest, vpnCallback)
            vpnRegistered = true
        } catch (_: RuntimeException) {
            // Default-network callbacks still report changes to the user's active route.
        }
        publish()

        awaitClose {
            if (defaultRegistered) runCatching { connectivityManager.unregisterNetworkCallback(defaultCallback) }
            if (vpnRegistered) runCatching { connectivityManager.unregisterNetworkCallback(vpnCallback) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, NetworkStatus())

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readStatus(): NetworkStatus = runCatching {
        val activeNetwork = connectivityManager.activeNetwork
        val networks = connectivityManager.allNetworks.toMutableList()
        if (activeNetwork != null && networks.none { it == activeNetwork }) networks += activeNetwork
        val snapshots = networks.mapNotNull { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                NetworkSnapshot(network.toString(), capabilities.toCapabilitySnapshot())
            }
        }
        NetworkSelection.status(snapshots, activeNetwork?.toString())
    }.getOrDefault(NetworkStatus())
}
