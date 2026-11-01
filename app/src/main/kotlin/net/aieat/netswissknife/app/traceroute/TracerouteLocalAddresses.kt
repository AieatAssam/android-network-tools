package net.aieat.netswissknife.app.traceroute

import java.net.NetworkInterface
import java.util.Collections

/** Addresses assigned to this device's interfaces, without IPv6 scope suffixes. */
internal fun currentDeviceAddresses(): Set<String> =
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val addresses = interfaces.flatMap { Collections.list(it.inetAddresses) }
        addresses.mapNotNullTo(mutableSetOf()) { it.hostAddress?.substringBefore('%') }
    } catch (_: Exception) {
        emptySet()
    }
