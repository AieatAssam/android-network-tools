package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class SocketTcpPresenceProbeTest {
    @Test
    fun `open connector returns open port`() = runTest {
        val result = SocketTcpPresenceProbe { _, port, _ ->
            if (port == 8080) TcpConnectOutcome.OPEN else TcpConnectOutcome.NONE
        }.probe("127.0.0.1", listOf(8080, 8081), 1000)
        assertEquals(TcpPresence.Open(8080), result)
    }

    @Test
    fun `closed connector maps to refused`() = runTest {
        val result = SocketTcpPresenceProbe { _, _, _ -> TcpConnectOutcome.REFUSED }
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
    fun `probe stops at first presence result`() = runTest {
        val calls = mutableListOf<Int>()
        val result = SocketTcpPresenceProbe { _, port, _ ->
            calls += port
            if (port == 80) TcpConnectOutcome.REFUSED else TcpConnectOutcome.NONE
        }.probe("127.0.0.1", listOf(80, 443), 1000)
        assertEquals(TcpPresence.Refused(80), result)
        assertEquals(listOf(80), calls)
    }
}
