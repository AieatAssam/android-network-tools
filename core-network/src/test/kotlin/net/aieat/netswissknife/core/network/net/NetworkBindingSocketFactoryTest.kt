package net.aieat.netswissknife.core.network.net

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkBindingSocketFactoryTest {
    @Test
    fun `local destination binds the socket before connect`() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { server ->
            val binder = FakeNetworkBinder(shouldBindResult = true)

            NetworkBindingSocketFactory(binder).createSocket(loopback, server.localPort).use { socket ->
                assertTrue(socket.isConnected)
                assertEquals(1, binder.boundTcpSockets.size)
                assertEquals(listOf(false), binder.tcpSocketConnectedStatesAtBind)
                assertTrue(server.accept().use { it.isConnected })
            }
        }
    }

    @Test
    fun `default route destination is not bound to selected network`() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { server ->
            val binder = FakeNetworkBinder(shouldBindResult = false)

            NetworkBindingSocketFactory(binder).createSocket(loopback, server.localPort).use { socket ->
                assertTrue(socket.isConnected)
                assertEquals(0, binder.boundTcpSockets.size)
                assertEquals(0, binder.atomicBindDestinations.size)
                assertTrue(server.accept().use { it.isConnected })
            }
        }
    }
}
