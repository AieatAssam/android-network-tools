package net.aieat.netswissknife.app.util

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the DNS server addresses from the currently active network using
 * [ConnectivityManager] / [android.net.LinkProperties].
 *
 * Must live in the app layer because it depends on the Android SDK.
 */
@Singleton
class SystemDnsAddressProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the DNS server IP strings for the active network, or an empty list if
     * they cannot be determined (e.g. no active network, permissions missing).
     */
    fun getAddresses(): List<String> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return emptyList()
            val props = cm.getLinkProperties(network) ?: return emptyList()
            props.dnsServers.mapNotNull { it.hostAddress }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
