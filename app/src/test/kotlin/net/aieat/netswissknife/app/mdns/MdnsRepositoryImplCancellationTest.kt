package net.aieat.netswissknife.app.mdns

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import net.aieat.netswissknife.core.network.mdns.MdnsOperation
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class MdnsRepositoryImplCancellationTest {

    @Test
    fun `socket setup ENODEV reaches caller and releases socket and multicast lock`() = runBlocking {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager

        val cleanupOrder = Collections.synchronizedList(mutableListOf<String>())
        val socket = JoinFailureMdnsSocket(cleanupOrder)
        val lock = TrackingMulticastLock(cleanupOrder)
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
        assertEquals(0, socket.leaveCount.get(), "failed join must not be treated as acquired membership")
        assertEquals(1, socket.closeCount.get())
        assertEquals(listOf("socket-close", "lock-release"), cleanupOrder)
    }

    @Test
    fun `stopScan closes blocked repository receive and leaves no late error or completion`() = runBlocking {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager

        val cleanupOrder = Collections.synchronizedList(mutableListOf<String>())
        val socket = BlockingMdnsSocket(cleanupOrder)
        val lock = TrackingMulticastLock(cleanupOrder)
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
            assertFalse(state.isScanning)
            assertFalse(state.isCanceling)
            assertTrue(state.scanCanceled)
            assertFalse(state.scanComplete, "cancel must not emit DiscoveryComplete")
            assertEquals(null, state.error, "cancellation must not become a discovery error")
            assertTrue(state.services.isEmpty(), "zero-length packet after close must not emit a service")
            assertEquals(0, state.totalFound)
            assertTrue(socket.closed.get())
            assertFalse(lock.held.get())
            assertTrue(lock.released.get())
            assertEquals(0, socket.leaveCount.get())
            assertEquals(1, socket.closeCount.get())
            assertEquals(1, lock.releaseCount.get())
            assertEquals(listOf("socket-close", "lock-release"), cleanupOrder)
        } finally {
            socket.close()
            viewModel?.viewModelScope?.coroutineContext?.job?.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `stop closes socket without waiting for a blocked join`() = runBlocking {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager

        val cleanupOrder = Collections.synchronizedList(mutableListOf<String>())
        val socket = BlockingJoinMdnsSocket(cleanupOrder)
        val lock = TrackingMulticastLock(cleanupOrder)
        val repository = MdnsRepositoryImpl(context).apply {
            socketFactory = { socket }
            multicastLockFactory = { lock }
        }
        val session = MdnsOperation.newSession()
        val scan = async(Dispatchers.IO) {
            runCatching { repository.discover(5_000L, session).collect { } }
        }

        withTimeout(2_000) {
            withContext(Dispatchers.IO) {
                assertTrue(socket.joinEntered.await(1, TimeUnit.SECONDS), "scan never entered joinGroup")
            }
        }
        withTimeout(2_000) {
            withContext(Dispatchers.IO) { session.cancel(CancellationReason.USER_STOP) }
        }
        val scanResult = withTimeout(2_000) { scan.await() }
        val cancellation = scanResult.exceptionOrNull()

        assertTrue(
            cancellation is OperationCancellationException,
            "unexpected result after blocked join was released: $scanResult",
        )
        assertEquals(CancellationReason.USER_STOP, (cancellation as OperationCancellationException).reason)
        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertEquals(1, socket.closeCount.get())
        assertEquals(0, socket.leaveCount.get())
        assertEquals(1, lock.releaseCount.get())
        assertEquals(listOf("socket-close", "lock-release"), cleanupOrder)
    }

    @Test
    fun `deadline with resource close failure does not report successful completion`() = runBlocking {
        val context = mockk<Context>()
        val wifiManager = mockk<WifiManager>()
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        val cleanupOrder = Collections.synchronizedList(mutableListOf<String>())
        val socket = BlockingCloseFailureMdnsSocket(cleanupOrder)
        val lock = TrackingMulticastLock(cleanupOrder)
        val repository = MdnsRepositoryImpl(context).apply {
            socketFactory = { socket }
            multicastLockFactory = { lock }
        }
        val session = MdnsOperation.newSession(500L)
        val updates = mutableListOf<net.aieat.netswissknife.core.network.mdns.MdnsUpdate>()

        val failure = withTimeout(2_000) {
            runCatching { repository.discover(500L, session).collect { updates += it } }.exceptionOrNull()
        }

        assertTrue(
            failure is OperationDeadlineExceededException || failure is OperationCancellationException,
            "Expected typed deadline, got ${failure?.javaClass?.name}: ${failure?.message}; reason=${session.cancellationReason}",
        )
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertTrue(
            failure!!.containsFailure("socket close failed"),
            "Close failure missing from chain: ${failure.describeFailureChain()}; closeCount=${socket.closeCount.get()}",
        )
        assertTrue(updates.none { it is net.aieat.netswissknife.core.network.mdns.MdnsUpdate.DiscoveryComplete })
        assertEquals(1, socket.closeCount.get())
        assertFalse(lock.held.get())
        assertTrue(lock.released.get())
    }

    private class TrackingMulticastLock(
        private val cleanupOrder: MutableList<String> = Collections.synchronizedList(mutableListOf())
    ) : MdnsMulticastLock {
        val held = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        val releaseCount = AtomicInteger()
        val releasedSignal = CountDownLatch(1)
        override fun setReferenceCounted(value: Boolean) = Unit
        override fun acquire() { held.set(true) }
        override val isHeld: Boolean get() = held.get()
        override fun release() {
            releaseCount.incrementAndGet()
            released.set(true)
            held.set(false)
            cleanupOrder += "lock-release"
            releasedSignal.countDown()
        }
    }

    private class BlockingMdnsSocket(
        private val cleanupOrder: MutableList<String>,
    ) : MdnsSocket {
        val receiveEntered = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val closeCount = AtomicInteger()
        val leaveCount = AtomicInteger()
        val closedSignal = CountDownLatch(1)
        val receiveReturned = CountDownLatch(1)
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override fun bind(address: InetSocketAddress) = Unit
        override fun joinGroup(address: InetAddress) = Unit
        override fun leaveGroup(address: InetAddress) {
            leaveCount.incrementAndGet()
            cleanupOrder += "leave"
        }
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
            closeCount.incrementAndGet()
            closed.set(true)
            cleanupOrder += "socket-close"
            closedSignal.countDown()
        }
    }

    private class JoinFailureMdnsSocket(
        private val cleanupOrder: MutableList<String>,
    ) : MdnsSocket {
        val closed = AtomicBoolean(false)
        val leaveCount = AtomicInteger()
        val closeCount = AtomicInteger()
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override fun bind(address: InetSocketAddress) = Unit
        override fun joinGroup(address: InetAddress) {
            throw SocketException("setsockopt failed: ENODEV")
        }
        override fun leaveGroup(address: InetAddress) { leaveCount.incrementAndGet() }
        override fun send(packet: DatagramPacket) = Unit
        override fun receive(packet: DatagramPacket) = Unit
        override fun close() {
            closeCount.incrementAndGet()
            closed.set(true)
            cleanupOrder += "socket-close"
        }
    }

    private class BlockingJoinMdnsSocket(
        private val cleanupOrder: MutableList<String>,
    ) : MdnsSocket {
        val joinEntered = CountDownLatch(1)
        private val closedSignal = CountDownLatch(1)
        val closeCount = AtomicInteger()
        val leaveCount = AtomicInteger()
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override fun bind(address: InetSocketAddress) = Unit
        override fun joinGroup(address: InetAddress) = blockedJoin()
        override fun joinGroup(address: InetSocketAddress, networkInterface: java.net.NetworkInterface?) = blockedJoin()
        private fun blockedJoin() {
            joinEntered.countDown()
            closedSignal.await()
            throw SocketException("closed during join")
        }
        override fun leaveGroup(address: InetAddress) { leaveCount.incrementAndGet() }
        override fun send(packet: DatagramPacket) = Unit
        override fun receive(packet: DatagramPacket) = Unit
        override fun close() {
            closeCount.incrementAndGet()
            cleanupOrder += "socket-close"
            closedSignal.countDown()
        }
    }

    private class BlockingCloseFailureMdnsSocket(
        private val cleanupOrder: MutableList<String>,
    ) : MdnsSocket {
        private val closedSignal = CountDownLatch(1)
        val closeCount = AtomicInteger()
        override var reuseAddress: Boolean = false
        override var soTimeout: Int = 0
        override fun bind(address: InetSocketAddress) = Unit
        override fun joinGroup(address: InetAddress) = Unit
        override fun leaveGroup(address: InetAddress) = Unit
        override fun send(packet: DatagramPacket) = Unit
        override fun receive(packet: DatagramPacket) {
            closedSignal.await(2, TimeUnit.SECONDS)
            throw SocketException("socket closed by deadline")
        }
        override fun close() {
            closeCount.incrementAndGet()
            cleanupOrder += "socket-close"
            closedSignal.countDown()
            throw SocketException("socket close failed")
        }
    }

    private fun Throwable.containsFailure(message: String): Boolean =
        this.message == message || cause?.containsFailure(message) == true ||
            suppressed.any { it.containsFailure(message) }

    private fun Throwable.describeFailureChain(): String =
        buildString {
            append(javaClass.simpleName).append(':').append(message)
            cause?.let { append(" caused by [").append(it.describeFailureChain()).append(']') }
            suppressed.forEach { append(" suppressed [").append(it.describeFailureChain()).append(']') }
        }
}
