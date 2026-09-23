package net.aieat.netswissknife.app.mdns

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsPacketParser
import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import javax.inject.Inject

internal interface MdnsMulticastLock {
    fun setReferenceCounted(value: Boolean)
    fun acquire()
    val isHeld: Boolean
    fun release()
}

internal interface MdnsSocket {
    var reuseAddress: Boolean
    var soTimeout: Int
    fun bind(address: InetSocketAddress)
    fun joinGroup(address: InetAddress)
    fun leaveGroup(address: InetAddress)
    fun send(packet: DatagramPacket)
    fun receive(packet: DatagramPacket)
    fun close()
}

internal enum class MdnsIoOperation { SEND_QUERY, RECEIVE_PACKET }

/** A packet transport failure; socket timeouts are expected during quiet discovery. */
internal class MdnsPacketIoException(
    val operation: MdnsIoOperation,
    cause: Exception
) : IOException(
    "mDNS ${if (operation == MdnsIoOperation.SEND_QUERY) "query send" else "packet receive"} failed" +
        (cause.message?.let { ": $it" } ?: ""),
    cause
)

private class PlatformMdnsSocket(private val socket: MulticastSocket) : MdnsSocket {
    override var reuseAddress: Boolean
        get() = socket.reuseAddress
        set(value) { socket.reuseAddress = value }
    override var soTimeout: Int
        get() = socket.soTimeout
        set(value) { socket.soTimeout = value }
    override fun bind(address: InetSocketAddress) = socket.bind(address)
    @Suppress("DEPRECATION")
    override fun joinGroup(address: InetAddress) = socket.joinGroup(address)
    @Suppress("DEPRECATION")
    override fun leaveGroup(address: InetAddress) = socket.leaveGroup(address)
    override fun send(packet: DatagramPacket) = socket.send(packet)
    override fun receive(packet: DatagramPacket) = socket.receive(packet)
    override fun close() = socket.close()
}

class MdnsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkBinder: NetworkBinder = NoOpNetworkBinder,
) : MdnsRepository {

    /** Platform seams let cancellation/cleanup be tested without relying on emulator networking. */
    internal var socketFactory: () -> MdnsSocket = { PlatformMdnsSocket(MulticastSocket(null)) }
    internal var multicastLockFactory: (WifiManager) -> MdnsMulticastLock = { manager ->
        val lock = manager.createMulticastLock("mdns_discovery")
        object : MdnsMulticastLock {
            override fun setReferenceCounted(value: Boolean) = lock.setReferenceCounted(value)
            override fun acquire() = lock.acquire()
            override val isHeld: Boolean get() = lock.isHeld
            override fun release() = lock.release()
        }
    }

    companion object {
        private const val MDNS_PORT = 5353
        private const val MDNS_GROUP = "224.0.0.251"
        private const val META_QUERY = "_services._dns-sd._udp.local."
        private const val BUFFER_SIZE = 65536
        private const val SOCKET_TIMEOUT_MS = 500
        private const val REQUERY_INTERVAL_MS = 1_500L
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun discover(timeoutMs: Long): Flow<MdnsUpdate> = flow {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = multicastLockFactory(wifiManager)

        try {
            multicastLock.setReferenceCounted(false)
            multicastLock.acquire()

            val socket = socketFactory()
            // DatagramSocket.receive is blocking and does not observe coroutine cancellation.
            // Close it as soon as the collecting job enters cancellation so the receive returns
            // immediately; final cleanup below still owns group leave and lock release.
            val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true
            ) { cause -> if (cause is kotlinx.coroutines.CancellationException) socket.close() }
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
                    currentCoroutineContext().ensureActive()
                    val now = System.currentTimeMillis()
                    if (now - lastRequery > REQUERY_INTERVAL_MS && serviceTypes.isNotEmpty()) {
                        for (type in serviceTypes) sendQuery(socket, multicastAddress, "$type.local.", Type.PTR)
                        lastRequery = now
                    }

                    val packet = receivePacket(socket) ?: continue
                    currentCoroutineContext().ensureActive()
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
                                        tryEmit(partial, emittedKeys)?.let {
                                            currentCoroutineContext().ensureActive()
                                            emit(MdnsUpdate.ServiceFound(it))
                                        }
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
                                        tryEmit(partial, emittedKeys)?.let {
                                            currentCoroutineContext().ensureActive()
                                            emit(MdnsUpdate.ServiceFound(it))
                                        }
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
                                                tryEmit(partial, emittedKeys)?.let {
                                                    currentCoroutineContext().ensureActive()
                                                    emit(MdnsUpdate.ServiceFound(it))
                                                }
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
                                                tryEmit(partial, emittedKeys)?.let {
                                                    currentCoroutineContext().ensureActive()
                                                    emit(MdnsUpdate.ServiceFound(it))
                                                }
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
                    currentCoroutineContext().ensureActive()
                    if (partial.instanceName !in emittedKeys && partial.hostname != null) {
                        currentCoroutineContext().ensureActive()
                        emit(MdnsUpdate.ServiceFound(partial.toService()))
                        emittedKeys.add(partial.instanceName)
                    }
                }

                currentCoroutineContext().ensureActive()
                emit(MdnsUpdate.DiscoveryComplete(emittedKeys.size))
            } finally {
                try { multicastGroup?.let { socket.leaveGroup(it) } } catch (_: Exception) {}
                socket.close()
                cancellationHandle.dispose()
            }
        } finally {
            if (multicastLock.isHeld) multicastLock.release()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun sendQuery(socket: MdnsSocket, address: InetSocketAddress, name: String, type: Int) {
        try {
            val bytes = MdnsPacketParser.buildMdnsQuery(name, type)
            val packet = DatagramPacket(bytes, bytes.size, address)
            socket.send(packet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A socket can report a generic I/O failure when cancellation closes it. Preserve
            // cancellation in that race instead of turning Stop into a discovery error.
            currentCoroutineContext().ensureActive()
            throw MdnsPacketIoException(MdnsIoOperation.SEND_QUERY, e)
        }
    }

    private suspend fun receivePacket(socket: MdnsSocket): ByteArray? {
        return try {
            val buf = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            buf.copyOf(packet.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw MdnsPacketIoException(MdnsIoOperation.RECEIVE_PACKET, e)
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
