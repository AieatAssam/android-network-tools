package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.test.runTest
import org.snmp4j.CommandResponder
import org.snmp4j.CommandResponderEvent
import org.snmp4j.MessageDispatcherImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
import org.snmp4j.smi.UdpAddress
import org.snmp4j.transport.DefaultUdpTransportMapping

class Snmp4jClientImplLoopbackTest {

    private val sysDescrOid = "1.3.6.1.2.1.1.1.0"

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
                response.getAll().forEach { binding ->
                    binding.setVariable(OctetString("loopback-agent"))
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
