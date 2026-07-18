package net.aieat.netswissknife.core.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket

class WakeOnLanRepositoryImplTest {

    private val repository = WakeOnLanRepositoryImpl()

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
    fun `returns Error for unresolvable broadcast address`() = runBlocking {
        val result = repository.sendMagicPacket(
            macAddress = "01:02:03:04:05:06",
            broadcastAddress = "definitely-not-a-real-host.invalid",
            port = 9,
        )
        assertTrue(result is NetworkResult.Error)
    }
}
