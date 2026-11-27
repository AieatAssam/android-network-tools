package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.subnet.Ipv4Scope
import java.text.NumberFormat
import java.util.Locale

/** Exact address and host counts with locale thousands separators (16,777,216 rather than 16.78M). */
internal fun formatSubnetCount(
    count: Long,
    locale: Locale = Locale.getDefault(),
): String = NumberFormat.getIntegerInstance(locale).format(count)

/** Plural quantity for a count. Clamped so a /0 count never overflows Int. */
internal fun subnetPluralQuantity(count: Long): Int = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/** A /31 is point-to-point (RFC 3021), so it has no broadcast address; the row would repeat the last host. */
internal fun showsBroadcastRow(prefixLength: Int): Boolean = prefixLength < POINT_TO_POINT_PREFIX

/** A /32 holds one address, so first and last host would repeat the network address. */
internal fun showsHostRange(prefixLength: Int): Boolean = prefixLength < SINGLE_ADDRESS_PREFIX

private const val POINT_TO_POINT_PREFIX = 31
private const val SINGLE_ADDRESS_PREFIX = 32

@StringRes
internal fun Ipv4Scope.labelRes(): Int =
    when (this) {
        Ipv4Scope.PRIVATE -> R.string.subnet_scope_private
        Ipv4Scope.SHARED -> R.string.subnet_scope_shared
        Ipv4Scope.LOOPBACK -> R.string.subnet_scope_loopback
        Ipv4Scope.LINK_LOCAL -> R.string.subnet_scope_link_local
        Ipv4Scope.THIS_NETWORK -> R.string.subnet_scope_this_network
        Ipv4Scope.DOCUMENTATION -> R.string.subnet_scope_documentation
        Ipv4Scope.BENCHMARK -> R.string.subnet_scope_benchmark
        Ipv4Scope.MULTICAST -> R.string.subnet_scope_multicast
        Ipv4Scope.RESERVED -> R.string.subnet_scope_reserved
        Ipv4Scope.PUBLIC -> R.string.subnet_scope_public
        Ipv4Scope.MIXED -> R.string.subnet_scope_mixed
    }
