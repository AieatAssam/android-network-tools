package net.aieat.netswissknife.app.mdns

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.viewModelScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.aieat.netswissknife.app.ui.screens.mdns.MdnsDiscoveryViewModel
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.mdns.MdnsDiscoveryLimits
import net.aieat.netswissknife.core.network.mdns.MdnsOperation
import net.aieat.netswissknife.core.network.mdns.MdnsTruncationReason
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MdnsRepositoryImplIoTest {
    @Test
    fun `query send failure fails discovery and never reports an empty successful scan`() =
        runBlocking {
            val fixture = fixture(send = { throw IOException("network unreachable") })
            val updates = mutableListOf<MdnsUpdate>()

            val failure =
                runCatching { fixture.repository.discover(timeoutMs = 500).toList(updates) }
                    .exceptionOrNull()

            assertTrue(failure is MdnsPacketIoException)
            assertEquals(MdnsIoOperation.SEND_QUERY, (failure as MdnsPacketIoException).operation)
            assertTrue(failure.message.orEmpty().contains("network unreachable"))
            assertTrue(updates.none { it is MdnsUpdate.DiscoveryComplete })
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `service type follow up send failure fails discovery`() =
        runBlocking {
            val sendCount = AtomicInteger()
            val response = serviceTypeResponse()
            val fixture =
                fixture(
                    send = { if (sendCount.incrementAndGet() == 2) throw IOException("follow-up send failed") },
                    receive = { packet ->
                        System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                        packet.length = response.size
                    },
                )
            val updates = mutableListOf<MdnsUpdate>()

            val failure =
                runCatching { fixture.repository.discover(timeoutMs = 5_000).toList(updates) }
                    .exceptionOrNull()

            assertTrue(failure is MdnsPacketIoException)
            assertEquals(MdnsIoOperation.SEND_QUERY, (failure as MdnsPacketIoException).operation)
            assertTrue(failure.message.orEmpty().contains("follow-up send failed"))
            assertEquals(2, sendCount.get())
            assertTrue(updates.none { it is MdnsUpdate.DiscoveryComplete })
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `receive failure reaches the view model error state`() =
        runBlocking {
            val fixture = fixture(receive = { throw IOException("interface disappeared") })
            Dispatchers.setMain(UnconfinedTestDispatcher())
            var viewModel: MdnsDiscoveryViewModel? = null
            try {
                val vm = MdnsDiscoveryViewModel(MdnsDiscoveryUseCase(fixture.repository))
                viewModel = vm
                vm.startScan(timeoutMs = 5_000)

                withTimeout(2_000) {
                    while (vm.uiState.value.error == null) delay(10)
                }

                val state = vm.uiState.value
                assertTrue(state.error.orEmpty().contains("interface disappeared"))
                assertFalse(state.isScanning)
                assertFalse(state.scanComplete)
                assertTrue(fixture.socket.closed.get())
                assertTrue(fixture.lock.released.get())
            } finally {
                viewModel
                    ?.viewModelScope
                    ?.coroutineContext
                    ?.job
                    ?.cancelAndJoin()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `socket receive timeout remains normal and eventually completes discovery`() =
        runBlocking {
            val receiveCount = AtomicInteger()
            val fixture =
                fixture(receive = {
                    receiveCount.incrementAndGet()
                    throw SocketTimeoutException("quiet network")
                })

            val updates = fixture.repository.discover(timeoutMs = 20).toList()

            assertEquals(20, receiveCount.get())
            assertEquals(1, updates.count { it is MdnsUpdate.DiscoveryComplete })
            assertEquals(0, (updates.last() as MdnsUpdate.DiscoveryComplete).totalFound)
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `busy multicast port falls back to ephemeral port and sends QU queries`() =
        runBlocking {
            val fixture =
                fixture(
                    bind = { address ->
                        if (address.port == 5353) throw SocketException("Address already in use")
                    },
                    receive = {
                        throw SocketTimeoutException("quiet network")
                    },
                )

            fixture.repository.discover(timeoutMs = 20).toList()

            assertEquals(listOf(5353, 0), fixture.socket.bindPorts)
            assertTrue(fixture.socket.sentQueries.isNotEmpty())
            assertTrue(fixture.socket.sentQueries.all { questionClass(it) == 0x8001 })
        }

    @Test
    fun `selected network is bound before local port and multicast membership`() =
        runBlocking {
            val binder = mockk<NetworkBinder>()
            val selectedInterface = mockk<java.net.NetworkInterface>()
            every { binder.localInterface() } returns selectedInterface
            every { binder.bind(any<java.net.DatagramSocket>()) } just Runs
            val fixture = fixture(networkBinder = binder)

            fixture.repository.discover(timeoutMs = 10).toList()

            assertEquals(listOf("network", "interface", "bind", "join"), fixture.socket.setupOrder)
            assertEquals(selectedInterface, fixture.socket.joinedInterface)
            verify(exactly = 1) { binder.bind(any<java.net.DatagramSocket>()) }
        }

    @Test
    fun `natural completion cleans resources before terminal event`() =
        runBlocking {
            val fixture = fixture()
            val operationSession = MdnsOperation.newSession(fixture.clock)
            val updates = mutableListOf<MdnsUpdate>()
            fixture.repository.discover(3L, operationSession).collect { update ->
                if (update is MdnsUpdate.DiscoveryComplete) {
                    assertEquals(listOf("leave", "socket-close", "lock-release"), fixture.cleanupOrder)
                }
                updates += update
            }

            assertEquals(1, updates.count { it is MdnsUpdate.DiscoveryComplete })
            assertEquals(listOf("leave", "socket-close", "lock-release"), fixture.cleanupOrder)
            assertEquals(1, fixture.socket.leaveCount.get())
            assertEquals(1, fixture.socket.closeCount.get())
            assertEquals(1, fixture.lock.releaseCount.get())
            assertEquals(3, fixture.socket.receiveCount.get())
            assertEquals(0, (updates.last() as MdnsUpdate.DiscoveryComplete).totalFound)
        }

    @Test
    fun `resolved service streams before discovery complete`() =
        runBlocking {
            val receiveCount = AtomicInteger()
            val serviceType = serviceTypeResponse()
            val resolvedService = resolvedServiceResponse()
            val fixture =
                fixture(receive = { packet ->
                    val response =
                        when (receiveCount.incrementAndGet()) {
                            1 -> serviceType
                            2 -> resolvedService
                            else -> throw SocketTimeoutException("quiet network")
                        }
                    System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                    packet.length = response.size
                })

            val updates = fixture.repository.discover(timeoutMs = 20).toList()

            val serviceEvents = updates.filterIsInstance<MdnsUpdate.ServiceFound>()
            assertEquals(1, serviceEvents.size)
            assertEquals("Web", serviceEvents.single().service.displayName)
            assertEquals(listOf("192.0.2.80"), serviceEvents.single().service.ipAddresses)
            assertEquals(
                listOf(MdnsUpdate.ServiceFound::class, MdnsUpdate.DiscoveryComplete::class),
                updates
                    .filter { it is MdnsUpdate.ServiceFound || it is MdnsUpdate.DiscoveryComplete }
                    .map { it::class },
            )
            assertEquals(1, (updates.last() as MdnsUpdate.DiscoveryComplete).totalFound)
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `slow collector backpressures a large service batch and resumes without losing updates`() =
        runBlocking {
            val secondReceiveEntered = CompletableDeferred<Unit>()
            val receiveCount = AtomicInteger()
            val clock = FakeClock()
            val response = manyResolvedServicesResponse(SERVICE_BATCH_SIZE)
            val fixture =
                fixture(clock = clock, receive = { packet ->
                    if (receiveCount.incrementAndGet() == 1) {
                        System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                        packet.length = response.size
                    } else {
                        secondReceiveEntered.complete(Unit)
                        clock.advanceBy(20_000_000_000L)
                        throw SocketTimeoutException("finish after draining the first batch")
                    }
                })
            val session = MdnsOperation.newSession(timeoutMs = 20_000L, clock = clock)
            val firstServiceReachedCollector = CompletableDeferred<Unit>()
            val resumeCollector = CompletableDeferred<Unit>()
            val updates = Collections.synchronizedList(mutableListOf<MdnsUpdate>())

            val scan =
                async(Dispatchers.Default) {
                    fixture.repository.discover(20_000L, session).collect { update ->
                        if (update is MdnsUpdate.ServiceFound && !firstServiceReachedCollector.isCompleted) {
                            firstServiceReachedCollector.complete(Unit)
                            resumeCollector.await()
                        }
                        updates += update
                    }
                }

            try {
                withTimeout(2_000L) { firstServiceReachedCollector.await() }

                // The packet contains more updates than channelFlow's bounded buffer. With the
                // collector parked on the first one, the producer must suspend in send() and cannot
                // advance to the socket's second receive.
                val producerAdvancedWhilePaused =
                    withTimeoutOrNull(250L) {
                        secondReceiveEntered.await()
                        true
                    } ?: false
                assertFalse(producerAdvancedWhilePaused, "producer ran past the blocked service batch")

                resumeCollector.complete(Unit)
                withTimeout(10_000L) { scan.await() }

                val services = updates.filterIsInstance<MdnsUpdate.ServiceFound>()
                assertEquals(SERVICE_BATCH_SIZE, services.size)
                assertEquals(SERVICE_BATCH_SIZE, services.map { it.service.instanceName }.toSet().size)
                assertEquals(
                    SERVICE_BATCH_SIZE,
                    updates.filterIsInstance<MdnsUpdate.DiscoveryComplete>().single().totalFound,
                )
                assertTrue(fixture.socket.closed.get())
                assertTrue(fixture.lock.released.get())
            } finally {
                resumeCollector.complete(Unit)
                if (scan.isActive) scan.cancelAndJoin()
            }
        }

    @Test
    fun `canceling a backpressured service batch emits no discovery completion`() =
        runBlocking {
            val receiveCount = AtomicInteger()
            val secondReceiveEntered = CompletableDeferred<Unit>()
            val clock = FakeClock()
            val response = manyResolvedServicesResponse(SERVICE_BATCH_SIZE)
            val fixture =
                fixture(clock = clock, receive = { packet ->
                    if (receiveCount.incrementAndGet() == 1) {
                        System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                        packet.length = response.size
                    } else {
                        secondReceiveEntered.complete(Unit)
                        throw SocketTimeoutException("unexpected receive after blocked batch")
                    }
                })
            val session = MdnsOperation.newSession(timeoutMs = 20_000L, clock = clock)
            val firstServiceReachedCollector = CompletableDeferred<Unit>()
            val holdCollector = CompletableDeferred<Unit>()
            val updates = Collections.synchronizedList(mutableListOf<MdnsUpdate>())
            val scan =
                async(Dispatchers.Default) {
                    runCatching {
                        fixture.repository.discover(20_000L, session).collect { update ->
                            if (update is MdnsUpdate.ServiceFound && !firstServiceReachedCollector.isCompleted) {
                                firstServiceReachedCollector.complete(Unit)
                                holdCollector.await()
                            }
                            updates += update
                        }
                    }
                }

            try {
                withTimeout(2_000L) { firstServiceReachedCollector.await() }
                val producerAdvancedWhilePaused =
                    withTimeoutOrNull(250L) {
                        secondReceiveEntered.await()
                        true
                    } ?: false
                assertFalse(producerAdvancedWhilePaused, "producer ran past the blocked service batch")
                session.cancel(CancellationReason.USER_STOP)
                holdCollector.complete(Unit)
                val failure = withTimeout(2_000L) { scan.await() }.exceptionOrNull()

                assertTrue(failure is OperationCancellationException, "expected typed cancellation, got $failure")
                assertEquals(CancellationReason.USER_STOP, (failure as OperationCancellationException).reason)
                assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
                assertTrue(updates.none { it is MdnsUpdate.DiscoveryComplete })
                assertTrue(fixture.socket.closed.get())
                assertTrue(fixture.lock.released.get())
                assertEquals(1, fixture.socket.closeCount.get())
            } finally {
                holdCollector.complete(Unit)
                if (scan.isActive) scan.cancelAndJoin()
            }
        }

    @Test
    fun `query cap preserves discovered services and reports partial completion`() =
        runBlocking {
            val receiveCount = AtomicInteger()
            val fixture =
                fixture(receive = { packet ->
                    val response =
                        when (receiveCount.incrementAndGet()) {
                            1 -> serviceTypeResponse()
                            2 -> resolvedServiceResponse()
                            else -> throw SocketTimeoutException("quiet network")
                        }
                    System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                    packet.length = response.size
                }).also { it.repository.discoveryLimits = MdnsDiscoveryLimits(maxQueries = 3) }

            val updates = fixture.repository.discover(timeoutMs = 20).toList()

            assertEquals(3, fixture.socket.sentQueries.size, "initial, type, and one follow-up query fit the cap")
            assertEquals(1, updates.filterIsInstance<MdnsUpdate.ServiceFound>().size)
            val complete = updates.filterIsInstance<MdnsUpdate.DiscoveryComplete>().single()
            assertEquals(1, complete.totalFound)
            assertTrue(complete.truncationReasons.contains(MdnsTruncationReason.QUERY_LIMIT))
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `periodic query cap is retained when operation deadline wins during next receive`() =
        runBlocking {
            val clock = FakeClock()
            val receiveCount = AtomicInteger()
            val typeResponse = serviceTypeResponse()
            val fixture =
                fixture(clock = clock, receive = { packet ->
                    if (receiveCount.incrementAndGet() == 1) {
                        System.arraycopy(typeResponse, 0, packet.data, packet.offset, typeResponse.size)
                        packet.length = typeResponse.size
                        clock.advanceBy(1_600_000_000L)
                    } else {
                        clock.advanceBy(500_000_000L)
                        throw OperationCancellationException(CancellationReason.DEADLINE_EXCEEDED)
                    }
                }).also { it.repository.discoveryLimits = MdnsDiscoveryLimits(maxQueries = 2) }
            val operationSession = MdnsOperation.newSession(timeoutMs = 2_000L, clock = clock)

            val updates = fixture.repository.discover(5_000L, operationSession).toList()

            assertEquals(2, fixture.socket.sentQueries.size)
            val complete = updates.filterIsInstance<MdnsUpdate.DiscoveryComplete>().single()
            assertTrue(complete.truncationReasons.contains(MdnsTruncationReason.QUERY_LIMIT))
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `packet record conversion stops at its producer cap and reports partial results`() =
        runBlocking {
            val owner = Name.fromString("_services._dns-sd._udp.local.")
            val target = Name.fromString("_http._tcp.local.")
            val noisyResponse =
                Message()
                    .apply {
                        repeat(MdnsDiscoveryLimits.DEFAULT_MAX_RECORDS_PER_PACKET + 20) {
                            addRecord(PTRRecord(owner, DClass.IN, 60, target), Section.ANSWER)
                        }
                    }.toWire()
            val fixture =
                fixture(receive = { packet ->
                    System.arraycopy(noisyResponse, 0, packet.data, packet.offset, noisyResponse.size)
                    packet.length = noisyResponse.size
                })

            val updates = fixture.repository.discover(timeoutMs = 20).toList()

            val complete = updates.filterIsInstance<MdnsUpdate.DiscoveryComplete>().single()
            assertTrue(complete.truncationReasons.contains(MdnsTruncationReason.PACKET_RECORD_LIMIT))
            assertEquals(2, fixture.socket.sentQueries.size, "initial query and one deduplicated type follow-up")
            assertTrue(fixture.socket.closed.get())
            assertTrue(fixture.lock.released.get())
        }

    @Test
    fun `service resolved without a port is emitted by discovery finish`() =
        runBlocking {
            val receiveCount = AtomicInteger()
            val fixture =
                fixture(receive = { packet ->
                    val response =
                        when (receiveCount.incrementAndGet()) {
                            1 -> serviceTypeResponse()
                            2 -> partialServiceResponse()
                            else -> throw SocketTimeoutException("quiet network")
                        }
                    System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                    packet.length = response.size
                })

            val operationSession = MdnsOperation.newSession(timeoutMs = 1_000L, clock = fixture.clock)
            val updates = fixture.repository.discover(timeoutMs = 20L, operationSession = operationSession).toList()

            assertEquals(1, updates.count { it is MdnsUpdate.ServiceFound })
            assertEquals(1, (updates.last() as MdnsUpdate.DiscoveryComplete).totalFound)
            assertEquals("host.local", (updates.first() as MdnsUpdate.ServiceFound).service.hostname)
        }

    @Test
    fun `legacy cold flow creates a fresh session for each collection`() =
        runBlocking {
            val context = mockk<Context>()
            val wifiManager = mockk<WifiManager>()
            every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
            val clock = FakeClock()
            val sockets = mutableListOf<ScriptedMdnsSocket>()
            val repository =
                MdnsRepositoryImpl(context).apply {
                    monotonicClock = clock
                    socketFactory = {
                        ScriptedMdnsSocket(
                            {},
                            {},
                            { throw SocketTimeoutException("quiet network") },
                            clock,
                            Collections.synchronizedList(mutableListOf()),
                        ).also(sockets::add)
                    }
                    multicastLockFactory = { TrackingLock() }
                }
            // Give the operation watcher room to observe this short fake-clock scan's natural
            // completion; the lower clamp itself is covered by MdnsOperationTest.
            val coldFlow = repository.discover(timeoutMs = 20L)

            coldFlow.toList()
            coldFlow.toList()

            assertEquals(2, sockets.size)
            assertTrue(sockets.all { it.closed.get() })
            assertEquals(listOf(1, 1), sockets.map { it.closeCount.get() })
        }

    @Test
    fun `monotonic scan window is deterministic across nano time wrap`() =
        runBlocking {
            val clock = FakeClock(Long.MAX_VALUE - 2_000_000L)
            val receiveCount = AtomicInteger()
            val fixture =
                fixture(clock = clock, receive = {
                    receiveCount.incrementAndGet()
                    throw SocketTimeoutException("quiet network")
                })

            fixture.repository.discover(timeoutMs = 5L).toList()

            assertEquals(5, receiveCount.get(), "deadline must use monotonic elapsed time across signed wrap")
        }

    private fun fixture(
        send: () -> Unit = {},
        bind: (InetSocketAddress) -> Unit = {},
        receive: (DatagramPacket) -> Unit = { throw SocketTimeoutException("quiet network") },
        networkBinder: NetworkBinder = NoOpNetworkBinder,
        clock: FakeClock = FakeClock(),
    ): Fixture {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        val cleanupOrder = Collections.synchronizedList(mutableListOf<String>())
        val socket = ScriptedMdnsSocket(send, bind, receive, clock, cleanupOrder)
        val lock = TrackingLock(cleanupOrder)
        val repository =
            MdnsRepositoryImpl(context, networkBinder).apply {
                monotonicClock = clock
                socketFactory = { socket }
                multicastLockFactory = { lock }
            }
        return Fixture(repository, socket, lock, clock, cleanupOrder)
    }

    private data class Fixture(
        val repository: MdnsRepositoryImpl,
        val socket: ScriptedMdnsSocket,
        val lock: TrackingLock,
        val clock: FakeClock,
        val cleanupOrder: MutableList<String>,
    )

    private class ScriptedMdnsSocket(
        private val sendAction: () -> Unit,
        private val bindAction: (InetSocketAddress) -> Unit,
        private val receiveAction: (DatagramPacket) -> Unit,
        private val clock: FakeClock,
        private val cleanupOrder: MutableList<String>,
    ) : MdnsSocket {
        val closed = AtomicBoolean(false)
        val closeCount = AtomicInteger()
        val leaveCount = AtomicInteger()
        val receiveCount = AtomicInteger()
        val bindPorts = mutableListOf<Int>()
        val sentQueries = mutableListOf<ByteArray>()
        val setupOrder = mutableListOf<String>()
        var joinedInterface: java.net.NetworkInterface? = null
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override var networkInterface: java.net.NetworkInterface? = null
            set(value) {
                field = value
                setupOrder += "interface"
            }

        override fun bindToNetwork(binder: NetworkBinder) {
            setupOrder += "network"
            binder.bind(mockk<java.net.DatagramSocket>())
        }

        override fun bind(address: InetSocketAddress) {
            setupOrder += "bind"
            bindPorts += address.port
            bindAction(address)
        }

        override fun joinGroup(address: InetAddress) = Unit

        override fun joinGroup(
            address: InetSocketAddress,
            networkInterface: java.net.NetworkInterface?,
        ) {
            setupOrder += "join"
            joinedInterface = networkInterface
        }

        override fun leaveGroup(address: InetAddress) {
            leaveCount.incrementAndGet()
            cleanupOrder += "leave"
        }

        override fun send(packet: DatagramPacket) {
            sendAction()
            sentQueries += packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
        }

        override fun leaveGroup(
            address: InetSocketAddress,
            networkInterface: java.net.NetworkInterface?,
        ) = leaveGroup(address.address)

        override fun receive(packet: DatagramPacket) {
            receiveCount.incrementAndGet()
            try {
                receiveAction(packet)
            } finally {
                clock.advanceBy(1_000_000L)
            }
        }

        override fun close() {
            closeCount.incrementAndGet()
            closed.set(true)
            cleanupOrder += "socket-close"
        }
    }

    private fun questionClass(packet: ByteArray): Int =
        ((packet[packet.lastIndex - 1].toInt() and 0xFF) shl 8) or
            (packet[packet.lastIndex].toInt() and 0xFF)

    private fun serviceTypeResponse(): ByteArray {
        val owner = Name.fromString("_services._dns-sd._udp.local.")
        val target = Name.fromString("_http._tcp.local.")
        return Message()
            .apply {
                addRecord(PTRRecord(owner, DClass.IN, 60, target), Section.ANSWER)
            }.toWire()
    }

    private fun resolvedServiceResponse(): ByteArray {
        val instance = Name.fromString("Web._http._tcp.local.")
        val host = Name.fromString("host.local.")
        return Message()
            .apply {
                addRecord(PTRRecord(Name.fromString("_http._tcp.local."), DClass.IN, 60, instance), Section.ANSWER)
                addRecord(SRVRecord(instance, DClass.IN, 60, 0, 0, 80, host), Section.ANSWER)
                addRecord(TXTRecord(instance, DClass.IN, 60, listOf("path=/status", "version=1")), Section.ANSWER)
                addRecord(ARecord(host, DClass.IN, 60, InetAddress.getByName("192.0.2.80")), Section.ANSWER)
            }.toWire()
    }

    private fun manyResolvedServicesResponse(count: Int): ByteArray {
        val serviceType = Name.fromString("_http._tcp.local.")
        return Message()
            .apply {
                repeat(count) { index ->
                    val instance = Name.fromString("Device$index._http._tcp.local.")
                    val host = Name.fromString("host$index.local.")
                    addRecord(PTRRecord(serviceType, DClass.IN, 60, instance), Section.ANSWER)
                    addRecord(SRVRecord(instance, DClass.IN, 60, 0, 0, 80, host), Section.ANSWER)
                    addRecord(ARecord(host, DClass.IN, 60, InetAddress.getByName("192.0.2.80")), Section.ANSWER)
                }
            }.toWire()
    }

    private fun partialServiceResponse(): ByteArray {
        val instance = Name.fromString("Web._http._tcp.local.")
        val host = Name.fromString("host.local.")
        return Message()
            .apply {
                addRecord(PTRRecord(Name.fromString("_http._tcp.local."), DClass.IN, 60, instance), Section.ANSWER)
                addRecord(SRVRecord(instance, DClass.IN, 60, 0, 0, 0, host), Section.ANSWER)
            }.toWire()
    }

    private class TrackingLock(
        private val cleanupOrder: MutableList<String> = Collections.synchronizedList(mutableListOf()),
    ) : MdnsMulticastLock {
        private val held = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        val releaseCount = AtomicInteger()

        override fun setReferenceCounted(value: Boolean) = Unit

        override fun acquire() {
            held.set(true)
        }

        override val isHeld: Boolean get() = held.get()

        override fun release() {
            releaseCount.incrementAndGet()
            released.set(true)
            held.set(false)
            cleanupOrder += "lock-release"
        }
    }

    private class FakeClock(
        initialNanos: Long = 0L,
    ) : MonotonicClock {
        var nowNanos: Long = initialNanos
            private set

        override fun nowNanos(): Long = nowNanos

        fun advanceBy(deltaNanos: Long) {
            require(deltaNanos >= 0L)
            nowNanos += deltaNanos
        }
    }

    private companion object {
        const val SERVICE_BATCH_SIZE = 128
    }
}
