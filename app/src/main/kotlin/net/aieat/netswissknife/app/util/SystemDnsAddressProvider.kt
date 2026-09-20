package net.aieat.netswissknife.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
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
    @param:ApplicationContext private val context: Context
) {
    data class SystemDnsInfo(
        val addresses: List<String>,
        val privateDnsActive: Boolean,
        val privateDnsHost: String?
    )

    fun getInfo(): SystemDnsInfo {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return SystemDnsInfo(emptyList(), false, null)
            val props = cm.getLinkProperties(network)
                ?: return SystemDnsInfo(emptyList(), false, null)
            SystemDnsInfo(
                addresses = props.dnsServers.mapNotNull { it.hostAddress },
                privateDnsActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && props.isPrivateDnsActive,
                privateDnsHost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) props.privateDnsServerName else null
            )
        } catch (_: Exception) {
            SystemDnsInfo(emptyList(), false, null)
        }
    }

    /**
     * Returns the DNS server IP strings for the active network, or an empty list if
     * they cannot be determined (e.g. no active network, permissions missing).
     */
    fun getAddresses(): List<String> {
        return getInfo().addresses
    }
}
