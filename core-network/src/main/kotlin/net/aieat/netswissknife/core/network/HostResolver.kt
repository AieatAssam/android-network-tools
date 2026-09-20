package net.aieat.netswissknife.core.network

import java.net.InetAddress

/** Resolves a host once at the start of a ping session. */
fun interface HostResolver {
    fun resolve(host: String): String
}

object InetAddressHostResolver : HostResolver {
    override fun resolve(host: String): String = InetAddress.getByName(host).hostAddress
}
