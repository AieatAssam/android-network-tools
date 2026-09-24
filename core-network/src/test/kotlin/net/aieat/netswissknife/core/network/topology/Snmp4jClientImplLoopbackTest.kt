package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.net.FakeNetworkBinder
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import org.snmp4j.CommandResponder
import org.snmp4j.CommandResponderEvent
import org.snmp4j.MessageDispatcherImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.PDU
import org.snmp4j.ScopedPDU
import org.snmp4j.Snmp
import org.snmp4j.mp.StatusInformation
import org.snmp4j.mp.MPv1
import org.snmp4j.mp.MPv2c
import org.snmp4j.mp.MPv3
import org.snmp4j.security.SecurityProtocols
import org.snmp4j.security.USM
import org.snmp4j.security.AuthHMAC192SHA256
import org.snmp4j.security.AuthHMAC384SHA512
import org.snmp4j.security.AuthMD5
import org.snmp4j.security.AuthSHA
import org.snmp4j.security.PrivAES192
import org.snmp4j.security.PrivAES128
import org.snmp4j.security.PrivAES256
import org.snmp4j.security.PrivDES
import org.snmp4j.smi.Address
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.OID
import org.snmp4j.smi.Null
import org.snmp4j.smi.VariableBinding
import org.snmp4j.smi.UdpAddress
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Snmp4jClientImplLoopbackTest {

    private val sysDescrOid = "1.3.6.1.2.1.1.1.0"

    @Test
    fun `selected SNMP transport binds its unbound socket before listen`() {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        val params = TopologyParams(targetIp = "192.168.1.7", timeoutMs = 100, retries = 0)

        val client = Snmp4jClientImpl(params, binder)
        try {
            assertEquals(1, binder.boundDatagramSockets.size)
            assertEquals(listOf(false), binder.datagramSocketBoundStatesAtBind)
            assertTrue(binder.boundDatagramSockets.single().isBound)
            assertTrue(binder.boundDatagramSockets.single().localPort > 0)
        } finally {
            client.close()
        }
    }

    @Test
    fun `SNMP transport maps bind permission denial to the typed exception`() {
        val delegate = FakeNetworkBinder(shouldBindResult = true)
        val binder = object : NetworkBinder by delegate {
            override fun bind(socket: DatagramSocket) {
                throw SecurityException("permission denied")
            }
        }
        val params = TopologyParams(targetIp = "192.168.1.7", timeoutMs = 100, retries = 0)

        val error = assertThrows(LocalNetworkPermissionDeniedException::class.java) {
            Snmp4jClientImpl(params, binder)
        }
        assertEquals("permission denied", error.cause?.message)
    }

    @Test
    fun `SNMP transport closes its socket when v3 client initialization fails`() {
        val binder = FakeNetworkBinder(shouldBindResult = true)
        val params = TopologyParams(
            targetIp = "192.168.1.7",
            snmpVersion = SnmpVersion.V3,
            v3Username = " ",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            Snmp4jClientImpl(params, binder)
        }

        assertEquals("SNMP v3 username must not be blank", error.message)
        assertEquals(1, binder.boundDatagramSockets.size)
        assertTrue(binder.boundDatagramSockets.single().isClosed)
    }

    @Test
    fun `v2c GET returns a responder value`() = runTest {
        val params = TopologyParams(
            targetIp = "127.0.0.1",
            snmpVersion = SnmpVersion.V2C,
            communityString = "public",
            timeoutMs = 1_000,
            retries = 0
        )

        withResponder(params) { port ->
            Snmp4jClientImpl(params).use { client ->
                val value = client.get(SnmpTarget("127.0.0.1", port, params), sysDescrOid)
                assertEquals("loopback-agent", value)
            }
        }
    }

    @Test
    fun `v2c wrong community reports an actionable failure`() = runTest {
        val params = TopologyParams(
            targetIp = "127.0.0.1",
            snmpVersion = SnmpVersion.V2C,
            communityString = "wrong-community",
            timeoutMs = 100,
            retries = 0
        )

        withResponder(params, responderCommunity = "public") { port ->
            Snmp4jClientImpl(params).use { client ->
                val error = assertThrows(SnmpRequestException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        client.get(SnmpTarget("127.0.0.1", port, params), sysDescrOid)
                    }
                }
                assertEquals(true, error.message!!.contains("community string"))
                assertEquals(true, error.message!!.contains("127.0.0.1:$port"))
            }
        }
    }

    @Test
    fun `v2c walk stops at its streaming row budget and reports truncation`() = runTest {
        val params = TopologyParams(
            targetIp = "127.0.0.1",
            snmpVersion = SnmpVersion.V2C,
            communityString = "public",
            timeoutMs = 1_000,
            retries = 0
        )
        val limits = TopologyResourceLimits(maxEntriesPerWalk = 3, maxBytesPerWalk = 16_384)
        val bulkRequests = AtomicInteger()

        withResponder(params, walkRowCount = 100, onBulkRequest = { bulkRequests.incrementAndGet() }) { port ->
            Snmp4jClientImpl(params).use { client ->
                val result = client.walk(
                    SnmpTarget("127.0.0.1", port, params),
                    "1.3.6.1.2.1.1.1",
                    SnmpWalkBudget(limits)
                )

                assertEquals(3, result.entries.size)
                assertEquals(setOf(TopologyTruncationReason.WALK_ENTRY_LIMIT), result.truncationReasons)
                assertEquals(1, bulkRequests.get())
            }
        }
    }

    @Test
    fun `v2c walk retains terminal page rows without claiming truncation`() = runTest {
        val params = TopologyParams(
            targetIp = "127.0.0.1",
            snmpVersion = SnmpVersion.V2C,
            timeoutMs = 1_000,
            retries = 0
        )
        val bulkRequests = AtomicInteger()
        val limits = TopologyResourceLimits(maxEntriesPerWalk = 10, maxBytesPerWalk = 16_384)

        withResponder(params, walkRowCount = 3, onBulkRequest = { bulkRequests.incrementAndGet() }) { port ->
            Snmp4jClientImpl(params).use { client ->
                val result = client.walk(
                    SnmpTarget("127.0.0.1", port, params),
                    "1.3.6.1.2.1.1.1",
                    SnmpWalkBudget(limits)
                )

                assertEquals(3, result.entries.size)
                assertEquals(setOf(
                    "1.3.6.1.2.1.1.1.1",
                    "1.3.6.1.2.1.1.1.2",
                    "1.3.6.1.2.1.1.1.3"
                ), result.entries.keys)
                assertTrue(result.truncationReasons.isEmpty())
                assertEquals(1, bulkRequests.get())
            }
        }
    }

    @Test
    fun `v2c walk cancellation returns without waiting for the SNMP timeout`() = runTest {
        val params = TopologyParams(
            targetIp = "192.0.2.1",
            snmpVersion = SnmpVersion.V2C,
            timeoutMs = 30_000,
            retries = 0
        )
        val client = Snmp4jClientImpl(params)
        try {
            assertThrows(TimeoutCancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    withTimeout(100) {
                        client.walk(
                            SnmpTarget(params.targetIp, params = params),
                            "1.3.6.1.2.1.1.1",
                            SnmpWalkBudget(TopologyResourceLimits())
                        )
                    }
                }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `v2c GET cancellation promptly cancels its pending SNMP request`() = kotlinx.coroutines.runBlocking {
        val params = TopologyParams(
            targetIp = "127.0.0.1",
            snmpVersion = SnmpVersion.V2C,
            communityString = "public",
            timeoutMs = 30_000,
            retries = 0
        )
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { silentAgent ->
            val requestReceived = CountDownLatch(1)
            val receiveThread = Thread {
                try {
                    val bytes = ByteArray(65_535)
                    silentAgent.receive(DatagramPacket(bytes, bytes.size))
                    requestReceived.countDown()
                } catch (_: Exception) {
                    // Closing the socket after the assertion releases this thread.
                }
            }.apply { isDaemon = true; start() }
            val client = Snmp4jClientImpl(params)
            val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val request = requestScope.async {
                client.get(SnmpTarget("127.0.0.1", silentAgent.localPort, params), sysDescrOid)
            }

            try {
                assertTrue(requestReceived.await(2, TimeUnit.SECONDS), "SNMP GET should reach the silent agent")
                assertEquals(1, client.pendingAsyncRequestCount)
                request.cancel()
                val cancelledPromptly = withTimeout(2_000) {
                    request.join()
                    request.isCancelled
                }
                assertTrue(cancelledPromptly, "cancel should remove the pending SNMP request without its 30s timeout")
                assertEquals(0, client.pendingAsyncRequestCount)
            } finally {
                client.close()
                requestScope.cancel()
                silentAgent.close()
                receiveThread.join(1_000)
            }
        }
    }

    @Test
    fun `unresolvable hostname reports target resolution details`() = runTest {
        val params = TopologyParams(
            targetIp = "does-not-exist.invalid",
            snmpVersion = SnmpVersion.V2C,
            communityString = "public",
            timeoutMs = 100,
            retries = 0
        )

        Snmp4jClientImpl(params).use { client ->
            val error = assertThrows(SnmpRequestException::class.java) {
                kotlinx.coroutines.runBlocking {
                    client.get(SnmpTarget(params.targetIp, 161, params), sysDescrOid)
                }
            }
            assertEquals(true, error.message!!.contains("could not resolve"))
            assertEquals(true, error.message!!.contains(params.targetIp))
        }
    }

    @Test
    fun `v3 authPriv GET returns a responder value`() = runTest {
        val params = TopologyParams(
            targetIp = "127.0.0.1",
            snmpVersion = SnmpVersion.V3,
            v3Username = "operator",
            v3AuthProtocol = V3AuthProtocol.SHA,
            v3AuthPassword = "auth-pass",
            v3PrivProtocol = V3PrivProtocol.AES128,
            v3PrivPassword = "priv-pass",
            timeoutMs = 1_000,
            retries = 0
        )

        withResponder(params) { port ->
            Snmp4jClientImpl(params).use { client ->
                val value = client.get(SnmpTarget("127.0.0.1", port, params), sysDescrOid)
                assertEquals("loopback-agent", value)
            }
        }
    }

    @Test
    fun `closed client rejects a subsequent request`() = runTest {
        val params = TopologyParams(targetIp = "127.0.0.1", timeoutMs = 100, retries = 0)
        val client = Snmp4jClientImpl(params)
        client.close()

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.get(SnmpTarget("127.0.0.1", 161, params), sysDescrOid)
            }
        }
    }

    private suspend fun withResponder(
        params: TopologyParams,
        responderCommunity: String = params.communityString,
        walkRowCount: Int = 0,
        onBulkRequest: (() -> Unit)? = null,
        block: suspend (port: Int) -> Unit
    ) {
        SecurityProtocols.getInstance().apply {
            addDefaultProtocols()
            addAuthenticationProtocol(AuthMD5())
            addAuthenticationProtocol(AuthSHA())
            addAuthenticationProtocol(AuthHMAC192SHA256())
            addAuthenticationProtocol(AuthHMAC384SHA512())
            addPrivacyProtocol(PrivDES())
            addPrivacyProtocol(PrivAES128())
            addPrivacyProtocol(PrivAES192())
            addPrivacyProtocol(PrivAES256())
        }
        val responderEngineId = OctetString(ByteArray(12) { 1 })
        val responderUsm = if (params.snmpVersion == SnmpVersion.V3) {
            USM(SecurityProtocols.getInstance(), responderEngineId, 0)
        } else {
            null
        }

        val transport = DefaultUdpTransportMapping(UdpAddress("127.0.0.1/0"))
        val dispatcher = MessageDispatcherImpl().apply {
            addMessageProcessingModel(MPv1())
            addMessageProcessingModel(MPv2c())
            if (params.snmpVersion == SnmpVersion.V3) {
                addMessageProcessingModel(MPv3(responderUsm!!))
            }
        }
        val responder = Snmp(dispatcher, transport)
        if (params.snmpVersion == SnmpVersion.V3) {
            responderUsm!!.addUser(
                UsmUserSpecFactory.from(params).toUsmUser(),
                responderEngineId
            )
        }
        var nextWalkRow = 1
        responder.addCommandResponder(object : CommandResponder {
            override fun <A : Address> processPdu(event: CommandResponderEvent<A>) {
                if (params.snmpVersion != SnmpVersion.V3 &&
                    String(event.securityName, Charsets.UTF_8) != responderCommunity
                ) {
                    return
                }
                val response = if (event.getPDU() is ScopedPDU) {
                    ScopedPDU(event.getPDU() as ScopedPDU)
                } else {
                    PDU(event.getPDU())
                }
                response.setType(PDU.RESPONSE)
                response.setErrorStatus(PDU.noError)
                response.setErrorIndex(0)
                if (walkRowCount > 0 && event.getPDU().type == PDU.GETBULK) {
                    onBulkRequest?.invoke()
                    response.clear()
                    response.requestID = event.getPDU().requestID
                    repeat(event.getPDU().maxRepetitions) {
                        if (nextWalkRow <= walkRowCount) {
                            response.add(
                                VariableBinding(
                                    OID("1.3.6.1.2.1.1.1.$nextWalkRow"),
                                    OctetString("row-$nextWalkRow")
                                )
                            )
                            nextWalkRow++
                        } else {
                            response.add(
                                VariableBinding(OID("1.3.6.1.2.1.2.0"), Null.endOfMibView)
                            )
                        }
                    }
                } else {
                    response.getAll().forEach { binding ->
                        binding.setVariable(OctetString("loopback-agent"))
                    }
                }
                event.getMessageDispatcher().returnResponsePdu(
                    event.getMessageProcessingModel(),
                    event.getSecurityModel(),
                    event.getSecurityName(),
                    event.getSecurityLevel(),
                response,
                    event.getMaxSizeResponsePDU(),
                    event.getStateReference(),
                    StatusInformation()
                )
            }
        })

        try {
            responder.listen()
            block((transport.listenAddress as UdpAddress).port)
        } finally {
            responder.close()
        }
    }
}
