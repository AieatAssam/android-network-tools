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
import net.aieat.netswissknife.core.network.mdns.MdnsDiscoverySession
import net.aieat.netswissknife.core.network.mdns.MdnsPacketParser
import net.aieat.netswissknife.core.network.mdns.MdnsQueryType
import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsSessionRecord
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
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
import java.net.NetworkInterface
import java.net.SocketException
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
    var networkInterface: NetworkInterface?
        get() = null
        set(_) {}
    fun bindToNetwork(binder: NetworkBinder) = Unit
    fun bind(address: InetSocketAddress)
    fun joinGroup(address: InetAddress)
    fun joinGroup(address: InetSocketAddress, networkInterface: NetworkInterface?) {
        joinGroup(address.address)
    }
    fun leaveGroup(address: InetAddress)
    fun leaveGroup(address: InetSocketAddress, networkInterface: NetworkInterface?) {
        leaveGroup(address.address)
    }
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
    override var networkInterface: NetworkInterface?
        get() = socket.networkInterface
        set(value) { if (value != null) socket.networkInterface = value }
    override fun bindToNetwork(binder: NetworkBinder) {
        try {
            binder.bind(socket)
        } catch (error: SecurityException) {
            throw LocalNetworkPermissionDeniedException(error)
        }
    }
    override fun bind(address: InetSocketAddress) = socket.bind(address)
    @Suppress("DEPRECATION")
    override fun joinGroup(address: InetAddress) = socket.joinGroup(address)
    override fun joinGroup(address: InetSocketAddress, networkInterface: NetworkInterface?) {
        if (networkInterface != null) socket.joinGroup(address, networkInterface)
        else {
            @Suppress("DEPRECATION")
            socket.joinGroup(address.address)
        }
    }
    @Suppress("DEPRECATION")
    override fun leaveGroup(address: InetAddress) = socket.leaveGroup(address)
    override fun leaveGroup(address: InetSocketAddress, networkInterface: NetworkInterface?) {
        if (networkInterface != null) socket.leaveGroup(address, networkInterface)
        else {
            @Suppress("DEPRECATION")
            socket.leaveGroup(address.address)
        }
    }
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
            var multicastGroup: InetSocketAddress? = null
            var multicastInterface: NetworkInterface? = null
            try {
                socket.reuseAddress = true
                socket.bindToNetwork(networkBinder)
                multicastInterface = networkBinder.localInterface()
                socket.networkInterface = multicastInterface
                var useUnicastResponse = false
                try {
                    socket.bind(InetSocketAddress(MDNS_PORT))
                } catch (error: SocketException) {
                    if (error.isDeviceUnavailable()) throw error
                    socket.bind(InetSocketAddress(0))
                    useUnicastResponse = true
                }

                val multicastAddress = InetSocketAddress(MDNS_GROUP, MDNS_PORT)
                multicastGroup = multicastAddress

                socket.joinGroup(multicastAddress, multicastInterface)
                socket.soTimeout = SOCKET_TIMEOUT_MS

                // Send the meta-query to enumerate all service types
                sendQuery(socket, multicastAddress, META_QUERY, Type.PTR, useUnicastResponse)

                val startTime = System.currentTimeMillis()
                val session = MdnsDiscoverySession()
                var lastRequery = 0L

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    currentCoroutineContext().ensureActive()
                    val now = System.currentTimeMillis()
                    if (now - lastRequery > REQUERY_INTERVAL_MS && session.serviceTypes.isNotEmpty()) {
                        for (type in session.serviceTypes) {
                            sendQuery(socket, multicastAddress, "$type.local.", Type.PTR, useUnicastResponse)
                        }
                        lastRequery = now
                    }

                    val packet = receivePacket(socket) ?: continue
                    currentCoroutineContext().ensureActive()
                    val message = MdnsPacketParser.parsePacket(packet) ?: continue

                    val allSections = listOf(Section.ANSWER, Section.AUTHORITY, Section.ADDITIONAL)
                    val records = mutableListOf<MdnsSessionRecord>()

                    for (section in allSections) {
                        for (record in message.getSection(section)) {
                            when (record.type) {
                                Type.PTR -> {
                                    val ptr = record as PTRRecord
                                    records += MdnsSessionRecord.Ptr(record.name.toString(), ptr.target.toString())
                                }

                                Type.SRV -> {
                                    val srv = record as SRVRecord
                                    records += MdnsSessionRecord.Srv(
                                        record.name.toString(), srv.target.toString(), srv.port
                                    )
                                }

                                Type.TXT -> {
                                    val txt = record as TXTRecord
                                    @Suppress("UNCHECKED_CAST")
                                    val strings = txt.strings as List<String>
                                    records += MdnsSessionRecord.Txt(record.name.toString(), strings)
                                }

                                Type.A -> {
                                    val a = record as ARecord
                                    val ip = a.address.hostAddress ?: continue
                                    records += MdnsSessionRecord.Address(record.name.toString(), ip)
                                }

                                Type.AAAA -> {
                                    val aaaa = record as AAAARecord
                                    val ip = aaaa.address.hostAddress ?: continue
                                    records += MdnsSessionRecord.Address(record.name.toString(), ip)
                                }
                            }
                        }
                    }

                    val result = session.process(records)
                    for (query in result.queries) {
                        sendQuery(
                            socket,
                            multicastAddress,
                            query.name,
                            query.type.toDnsType(),
                            useUnicastResponse,
                        )
                    }
                    for (service in result.services) {
                        currentCoroutineContext().ensureActive()
                        emit(MdnsUpdate.ServiceFound(service))
                    }
                }

                // Keep hostname-bearing partials that never became fully ready visible at scan end.
                for (service in session.finish()) {
                    currentCoroutineContext().ensureActive()
                    emit(MdnsUpdate.ServiceFound(service))
                }

                currentCoroutineContext().ensureActive()
                emit(MdnsUpdate.DiscoveryComplete(session.totalFound))
            } finally {
                try { multicastGroup?.let { socket.leaveGroup(it, multicastInterface) } } catch (_: Exception) {}
                socket.close()
                cancellationHandle.dispose()
            }
        } finally {
            if (multicastLock.isHeld) multicastLock.release()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun sendQuery(
        socket: MdnsSocket,
        address: InetSocketAddress,
        name: String,
        type: Int,
        unicastResponse: Boolean,
    ) {
        try {
            val bytes = MdnsPacketParser.buildMdnsQuery(name, type, unicastResponse)
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

    private fun MdnsQueryType.toDnsType(): Int = when (this) {
        MdnsQueryType.PTR -> Type.PTR
        MdnsQueryType.SRV -> Type.SRV
        MdnsQueryType.TXT -> Type.TXT
        MdnsQueryType.A -> Type.A
        MdnsQueryType.AAAA -> Type.AAAA
    }

    private fun SocketException.isDeviceUnavailable(): Boolean =
        message.orEmpty().contains("ENODEV", ignoreCase = true) ||
            message.orEmpty().contains("no such device", ignoreCase = true)
}
