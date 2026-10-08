package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class SocketTcpPresenceProbeTest {
    @Test
    fun `open connector returns open port`() = runTest {
        val result = SocketTcpPresenceProbe { _, port, _ ->
            TcpConnectResult(if (port == 8080) TcpConnectOutcome.OPEN else TcpConnectOutcome.NONE)
        }.probe("127.0.0.1", listOf(8080, 8081), 1000)
        assertEquals(TcpPresence.Open(8080), result)
    }

    @Test
    fun `closed connector maps to refused`() = runTest {
        val result = SocketTcpPresenceProbe { _, _, _ -> TcpConnectResult(TcpConnectOutcome.REFUSED) }
            .probe("127.0.0.1", listOf(54321), 1000)
        assertEquals(TcpPresence.Refused(54321), result)
    }

    @Test
    fun `real open socket is detected`() = runTest {
        ServerSocket(0).use { server ->
            val result = SocketTcpPresenceProbe().probe(
                "127.0.0.1",
                listOf(server.localPort),
                1000,
            )
            assertEquals(TcpPresence.Open(server.localPort), result)
        }
    }

    @Test
    fun `probe continues after refusal to find positive evidence`() = runTest {
        val calls = mutableListOf<Int>()
        val result = SocketTcpPresenceProbe { _, port, _ ->
            calls += port
            TcpConnectResult(if (port == 80) TcpConnectOutcome.REFUSED else TcpConnectOutcome.OPEN)
        }.probe("127.0.0.1", listOf(80, 443), 1000)
        assertEquals(TcpPresence.Open(443), result)
        assertEquals(listOf(80, 443), calls)
    }

    @Test
    fun `multiple refused ports remain a non-positive outcome`() = runTest {
        val calls = mutableListOf<Int>()
        val result = SocketTcpPresenceProbe { _, port, _ ->
            calls += port
            TcpConnectResult(TcpConnectOutcome.REFUSED)
        }.probe("127.0.0.1", listOf(80, 443), 1000)

        assertEquals(TcpPresence.Refused(80), result)
        assertEquals(listOf(80, 443), calls)
    }

    @Test
    fun `connect exception is uncertain and retains its cause`() = runTest {
        val server = ServerSocket(0)
        val closedPort = server.localPort
        server.close()

        val result = SocketTcpPresenceProbe().probe("127.0.0.1", listOf(closedPort), 1000)

        assertEquals(true, result is TcpPresence.UnknownFailure)
        assertEquals(true, (result as TcpPresence.UnknownFailure).detail?.startsWith("ConnectException") == true)
    }
}
