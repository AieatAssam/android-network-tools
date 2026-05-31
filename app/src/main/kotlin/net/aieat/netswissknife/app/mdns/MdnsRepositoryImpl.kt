package net.aieat.netswissknife.app.mdns

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsPacketParser
import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import javax.inject.Inject

class MdnsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MdnsRepository {

    companion object {
        private const val MDNS_PORT = 5353
        private const val MDNS_GROUP = "224.0.0.251"
        private const val META_QUERY = "_services._dns-sd._udp.local."
        private const val BUFFER_SIZE = 65536
        private const val SOCKET_TIMEOUT_MS = 500
        private const val REQUERY_INTERVAL_MS = 1_500L
    }

    override fun discover(timeoutMs: Long): Flow<MdnsUpdate> = flow {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("mdns_discovery")

        try {
            multicastLock.setReferenceCounted(false)
            multicastLock.acquire()

            val socket = MulticastSocket(null)
            var multicastGroup: InetAddress? = null
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(MDNS_PORT))

                multicastGroup = InetAddress.getByName(MDNS_GROUP)
                val multicastAddress = InetSocketAddress(MDNS_GROUP, MDNS_PORT)

                socket.joinGroup(multicastGroup)
                socket.soTimeout = SOCKET_TIMEOUT_MS

                // Send the meta-query to enumerate all service types
                sendQuery(socket, multicastAddress, META_QUERY, Type.PTR)

                val startTime = System.currentTimeMillis()
                val emittedKeys = mutableSetOf<String>()
                val serviceTypes = mutableSetOf<String>()
                val partialServices = mutableMapOf<String, PartialService>()
                var lastRequery = 0L

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val now = System.currentTimeMillis()
                    if (now - lastRequery > REQUERY_INTERVAL_MS && serviceTypes.isNotEmpty()) {
                        for (type in serviceTypes) sendQuery(socket, multicastAddress, "$type.local.", Type.PTR)
                        lastRequery = now
                    }

                    val packet = receivePacket(socket) ?: continue
                    val message = MdnsPacketParser.parsePacket(packet) ?: continue

                    val allSections = listOf(Section.ANSWER, Section.AUTHORITY, Section.ADDITIONAL)

                    for (section in allSections) {
                        for (record in message.getSection(section)) {
                            when (record.type) {
                                Type.PTR -> {
                                    val ptr = record as PTRRecord
                                    val target = ptr.target.toString()
                                    val owner = record.name.toString()

                                    when {
                                        // Meta-query response: new service type discovered
                                        owner.contains("_services._dns-sd") -> {
                                            val serviceType = MdnsPacketParser.extractServiceType(target)
                                            if (serviceType.isNotEmpty() && serviceTypes.add(serviceType)) {
                                                // target already has a trailing dot from dnsjava
                                                sendQuery(socket, multicastAddress, target, Type.PTR)
                                            }
                                        }
                                        // Instance enumeration: new service instance
                                        else -> {
                                            val key = target
                                            if (key !in partialServices) {
                                                val serviceType = MdnsPacketParser.extractServiceType(target)
                                                val displayName = MdnsPacketParser.extractDisplayName(target)
                                                partialServices[key] = PartialService(
                                                    instanceName = target,
                                                    displayName = displayName,
                                                    serviceType = serviceType
                                                )
                                                // Query for SRV+TXT
                                                sendQuery(socket, multicastAddress, target, Type.SRV)
                                                sendQuery(socket, multicastAddress, target, Type.TXT)
                                            }
                                        }
                                    }
                                }

                                Type.SRV -> {
                                    val srv = record as SRVRecord
                                    val owner = record.name.toString()
                                    val partial = partialServices[owner]
                                    if (partial != null) {
                                        partial.hostname = MdnsPacketParser.normalizeHostname(srv.target.toString())
                                        partial.port = srv.port
                                        // Query for A/AAAA
                                        sendQuery(socket, multicastAddress, srv.target.toString(), Type.A)
                                        sendQuery(socket, multicastAddress, srv.target.toString(), Type.AAAA)
                                        tryEmit(partial, emittedKeys)?.let { emit(MdnsUpdate.ServiceFound(it)) }
                                    }
                                }

                                Type.TXT -> {
                                    val txt = record as TXTRecord
                                    val owner = record.name.toString()
                                    val partial = partialServices[owner]
                                    if (partial != null) {
                                        @Suppress("UNCHECKED_CAST")
                                        val strings = txt.strings as List<String>
                                        partial.txtRecords = MdnsPacketParser.parseTxtPairs(strings)
                                        tryEmit(partial, emittedKeys)?.let { emit(MdnsUpdate.ServiceFound(it)) }
                                    }
                                }

                                Type.A -> {
                                    val a = record as ARecord
                                    val owner = record.name.toString()
                                    val ip = a.address.hostAddress ?: continue
                                    for (partial in partialServices.values) {
                                        if (partial.hostname?.let { "$it." } == owner || partial.hostname == MdnsPacketParser.normalizeHostname(owner)) {
                                            if (!partial.ipAddresses.contains(ip)) {
                                                partial.ipAddresses.add(ip)
                                                tryEmit(partial, emittedKeys)?.let { emit(MdnsUpdate.ServiceFound(it)) }
                                            }
                                        }
                                    }
                                }

                                Type.AAAA -> {
                                    val aaaa = record as AAAARecord
                                    val owner = record.name.toString()
                                    val ip = aaaa.address.hostAddress ?: continue
                                    for (partial in partialServices.values) {
                                        if (partial.hostname?.let { "$it." } == owner || partial.hostname == MdnsPacketParser.normalizeHostname(owner)) {
                                            if (!partial.ipAddresses.contains(ip)) {
                                                partial.ipAddresses.add(ip)
                                                tryEmit(partial, emittedKeys)?.let { emit(MdnsUpdate.ServiceFound(it)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Emit any partial services that have at minimum a hostname
                for (partial in partialServices.values) {
                    if (partial.instanceName !in emittedKeys && partial.hostname != null) {
                        emit(MdnsUpdate.ServiceFound(partial.toService()))
                        emittedKeys.add(partial.instanceName)
                    }
                }

                emit(MdnsUpdate.DiscoveryComplete(emittedKeys.size))
            } finally {
                try { multicastGroup?.let { socket.leaveGroup(it) } } catch (_: Exception) {}
                socket.close()
            }
        } finally {
            if (multicastLock.isHeld) multicastLock.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun sendQuery(socket: MulticastSocket, address: InetSocketAddress, name: String, type: Int) {
        try {
            val bytes = MdnsPacketParser.buildMdnsQuery(name, type)
            val packet = DatagramPacket(bytes, bytes.size, address)
            socket.send(packet)
        } catch (_: Exception) {}
    }

    private fun receivePacket(socket: MulticastSocket): ByteArray? {
        return try {
            val buf = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            buf.copyOf(packet.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryEmit(partial: PartialService, emitted: MutableSet<String>): DiscoveredService? {
        if (partial.hostname == null || partial.port == 0) return null
        emitted.add(partial.instanceName) // track for end-of-scan sweep dedup
        return partial.toService()
    }

    private class PartialService(
        val instanceName: String,
        val displayName: String,
        val serviceType: String,
        var hostname: String? = null,
        var port: Int = 0,
        val ipAddresses: MutableList<String> = mutableListOf(),
        var txtRecords: Map<String, String> = emptyMap()
    ) {
        fun toService() = DiscoveredService(
            serviceType = serviceType,
            instanceName = instanceName,
            displayName = displayName,
            hostname = hostname ?: "",
            port = port,
            ipAddresses = ipAddresses.toList(),
            txtRecords = txtRecords
        )
    }
}
