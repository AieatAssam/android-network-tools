package net.aieat.netswissknife.app.mdns

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.mdns.MdnsOperation
import net.aieat.netswissknife.core.network.mdns.MdnsDiscoverySession
import net.aieat.netswissknife.core.network.mdns.MdnsPacketParser
import net.aieat.netswissknife.core.network.mdns.MdnsQueryType
import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsSessionRecord
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.operation.OperationContext
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ResourceScopeClosedException
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    internal var monotonicClock: MonotonicClock = SystemMonotonicClock

    companion object {
        private const val MDNS_PORT = 5353
        private const val MDNS_GROUP = "224.0.0.251"
        private const val META_QUERY = "_services._dns-sd._udp.local."
        private const val BUFFER_SIZE = MdnsOperation.RECEIVE_BUFFER_SIZE_BYTES
        private const val SOCKET_TIMEOUT_MS = 500
        private const val REQUERY_INTERVAL_MS = 1_500L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    override fun discover(timeoutMs: Long): Flow<MdnsUpdate> = flow {
        val scanWindowMs = MdnsOperation.clampScanDuration(timeoutMs)
        emitAll(discover(scanWindowMs, MdnsOperation.newSession(timeoutMs = scanWindowMs, clock = monotonicClock)))
    }

    override fun discover(timeoutMs: Long, operationSession: OperationSession): Flow<MdnsUpdate> = flow {
        val scanWindowMs = MdnsOperation.clampScanDuration(timeoutMs)
        val observedTotal = AtomicInteger(0)
        val totalFound = try {
            OperationRunner.run(operationSession) {
                runDiscovery(scanWindowMs, this@flow, observedTotal::set)
            }
        } catch (deadline: OperationDeadlineExceededException) {
            // A requested scan-window deadline is normal completion for this bounded scan. The
            // runner has already closed all resources before surfacing this expected deadline.
            if (operationSession.cancellationReason != net.aieat.netswissknife.core.network.operation.CancellationReason.DEADLINE_EXCEEDED) {
                throw deadline
            }
            if (deadline.hasSuppressedFailures()) throw deadline
            observedTotal.get()
        } catch (cancelled: OperationCancellationException) {
            // A blocking socket call may surface the deadline watcher as job cancellation.
            // Treat only the scan's own expected deadline as normal completion.
            if (cancelled.reason != net.aieat.netswissknife.core.network.operation.CancellationReason.DEADLINE_EXCEEDED ||
                operationSession.cancellationReason != net.aieat.netswissknife.core.network.operation.CancellationReason.DEADLINE_EXCEEDED
            ) {
                throw cancelled
            }
            if (cancelled.hasSuppressedFailures()) throw cancelled
            observedTotal.get()
        }
        // Publish terminal success only after OperationRunner has closed the group,
        // socket, and multicast lock successfully.
        emit(MdnsUpdate.DiscoveryComplete(totalFound))
    }.flowOn(Dispatchers.IO)

    private suspend fun OperationContext.runDiscovery(
        scanWindowMs: Long,
        collector: FlowCollector<MdnsUpdate>,
        updateTotalFound: (Int) -> Unit,
    ): Int {
        // The requested scan duration includes lock/socket setup and the initial query.
        val startNanos = monotonicClock.nowNanos()
        var greatestElapsedNanos = 0L
        fun elapsedMillis(): Long {
            val observed = (monotonicClock.nowNanos() - startNanos).coerceAtLeast(0L)
            if (observed > greatestElapsedNanos) greatestElapsedNanos = observed
            return greatestElapsedNanos / NANOS_PER_MILLISECOND
        }

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = multicastLockFactory(wifiManager)
        val lockLease = registerResource(MulticastLockLease(multicastLock))
        lockLease.acquire()
        ensureOperationActive()

        val mdnsSocket = socketFactory()
        val socketLease = registerResource(MdnsSocketLease(mdnsSocket))

        mdnsSocket.reuseAddress = true
        mdnsSocket.bindToNetwork(networkBinder)
        val multicastInterface = networkBinder.localInterface()
        mdnsSocket.networkInterface = multicastInterface
        ensureOperationActive()

        var useUnicastResponse = false
        try {
            mdnsSocket.bind(InetSocketAddress(MDNS_PORT))
        } catch (error: SocketException) {
            if (error.isDeviceUnavailable()) throw error
            mdnsSocket.bind(InetSocketAddress(0))
            useUnicastResponse = true
        }

        val multicastAddress = InetSocketAddress(MDNS_GROUP, MDNS_PORT)
        val membershipLease = registerResource(
            MulticastMembershipLease(
                socketLease,
                multicastAddress,
                multicastInterface,
                isCancellation = { cancellationReason != null },
            )
        )
        membershipLease.join()
        ensureOperationActive()
        mdnsSocket.soTimeout = SOCKET_TIMEOUT_MS

        if (elapsedMillis() < scanWindowMs) {
            sendQuery(mdnsSocket, multicastAddress, META_QUERY, Type.PTR, useUnicastResponse)
        }

        val discovery = MdnsDiscoverySession()
        var lastRequeryMs = 0L
        while (true) {
            ensureOperationActive()
            val nowMs = elapsedMillis()
            val remainingMs = scanWindowMs - nowMs
            if (remainingMs <= 0L) break

            mdnsSocket.soTimeout = minOf(SOCKET_TIMEOUT_MS.toLong(), remainingMs)
                .coerceAtLeast(1L)
                .toInt()

            if (nowMs - lastRequeryMs > REQUERY_INTERVAL_MS && discovery.serviceTypes.isNotEmpty()) {
                for (type in discovery.serviceTypes) {
                    sendQuery(mdnsSocket, multicastAddress, "$type.local.", Type.PTR, useUnicastResponse)
                }
                lastRequeryMs = nowMs
            }

            val packet = receivePacket(mdnsSocket) ?: continue
            ensureOperationActive()
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

            val result = discovery.process(records)
            updateTotalFound(discovery.totalFound)
            for (query in result.queries) {
                sendQuery(
                    mdnsSocket,
                    multicastAddress,
                    query.name,
                    query.type.toDnsType(),
                    useUnicastResponse,
                )
            }
            for (service in result.services) {
                ensureOperationActive()
                collector.emit(MdnsUpdate.ServiceFound(service))
            }
        }

        for (service in discovery.finish()) {
            ensureOperationActive()
            collector.emit(MdnsUpdate.ServiceFound(service))
        }
        updateTotalFound(discovery.totalFound)
        ensureOperationActive()
        return discovery.totalFound
    }

    private suspend fun <T : AutoCloseable> OperationContext.registerResource(resource: T): T =
        try {
            resources.register(resource)
        } catch (closed: ResourceScopeClosedException) {
            // Late registration closes the resource immediately; preserve the winning operation
            // cancellation rather than surfacing the scope's closed-state exception.
            ensureOperationActive()
            throw closed
        }

    private class MulticastLockLease(private val lock: MdnsMulticastLock) : AutoCloseable {
        private var closed = false

        @Synchronized
        fun acquire() {
            if (closed) throw CancellationException("mDNS operation already stopped")
            lock.setReferenceCounted(false)
            lock.acquire()
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            if (lock.isHeld) lock.release()
        }
    }

    private class MdnsSocketLease(private val socket: MdnsSocket) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) socket.close()
        }

        fun joinGroup(address: InetSocketAddress, networkInterface: NetworkInterface?) =
            socket.joinGroup(address, networkInterface)

        fun leaveGroup(address: InetSocketAddress, networkInterface: NetworkInterface?) =
            socket.leaveGroup(address, networkInterface)
    }

    private class MulticastMembershipLease(
        private val socket: MdnsSocketLease,
        private val address: InetSocketAddress,
        private val networkInterface: NetworkInterface?,
        private val isCancellation: () -> Boolean,
    ) : AutoCloseable {
        private val lock = Any()
        private var closed = false
        private var joining = false
        private var joined = false

        fun join() {
            synchronized(lock) {
                if (closed) throw CancellationException("mDNS operation already stopped")
                joining = true
            }
            var joinSucceeded = false
            try {
                socket.joinGroup(address, networkInterface)
                joinSucceeded = true
            } finally {
                val leaveLateMembership = synchronized(lock) {
                    joining = false
                    joined = joinSucceeded && !closed
                    joinSucceeded && closed && !isCancellation()
                }
                // Cancellation closes the socket to break a blocking join. If the platform call
                // reports success after that close, still attempt to drop the late membership.
                if (leaveLateMembership) {
                    runCatching { socket.leaveGroup(address, networkInterface) }
                }
            }
        }

        override fun close() {
            val (closeSocket, leaveGroup) = synchronized(lock) {
                if (closed) return
                closed = true
                val cancelled = isCancellation()
                val shouldLeave = joined && !joining && !cancelled
                joined = false
                (joining || cancelled) to shouldLeave
            }
            if (leaveGroup) {
                socket.leaveGroup(address, networkInterface)
            }
            if (closeSocket) {
                // Do not wait for joinGroup here: socket close is what releases a blocking join.
                // During cancellation socket close also drops membership without a platform
                // leaveGroup call on the cancelling thread.
                socket.close()
            }
        }
    }

    private suspend fun OperationContext.sendQuery(
        socket: MdnsSocket,
        address: InetSocketAddress,
        name: String,
        type: Int,
        unicastResponse: Boolean,
    ) {
        try {
            ensureOperationActive()
            val bytes = MdnsPacketParser.buildMdnsQuery(name, type, unicastResponse)
            val packet = DatagramPacket(bytes, bytes.size, address)
            ensureOperationActive()
            socket.send(packet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A socket can report a generic I/O failure when cancellation closes it. Preserve
            // cancellation in that race instead of turning Stop into a discovery error.
            ensureOperationActive()
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

/** Finds cleanup failures even when coroutine cancellation wraps the runner's deadline error. */
private fun Throwable.hasSuppressedFailures(): Boolean =
    suppressed.isNotEmpty() || cause?.hasSuppressedFailures() == true
