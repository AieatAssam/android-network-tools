package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.UserTarget
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.security.*
import org.snmp4j.smi.*
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.DefaultPDUFactory
import org.snmp4j.util.TreeUtils

class Snmp4jClientImpl : SnmpClient {

    override suspend fun get(target: SnmpTarget, oid: String): String? =
        withContext(Dispatchers.IO) {
            val transport = DefaultUdpTransportMapping()
            val snmp = Snmp(transport)
            try {
                transport.listen()
                val snmpTarget = buildTarget(target)
                val pdu = PDU().apply {
                    type = PDU.GET
                    add(VariableBinding(OID(oid)))
                }
                val response = snmp.get(pdu, snmpTarget) ?: return@withContext null
                val vb = response.response?.getVariable(OID(oid)) ?: return@withContext null
                if (vb is Null) null else vb.toString()
            } finally {
                snmp.close()
            }
        }

    override suspend fun walk(target: SnmpTarget, oidPrefix: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val transport = DefaultUdpTransportMapping()
            val snmp = Snmp(transport)
            try {
                transport.listen()
                val snmpTarget = buildTarget(target)
                val results = mutableMapOf<String, String>()
                val treeUtils = TreeUtils(snmp, DefaultPDUFactory())
                val events = treeUtils.getSubtree(snmpTarget, OID(oidPrefix))
                events?.forEach { event ->
                    if (!event.isError) {
                        event.variableBindings?.forEach { vb ->
                            val value = vb.variable
                            if (value !is Null) {
                                results[vb.oid.toString()] = value.toString()
                            }
                        }
                    }
                }
                results
            } finally {
                snmp.close()
            }
        }

    private fun buildTarget(target: SnmpTarget): org.snmp4j.Target<*> {
        val params = target.params
        val address = UdpAddress(
            java.net.InetAddress.getByName(target.ip),
            target.port
        )
        return when (params.snmpVersion) {
            SnmpVersion.V1 -> CommunityTarget<UdpAddress>(
                address,
                OctetString(params.communityString)
            ).apply {
                version = SnmpConstants.version1
                timeout = params.timeoutMs.toLong()
                retries = params.retries
            }
            SnmpVersion.V2C -> CommunityTarget<UdpAddress>(
                address,
                OctetString(params.communityString)
            ).apply {
                version = SnmpConstants.version2c
                timeout = params.timeoutMs.toLong()
                retries = params.retries
            }
            SnmpVersion.V3 -> {
                val securityName = OctetString(params.v3Username ?: "")
                val secLevel = buildV3SecurityLevel(params)
                UserTarget<UdpAddress>().apply {
                    this.address = address
                    version = SnmpConstants.version3
                    timeout = params.timeoutMs.toLong()
                    retries = params.retries
                    this.securityName = securityName
                    this.securityLevel = secLevel
                }
            }
        }
    }

    private fun buildV3SecurityLevel(params: TopologyParams): Int {
        return when {
            params.v3AuthProtocol != V3AuthProtocol.NONE && params.v3PrivProtocol != V3PrivProtocol.NONE ->
                SecurityLevel.AUTH_PRIV
            params.v3AuthProtocol != V3AuthProtocol.NONE ->
                SecurityLevel.AUTH_NOPRIV
            else ->
                SecurityLevel.NOAUTH_NOPRIV
        }
    }
}
