package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UdpExchangeTest {
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
