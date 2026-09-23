package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UdpExchangeTest {
    @Test
    fun `LAN UDP exchange binds multicast probes to the selected network`() = runTest {
        val binder = FakeNetworkBinder(shouldBindResult = false)
        val socket = object : DatagramSocket(null as java.net.SocketAddress?) {
            override fun send(packet: DatagramPacket) = Unit
            override fun receive(packet: DatagramPacket) {
                throw SocketException("test complete")
            }
        }
        val exchange = NetworkBoundUdpExchange(
            binder = binder,
            socketFactory = { socket },
            bindMulticastDestinations = true,
        )

        withContext(Dispatchers.IO) {
            exchange.exchangeCorrelated(
                ip = "224.0.0.251",
                port = 5353,
                payload = byteArrayOf(0x01),
                timeoutMs = 100,
                accepts = { true },
            )
        }

        assertEquals(listOf(socket), binder.boundDatagramSockets)
        assertEquals(listOf(false), binder.datagramSocketBoundStatesAtBind)
    }

    @Test
    fun `UDP exchange maps permission denial to the typed local network error`() = runTest {
        val delegate = FakeNetworkBinder(shouldBindResult = true)
        val binder = object : NetworkBinder by delegate {
            override fun bind(socket: DatagramSocket) {
                throw SecurityException("permission denied")
            }
        }
        val exchange = NetworkBoundUdpExchange(binder)

        var failure: LocalNetworkPermissionDeniedException? = null
        withContext(Dispatchers.IO) {
            try {
                exchange.exchangeCorrelated(
                    ip = "192.168.1.7",
                    port = 137,
                    payload = byteArrayOf(0x01),
                    timeoutMs = 100,
                    accepts = { true },
                )
            } catch (error: LocalNetworkPermissionDeniedException) {
                failure = error
            }
        }

        assertEquals("permission denied", failure?.cause?.message)
    }

    @Test
    fun `UDP exchange binds an unbound local socket before sending`() = runTest {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            val responder = async(Dispatchers.IO) {
                val request = DatagramPacket(ByteArray(8), 8)
                server.receive(request)
                val response = byteArrayOf(0x2A)
                server.send(DatagramPacket(response, response.size, request.address, request.port))
            }
            val binder = FakeNetworkBinder(shouldBindResult = true)
            val exchange = NetworkBoundUdpExchange(binder)

            val response = withContext(Dispatchers.IO) {
                exchange.exchangeCorrelated(
                    ip = "127.0.0.1",
                    port = server.localPort,
                    payload = byteArrayOf(0x01),
                    timeoutMs = 1_000,
                    accepts = { true },
                )
            }

            assertEquals(listOf(0x2A.toByte()), response?.payload?.toList())
            assertEquals(1, binder.boundDatagramSockets.size)
            assertEquals(listOf(false), binder.datagramSocketBoundStatesAtBind)
            assertTrue(binder.boundDatagramSockets.single().isBound)
            responder.await()
        }
    }

    @Test
    fun `ignores unrelated datagram and accepts a later correlated reply`() = runTest {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            val responder = async(Dispatchers.IO) {
                val request = DatagramPacket(ByteArray(8), 8)
                server.receive(request)
                listOf(byteArrayOf(0x7F), byteArrayOf(0x2A)).forEach { bytes ->
                    server.send(DatagramPacket(bytes, bytes.size, request.address, request.port))
                }
            }

            val response = withContext(Dispatchers.IO) {
                DefaultUdpExchange.exchangeCorrelated(
                    ip = "127.0.0.1",
                    port = server.localPort,
                    payload = byteArrayOf(0x01),
                    timeoutMs = 1_000,
                    accepts = { it.payload.contentEquals(byteArrayOf(0x2A)) },
                )
            }

            assertEquals(listOf(0x2A.toByte()), response?.payload?.toList())
            responder.await()
        }
    }

    @Test
    fun `correlated receive observes cancellation before its deadline`() = runTest {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 2_000
            val requestReceived = CountDownLatch(1)
            val receiver = launch(Dispatchers.IO) {
                val request = DatagramPacket(ByteArray(8), 8)
                server.receive(request)
                requestReceived.countDown()
            }
            val exchange = async(Dispatchers.IO) {
                DefaultUdpExchange.exchangeCorrelated(
                    ip = "127.0.0.1",
                    port = server.localPort,
                    payload = byteArrayOf(0x01),
                    timeoutMs = 5_000,
                    accepts = { true },
                )
            }

            assertTrue(requestReceived.await(2, TimeUnit.SECONDS))
            exchange.cancelAndJoin()
            receiver.cancelAndJoin()
            assertTrue(exchange.isCancelled)
        }
    }
}
