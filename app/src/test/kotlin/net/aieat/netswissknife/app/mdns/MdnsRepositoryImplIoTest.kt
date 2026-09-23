package net.aieat.netswissknife.app.mdns

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.app.ui.screens.mdns.MdnsDiscoveryViewModel
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Section
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify

@OptIn(ExperimentalCoroutinesApi::class)
class MdnsRepositoryImplIoTest {

    @Test
    fun `query send failure fails discovery and never reports an empty successful scan`() = runBlocking {
        val fixture = fixture(send = { throw IOException("network unreachable") })
        val updates = mutableListOf<MdnsUpdate>()

        val failure = runCatching { fixture.repository.discover(timeoutMs = 500).toList(updates) }
            .exceptionOrNull()

        assertTrue(failure is MdnsPacketIoException)
        assertEquals(MdnsIoOperation.SEND_QUERY, (failure as MdnsPacketIoException).operation)
        assertTrue(failure.message.orEmpty().contains("network unreachable"))
        assertTrue(updates.none { it is MdnsUpdate.DiscoveryComplete })
        assertTrue(fixture.socket.closed.get())
        assertTrue(fixture.lock.released.get())
    }

    @Test
    fun `service type follow up send failure fails discovery`() = runBlocking {
        val sendCount = AtomicInteger()
        val response = serviceTypeResponse()
        val fixture = fixture(
            send = { if (sendCount.incrementAndGet() == 2) throw IOException("follow-up send failed") },
            receive = { packet ->
                System.arraycopy(response, 0, packet.data, packet.offset, response.size)
                packet.length = response.size
            }
        )
        val updates = mutableListOf<MdnsUpdate>()

        val failure = runCatching { fixture.repository.discover(timeoutMs = 5_000).toList(updates) }
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
    fun `receive failure reaches the view model error state`() = runBlocking {
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
            viewModel?.viewModelScope?.coroutineContext?.job?.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `socket receive timeout remains normal and eventually completes discovery`() = runBlocking {
        val receiveCount = AtomicInteger()
        val fixture = fixture(receive = {
            receiveCount.incrementAndGet()
            Thread.sleep(5)
            throw SocketTimeoutException("quiet network")
        })

        val updates = fixture.repository.discover(timeoutMs = 20).toList()

        assertTrue(receiveCount.get() > 0)
        assertEquals(1, updates.count { it is MdnsUpdate.DiscoveryComplete })
        assertEquals(0, (updates.last() as MdnsUpdate.DiscoveryComplete).totalFound)
        assertTrue(fixture.socket.closed.get())
        assertTrue(fixture.lock.released.get())
    }

    @Test
    fun `busy multicast port falls back to ephemeral port and sends QU queries`() = runBlocking {
        val fixture = fixture(
            bind = { address ->
                if (address.port == 5353) throw SocketException("Address already in use")
            },
            receive = {
                Thread.sleep(5)
                throw SocketTimeoutException("quiet network")
            }
        )

        fixture.repository.discover(timeoutMs = 20).toList()

        assertEquals(listOf(5353, 0), fixture.socket.bindPorts)
        assertTrue(fixture.socket.sentQueries.isNotEmpty())
        assertTrue(fixture.socket.sentQueries.all { questionClass(it) == 0x8001 })
    }

    @Test
    fun `selected network is bound before local port and multicast membership`() = runBlocking {
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

    private fun fixture(
        send: () -> Unit = {},
        bind: (InetSocketAddress) -> Unit = {},
        receive: (DatagramPacket) -> Unit = { throw SocketTimeoutException("quiet network") },
        networkBinder: NetworkBinder = NoOpNetworkBinder,
    ): Fixture {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        val socket = ScriptedMdnsSocket(send, bind, receive)
        val lock = TrackingLock()
        val repository = MdnsRepositoryImpl(context, networkBinder).apply {
            socketFactory = { socket }
            multicastLockFactory = { lock }
        }
        return Fixture(repository, socket, lock)
    }

    private data class Fixture(
        val repository: MdnsRepositoryImpl,
        val socket: ScriptedMdnsSocket,
        val lock: TrackingLock
    )

    private class ScriptedMdnsSocket(
        private val sendAction: () -> Unit,
        private val bindAction: (InetSocketAddress) -> Unit,
        private val receiveAction: (DatagramPacket) -> Unit
    ) : MdnsSocket {
        val closed = AtomicBoolean(false)
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
        override fun joinGroup(address: InetSocketAddress, networkInterface: java.net.NetworkInterface?) {
            setupOrder += "join"
            joinedInterface = networkInterface
        }
        override fun leaveGroup(address: InetAddress) = Unit
        override fun send(packet: DatagramPacket) {
            sendAction()
            sentQueries += packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
        }
        override fun receive(packet: DatagramPacket) = receiveAction(packet)
        override fun close() { closed.set(true) }
    }

    private fun questionClass(packet: ByteArray): Int =
        ((packet[packet.lastIndex - 1].toInt() and 0xFF) shl 8) or
            (packet[packet.lastIndex].toInt() and 0xFF)

    private fun serviceTypeResponse(): ByteArray {
        val owner = Name.fromString("_services._dns-sd._udp.local.")
        val target = Name.fromString("_http._tcp.local.")
        return Message().apply {
            addRecord(PTRRecord(owner, DClass.IN, 60, target), Section.ANSWER)
        }.toWire()
    }

    private class TrackingLock : MdnsMulticastLock {
        private val held = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        override fun setReferenceCounted(value: Boolean) = Unit
        override fun acquire() { held.set(true) }
        override val isHeld: Boolean get() = held.get()
        override fun release() {
            released.set(true)
            held.set(false)
        }
    }
}
