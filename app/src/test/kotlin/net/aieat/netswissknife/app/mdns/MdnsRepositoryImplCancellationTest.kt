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
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.ui.screens.mdns.MdnsDiscoveryViewModel
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class MdnsRepositoryImplCancellationTest {

    @Test
    fun `socket setup ENODEV reaches caller and releases socket and multicast lock`() = runBlocking {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager

        val socket = JoinFailureMdnsSocket()
        val lock = TrackingMulticastLock()
        val repository = MdnsRepositoryImpl(context).apply {
            socketFactory = { socket }
            multicastLockFactory = { lock }
        }

        val failure = runCatching {
            repository.discover(timeoutMs = 1_000).collect { }
        }.exceptionOrNull()

        assertEquals("setsockopt failed: ENODEV", failure?.message)
        assertTrue(socket.closed.get())
        assertFalse(lock.held.get())
        assertTrue(lock.released.get())
    }

    @Test
    fun `stopScan closes blocked repository receive and leaves no late error or completion`() = runBlocking {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager

        val socket = BlockingMdnsSocket()
        val lock = TrackingMulticastLock()
        val repository = MdnsRepositoryImpl(context).apply {
            socketFactory = { socket }
            multicastLockFactory = { lock }
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        var viewModel: MdnsDiscoveryViewModel? = null
        try {
            val activeViewModel = MdnsDiscoveryViewModel(MdnsDiscoveryUseCase(repository))
            viewModel = activeViewModel
            activeViewModel.startScan(timeoutMs = 60_000)

            withTimeout(2_000) {
                withContext(Dispatchers.IO) {
                    assertTrue(socket.receiveEntered.await(1, TimeUnit.SECONDS), "scan never entered receive")
                }
            }

            activeViewModel.stopScan()
            val stoppedState = activeViewModel.uiState.value

            withTimeout(2_000) {
                withContext(Dispatchers.IO) {
                    assertTrue(socket.closedSignal.await(1, TimeUnit.SECONDS), "stopScan did not close socket")
                    assertTrue(socket.receiveReturned.await(1, TimeUnit.SECONDS), "closed receive did not return")
                    assertTrue(lock.releasedSignal.await(1, TimeUnit.SECONDS), "stopScan did not release multicast lock")
                }
            }
            // Let the cancelled collection unwind through the real repository and ViewModel.
            delay(50)

            val state = activeViewModel.uiState.value
            assertEquals(stoppedState, state, "cancelled collection must not update UI after Stop")
            assertFalse(state.isScanning)
            assertFalse(state.scanComplete, "cancel must not emit DiscoveryComplete")
            assertEquals(null, state.error, "cancellation must not become a discovery error")
            assertTrue(state.services.isEmpty(), "zero-length packet after close must not emit a service")
            assertEquals(0, state.totalFound)
            assertTrue(socket.closed.get())
            assertFalse(lock.held.get())
            assertTrue(lock.released.get())
        } finally {
            socket.close()
            viewModel?.viewModelScope?.coroutineContext?.job?.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    private class TrackingMulticastLock : MdnsMulticastLock {
        val held = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        val releasedSignal = CountDownLatch(1)
        override fun setReferenceCounted(value: Boolean) = Unit
        override fun acquire() { held.set(true) }
        override val isHeld: Boolean get() = held.get()
        override fun release() {
            released.set(true)
            held.set(false)
            releasedSignal.countDown()
        }
    }

    private class BlockingMdnsSocket : MdnsSocket {
        val receiveEntered = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val closedSignal = CountDownLatch(1)
        val receiveReturned = CountDownLatch(1)
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override fun bind(address: InetSocketAddress) = Unit
        override fun joinGroup(address: InetAddress) = Unit
        override fun leaveGroup(address: InetAddress) = Unit
        override fun send(packet: DatagramPacket) = Unit
        override fun receive(packet: DatagramPacket) {
            receiveEntered.countDown()
            closedSignal.await(5, TimeUnit.SECONDS)
            // A socket may return a final empty datagram as it is being closed. The repository
            // must check cancellation before parsing or emitting anything from that receive.
            packet.length = 0
            receiveReturned.countDown()
        }
        override fun close() {
            closed.set(true)
            closedSignal.countDown()
        }
    }

    private class JoinFailureMdnsSocket : MdnsSocket {
        val closed = AtomicBoolean(false)
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override fun bind(address: InetSocketAddress) = Unit
        override fun joinGroup(address: InetAddress) {
            throw SocketException("setsockopt failed: ENODEV")
        }
        override fun leaveGroup(address: InetAddress) = Unit
        override fun send(packet: DatagramPacket) = Unit
        override fun receive(packet: DatagramPacket) = Unit
        override fun close() { closed.set(true) }
    }
}
