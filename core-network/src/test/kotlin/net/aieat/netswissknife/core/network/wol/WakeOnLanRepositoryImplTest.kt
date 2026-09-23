package net.aieat.netswissknife.core.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WakeOnLanRepositoryImplTest {

    private val repository = WakeOnLanRepositoryImpl()

    @Test
    fun `operation policy requires local network and has a bounded deadline`() {
        val session = WakeOnLanOperation.newSession()

        assertEquals(OperationRequirement.LOCAL_NETWORK, session.budget.requirement)
        assertEquals(WakeOnLanOperation.TIMEOUT_MILLIS, session.budget.deadline.timeoutNanos / 1_000_000)
    }

    @Test
    fun `sends magic packet datagrams that a listener receives`() = runBlocking {
        DatagramSocket(0).use { listener ->
            listener.soTimeout = 5_000
            val port = listener.localPort

            val received = async(Dispatchers.IO) {
                val buffer = ByteArray(200)
                val datagram = DatagramPacket(buffer, buffer.size)
                listener.receive(datagram)
                buffer.copyOfRange(0, datagram.length)
            }

            val result = withTimeout(10_000) {
                repository.sendMagicPacket(
                    macAddress = "01:02:03:04:05:06",
                    broadcastAddress = "127.0.0.1",
                    port = port,
                    repeatCount = 3,
                )
            }

            val payload = withContext(Dispatchers.IO) { withTimeout(10_000) { received.await() } }

            assertTrue(result is NetworkResult.Success, "expected Success, got $result")
            result as NetworkResult.Success
            assertEquals("01:02:03:04:05:06".uppercase(), result.data.macAddress)
            assertEquals(3, result.data.packetsSent)
            assertEquals(port, result.data.port)

            assertArrayEquals(WolMagicPacket.build("01:02:03:04:05:06"), payload)
        }
    }

    @Test
    fun `returns Error for invalid MAC`() = runBlocking {
        val result = repository.sendMagicPacket("not-a-mac", "127.0.0.1", 9)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `rejects hostnames without creating a socket or invoking DNS`() = runBlocking {
        val socketCreations = AtomicInteger()
        val repo = WakeOnLanRepositoryImpl(socketFactory = {
            socketCreations.incrementAndGet()
            DatagramSocket(null as SocketAddress?)
        })

        val result = repo.sendMagicPacket(
            macAddress = "01:02:03:04:05:06",
            broadcastAddress = "definitely-not-a-real-host.invalid",
            port = 9,
        )

        assertTrue(result is NetworkResult.Error)
        assertEquals("Broadcast address must be an IPv4 address", (result as NetworkResult.Error).message)
        assertEquals(0, socketCreations.get())
    }

    @Test
    fun `binds a selected local socket before sending the magic packet`() = runBlocking {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        val boundAtSend = mutableListOf<Boolean>()
        val closes = AtomicInteger()
        val socket = object : DatagramSocket(null as SocketAddress?) {
            override fun send(packet: DatagramPacket) {
                boundAtSend += binder.boundDatagramSockets.singleOrNull() === this
            }

            override fun close() {
                closes.incrementAndGet()
                super.close()
            }
        }
        val repo = WakeOnLanRepositoryImpl(binder = binder, socketFactory = { socket })

        val result = repo.sendMagicPacket("01:02:03:04:05:06", "192.168.1.255", port = 9, repeatCount = 1)

        assertTrue(result is NetworkResult.Success, "expected Success, got $result")
        assertEquals(listOf(true), boundAtSend)
        assertEquals(listOf(socket), binder.boundDatagramSockets)
        assertEquals(listOf(false), binder.datagramSocketBoundStatesAtBind)
        assertTrue(socket.isBound)
        assertEquals(1, closes.get(), "the operation scope must close the socket exactly once")
    }

    @Test
    fun `user stop closes a blocked send once and remains typed cancellation`() = runBlocking {
        val enteredSend = CountDownLatch(1)
        val releasedByClose = CountDownLatch(1)
        val closes = AtomicInteger()
        val socket = object : DatagramSocket(null as SocketAddress?) {
            override fun send(packet: DatagramPacket) {
                enteredSend.countDown()
                check(releasedByClose.await(3, TimeUnit.SECONDS)) { "socket close did not release send" }
            }

            override fun close() {
                closes.incrementAndGet()
                releasedByClose.countDown()
                super.close()
            }
        }
        val repo = WakeOnLanRepositoryImpl(socketFactory = { socket })
        val session = OperationSession(OperationBudget.start(timeoutMillis = 5_000))
        val pending = async {
            repo.sendMagicPacket("01:02:03:04:05:06", "127.0.0.1", 9, 1, session)
        }

        assertTrue(withContext(Dispatchers.IO) { enteredSend.await(3, TimeUnit.SECONDS) })
        session.cancel(CancellationReason.USER_STOP)
        val failure = runCatching { pending.await() }.exceptionOrNull()

        assertTrue(failure is OperationCancellationException, "expected typed cancellation, got $failure")
        assertEquals(CancellationReason.USER_STOP, (failure as OperationCancellationException).reason)
        assertEquals(1, closes.get())
        assertTrue(releasedByClose.count == 0L)
    }

    @Test
    fun `deadline closes blocked send and cannot return late success`() = runBlocking {
        val enteredSend = CountDownLatch(1)
        val releasedByClose = CountDownLatch(1)
        val closes = AtomicInteger()
        val socket = object : DatagramSocket(null as SocketAddress?) {
            override fun send(packet: DatagramPacket) {
                enteredSend.countDown()
                check(releasedByClose.await(3, TimeUnit.SECONDS)) { "deadline did not close socket" }
                // Simulate a native send that returns successfully after cancellation closed it.
            }

            override fun close() {
                closes.incrementAndGet()
                releasedByClose.countDown()
                super.close()
            }
        }
        val repo = WakeOnLanRepositoryImpl(socketFactory = { socket })
        val session = OperationSession(OperationBudget.start(timeoutMillis = 1_000))
        val pending = async {
            repo.sendMagicPacket("01:02:03:04:05:06", "127.0.0.1", 9, 1, session)
        }

        assertTrue(withContext(Dispatchers.IO) { enteredSend.await(3, TimeUnit.SECONDS) })
        val result = withTimeout(3_000) { pending.await() }

        assertTrue(result is NetworkResult.Error, "deadline must not return success: $result")
        assertEquals("Wake-on-LAN send timed out", (result as NetworkResult.Error).message)
        assertEquals(1, closes.get())
    }

    @Test
    fun `maps local socket permission denial to a typed error cause`() = runBlocking {
        val delegate = FakeNetworkBinder(shouldBindResult = true)
        val binder = object : NetworkBinder by delegate {
            override fun bind(socket: DatagramSocket) {
                throw SecurityException("permission denied")
            }
        }
        val socket = DatagramSocket(null as SocketAddress?)
        val repo = WakeOnLanRepositoryImpl(binder = binder, socketFactory = { socket })

        val result = repo.sendMagicPacket("01:02:03:04:05:06", "192.168.1.255", port = 9)

        assertTrue(result is NetworkResult.Error)
        result as NetworkResult.Error
        assertTrue(result.cause is LocalNetworkPermissionDeniedException)
        assertEquals("permission denied", result.cause?.cause?.message)
    }
}
