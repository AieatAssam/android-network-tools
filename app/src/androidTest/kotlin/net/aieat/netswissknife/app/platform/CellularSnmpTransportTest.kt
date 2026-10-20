package net.aieat.netswissknife.app.platform

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.topology.Snmp4jClientImpl
import net.aieat.netswissknife.core.network.topology.SnmpTarget
import net.aieat.netswissknife.core.network.topology.TopologyParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android Network.bindSocket path with only a cellular local route. */
@RunWith(AndroidJUnit4::class)
class CellularSnmpTransportTest {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            GrantPermissionRule.grant(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            GrantPermissionRule.grant()
        }

    @Test
    fun cellularBoundSnmpTransport_sendsRequestThroughSelectedNetwork() = runBlocking {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testArguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val requestedTargetIp = testArguments.getString("snmpTargetIp")
        val requestedTargetPort = testArguments.getString("snmpTargetPort")?.toIntOrNull()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val networks = connectivityManager.allNetworks.toList()
        val localIpv4Networks = networks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val properties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            val address = properties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return@mapNotNull null
            Triple(network, capabilities, address)
        }
        val cellular = localIpv4Networks.firstOrNull { (_, capabilities, _) ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        assumeTrue("No cellular IPv4 route is available on this device", cellular != null)
        assumeFalse(
            "Disable Wi-Fi/Ethernet to exercise the cellular-bound transport",
            localIpv4Networks.any { (_, capabilities, _) ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            },
        )

        val (_, _, linkAddress) = checkNotNull(cellular)
        val cellularAddress = linkAddress.address as Inet4Address
        val cellularAddressText = cellularAddress.hostAddress ?: error("Cellular IPv4 address has no host value")
        val destinationIp = requestedTargetIp ?: cellularAddressText
        val binder = AndroidNetworkBinder(connectivityManager)
        assertTrue("Selected local network should be available", binder.isAvailable)
        assertTrue("Cellular destination should be routed through the bound network", binder.shouldBind(destinationIp))

        val receiver = DatagramSocket(null as SocketAddress?).apply {
            reuseAddress = true
            bind(InetSocketAddress(cellularAddress, 0))
        }
        val received = CountDownLatch(1)
        val receivedLength = AtomicInteger()
        val receiveThread = Thread {
            try {
                val packet = DatagramPacket(ByteArray(4_096), 4_096)
                receiver.receive(packet)
                receivedLength.set(packet.length)
                received.countDown()
            } catch (_: SocketException) {
                received.countDown()
            }
        }.apply {
            name = "cellular-snmp-test-receiver"
            isDaemon = true
            start()
        }
        val params = TopologyParams(
            targetIp = destinationIp,
            timeoutMs = 1_000,
            retries = 0,
        )
        val client = Snmp4jClientImpl(
            sessionParams = params,
            binder = binder,
            operationDeadline = OperationBudget.start(timeoutMillis = 10_000).deadline,
            deferInitialization = true,
        )

        try {
            val failure = runCatching {
                client.get(
                    SnmpTarget(destinationIp, requestedTargetPort ?: receiver.localPort, params),
                    "1.3.6.1.2.1.1.1.0",
                )
            }.exceptionOrNull()
            if (requestedTargetIp == null) {
                assertTrue("The receiver should observe the SNMP request", received.await(5, TimeUnit.SECONDS))
                assertTrue("The emitted SNMP request must contain a datagram", receivedLength.get() > 0)
            }
            assertNotNull("A silent test receiver should produce an SNMP response timeout", failure)
            assertFalse(
                "The timeout must be at the SNMP request phase, not transport initialization",
                failure?.message.orEmpty().contains("transport initialization timed out"),
            )
        } finally {
            client.close()
            receiver.close()
            receiveThread.join(1_000)
        }
    }
}
