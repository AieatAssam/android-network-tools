package net.aieat.netswissknife.core.network.tls

import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import net.aieat.netswissknife.core.network.net.LocalNetworkBindingUnavailableException
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.containsLocalNetworkPermissionDenied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import javax.net.ssl.SSLSocket

class TlsNetworkBindingTest {
    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    private fun engine(binder: FakeNetworkBinder) = SocketTlsHandshakeEngine(
        socketFactory = TlsInspectorSocketFactory { context ->
            context.socketFactory.createSocket() as SSLSocket
        },
        networkBinder = binder,
    )

    @Test
    fun `TLS binds local destination before connect`() {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        val connection = engine(binder).openConnection("127.0.0.1", unusedPort(), 500)

        try {
            assertThrows(Exception::class.java) { connection.connect() }
            assertEquals(1, binder.boundTcpSockets.size)
            assertEquals(listOf(false), binder.tcpSocketConnectedStatesAtBind)
            assertFalse(binder.boundTcpSockets.single().isConnected)
        } finally {
            connection.close()
        }
    }

    @Test
    fun `TLS public destination bypasses local network binding`() {
        val binder = FakeNetworkBinder(shouldBindResult = false)
        val connection = engine(binder).openConnection("127.0.0.1", unusedPort(), 500)

        try {
            assertThrows(Exception::class.java) { connection.connect() }
            assertEquals(0, binder.boundTcpSockets.size)
        } finally {
            connection.close()
        }
    }

    @Test
    fun `TLS preserves typed local permission denial from socket binding`() {
        val binder = FakeNetworkBinder(
            shouldBindResult = true,
            throwTcpBindSecurityException = true,
        )
        val connection = engine(binder).openConnection("127.0.0.1", unusedPort(), 500)

        try {
            val failure = assertThrows(LocalNetworkPermissionDeniedException::class.java) {
                connection.connect()
            }
            assertTrue(failure.containsLocalNetworkPermissionDenied())
            assertEquals(listOf(false), binder.tcpSocketConnectedStatesAtBind)
        } finally {
            connection.close()
        }
    }

    @Test
    fun `TLS fails closed if selected local network disappears before bind`() {
        val binder = FakeNetworkBinder(
            shouldBindResult = true,
            tcpBindIfLocalReturnsFalse = true,
        )
        val connection = engine(binder).openConnection("127.0.0.1", unusedPort(), 500)

        try {
            assertThrows(LocalNetworkBindingUnavailableException::class.java) {
                connection.connect()
            }
            assertEquals(0, binder.boundTcpSockets.size)
            assertEquals(listOf("127.0.0.1"), binder.atomicBindDestinations)
        } finally {
            connection.close()
        }
    }
}
