package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.mdns.DiscoveredService

/** Number of TXT entries shown before the "Show all" control. */
internal const val MDNS_TXT_VISIBLE_LIMIT = 5

/** Whole seconds shown for the scan timer. Negative clocks read as zero. */
internal fun mdnsElapsedSeconds(elapsedMs: Long): Long = elapsedMs.coerceAtLeast(0L) / 1_000L

/** Fraction of the scan window that has passed, clamped to 0..1. */
internal fun mdnsScanProgress(elapsedMs: Long, windowMs: Long): Float {
    if (windowMs <= 0L) return 0f
    return (elapsedMs.coerceAtLeast(0L).toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
}

/** Display name and icon for a DNS-SD service type. */
internal data class MdnsServiceTypeInfo(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val known: Boolean,
)

private val knownServiceTypes: Map<String, MdnsServiceTypeInfo> by lazy {
    val printer = MdnsServiceTypeInfo(R.string.mdns_type_printer, Icons.Default.Print, true)
    mapOf(
        "_http._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_http, Icons.Default.Http, true),
        "_https._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_https, Icons.Default.Lock, true),
        "_ssh._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_ssh, Icons.Default.Terminal, true),
        "_ipp._tcp" to printer,
        "_ipps._tcp" to printer,
        "_printer._tcp" to printer,
        "_googlecast._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_cast, Icons.Default.Cast, true),
        "_airplay._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_airplay, Icons.Default.Tv, true),
        "_raop._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_airplay, Icons.Default.Tv, true),
        "_smb._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_smb, Icons.Default.FolderShared, true),
        "_workstation._tcp" to MdnsServiceTypeInfo(R.string.mdns_type_workstation, Icons.Default.Computer, true),
    )
}

/** Looks up a service type case-insensitively; unknown types get the generic label and router icon. */
internal fun mdnsServiceTypeInfo(serviceType: String): MdnsServiceTypeInfo =
    knownServiceTypes[serviceType.trim().lowercase()]
        ?: MdnsServiceTypeInfo(R.string.mdns_type_generic, Icons.Default.Router, known = false)

/**
 * Groups services by the device they belong to. The device key is the hostname when there is one,
 * otherwise the first address, otherwise the instance name, so one device's services stay together.
 */
internal fun groupMdnsServicesByDevice(services: List<DiscoveredService>): Map<String, List<DiscoveredService>> =
    services
        .groupBy { service -> deviceKey(service) }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

internal fun deviceKey(service: DiscoveredService): String {
    val hostname = service.hostname.trim().trimEnd('.')
    return when {
        hostname.isNotEmpty() -> hostname
        service.ipAddresses.isNotEmpty() -> service.ipAddresses.first()
        else -> service.instanceName
    }
}

/** TXT entries in a stable order, limited to [MDNS_TXT_VISIBLE_LIMIT] unless [showAll] is set. */
internal fun visibleTxtEntries(
    txt: Map<String, String>,
    showAll: Boolean,
): List<Pair<String, String>> {
    val sorted = txt.entries.map { it.key to it.value }.sortedBy { it.first }
    return if (showAll) sorted else sorted.take(MDNS_TXT_VISIBLE_LIMIT)
}
