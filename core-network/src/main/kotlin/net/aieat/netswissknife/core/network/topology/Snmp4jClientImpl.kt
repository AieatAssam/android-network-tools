package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.Target
import org.snmp4j.UserTarget
import org.snmp4j.mp.MPv3
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.security.SecurityLevel
import org.snmp4j.security.SecurityModels
import org.snmp4j.security.SecurityProtocols
import org.snmp4j.security.USM
import org.snmp4j.security.AuthHMAC192SHA256
import org.snmp4j.security.AuthHMAC384SHA512
import org.snmp4j.security.AuthMD5
import org.snmp4j.security.AuthSHA
import org.snmp4j.security.PrivAES128
import org.snmp4j.security.PrivAES192
import org.snmp4j.security.PrivAES256
import org.snmp4j.security.PrivDES
import org.snmp4j.smi.Null
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.ScopedPDU
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.DefaultPDUFactory
import org.snmp4j.util.TreeUtils
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * One SNMP4J transport/session for one topology discovery run.
 *
 * The repository creates this client once per flow and closes it in the flow's
 * use block. Keeping the transport here avoids opening a UDP socket for every
 * GET and WALK while still isolating credentials between discovery runs.
 */
class Snmp4jClientImpl(
    private val sessionParams: TopologyParams
) : SnmpClient {

    private val transport = DefaultUdpTransportMapping()
    private val snmp = Snmp(transport)
    private val targetCache = ConcurrentHashMap<String, Target<*>>()
    private val authoritativeEngineIdCache = ConcurrentHashMap<String, ByteArray>()
    private var closed = false

    init {
        if (sessionParams.snmpVersion == SnmpVersion.V3) {
            ensureSecurityProtocols()
            ensureUsmSecurityModel()
            val spec = UsmUserSpecFactory.from(sessionParams)
            snmp.getUSM().addUser(spec.toUsmUser())
        }
        transport.listen()
    }

    override suspend fun get(target: SnmpTarget, oid: String): String? =
        withContext(Dispatchers.IO) {
            check(!closed) { "SNMP client is closed" }
            val pdu = (if (sessionParams.snmpVersion == SnmpVersion.V3) ScopedPDU() else PDU()).apply {
                type = PDU.GET
                add(VariableBinding(OID(oid)))
            }
            val snmpTarget = try {
                targetFor(target)
            } catch (error: Exception) {
                throw SnmpRequestException.unresolved(target, error)
            }
            ensureAuthoritativeEngineId(target)
            val responseEvent = snmp.get(pdu, snmpTarget)
            val response = responseEvent.response
                ?: throw SnmpRequestException.noResponse(
                    target = target,
                    operation = "GET",
                    oid = oid,
                    timeoutMs = sessionParams.timeoutMs,
                    cause = responseEvent.error
                )
            if (response.errorStatus != PDU.noError) {
                throw SnmpRequestException.responseError(
                    target = target,
                    operation = "GET",
                    oid = oid,
                    status = response.errorStatusText,
                    index = response.errorIndex
                )
            }
            val variable = response.getVariable(OID(oid)) ?: return@withContext null
            if (variable is Null) null else variable.toString()
        }

    override suspend fun walk(target: SnmpTarget, oidPrefix: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            check(!closed) { "SNMP client is closed" }
            val results = linkedMapOf<String, String>()
            val treeUtils = TreeUtils(snmp, DefaultPDUFactory())
            val snmpTarget = try {
                targetFor(target)
            } catch (error: Exception) {
                throw SnmpRequestException.unresolved(target, error)
            }
            ensureAuthoritativeEngineId(target)
            val events = treeUtils.getSubtree(snmpTarget, OID(oidPrefix))
            results.putAll(collectWalkResults(events))
            results
        }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { snmp.close() }
    }

    private fun targetFor(target: SnmpTarget): Target<*> =
        targetCache.getOrPut("${target.ip}:${target.port}") { buildTarget(target) }

    /**
     * USM auth/privacy keys are localized against the responder's authoritative
     * engine ID. Discover it before the first authenticated request and retain
     * the result for the lifetime of this client.
     */
    private fun ensureAuthoritativeEngineId(target: SnmpTarget) {
        if (sessionParams.snmpVersion != SnmpVersion.V3) return

        val cacheKey = "${target.ip}:${target.port}"
        if (authoritativeEngineIdCache.containsKey(cacheKey)) return

        synchronized(authoritativeEngineIdCache) {
            if (authoritativeEngineIdCache.containsKey(cacheKey)) return

            val address = UdpAddress(InetAddress.getByName(target.ip), target.port)
            val engineId = snmp.discoverAuthoritativeEngineID(
                address,
                sessionParams.timeoutMs.toLong()
            )
            if (engineId != null) {
                authoritativeEngineIdCache[cacheKey] = engineId
            }
        }
    }

    private fun buildTarget(target: SnmpTarget): Target<*> {
        val params = sessionParams
        val address = UdpAddress(InetAddress.getByName(target.ip), target.port)
        return when (params.snmpVersion) {
            SnmpVersion.V1 -> CommunityTarget<UdpAddress>(address, OctetString(params.communityString)).apply {
                version = SnmpConstants.version1
                timeout = params.timeoutMs.toLong()
                retries = params.retries
            }
            SnmpVersion.V2C -> CommunityTarget<UdpAddress>(address, OctetString(params.communityString)).apply {
                version = SnmpConstants.version2c
                timeout = params.timeoutMs.toLong()
                retries = params.retries
            }
            SnmpVersion.V3 -> UserTarget<UdpAddress>().apply {
                this.address = address
                version = SnmpConstants.version3
                timeout = params.timeoutMs.toLong()
                retries = params.retries
                securityName = OctetString(params.v3Username ?: "")
                securityLevel = buildV3SecurityLevel(params)
            }
        }
    }

    private fun buildV3SecurityLevel(params: TopologyParams): Int = when {
        params.v3AuthProtocol != V3AuthProtocol.NONE && params.v3PrivProtocol != V3PrivProtocol.NONE ->
            SecurityLevel.AUTH_PRIV
        params.v3AuthProtocol != V3AuthProtocol.NONE -> SecurityLevel.AUTH_NOPRIV
        else -> SecurityLevel.NOAUTH_NOPRIV
    }

    private fun ensureSecurityProtocols() {
        SecurityProtocols.getInstance().apply {
            // SNMP4J's default protocol loader is disabled unless its
            // extensibility setting is enabled; register the USM protocols
            // explicitly so Android builds support the configured algorithms.
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
    }

    private fun ensureUsmSecurityModel() {
        synchronized(Companion) {
            if (!usmSecurityModelRegistered) {
                val usm = USM(
                    SecurityProtocols.getInstance(),
                    OctetString(MPv3.createLocalEngineID()),
                    0
                )
                SecurityModels.getInstance().addSecurityModel(usm)
                usmSecurityModelRegistered = true
            }
        }
    }

    companion object {
        @Volatile
        private var usmSecurityModelRegistered = false
    }
}

internal fun collectWalkResults(events: List<org.snmp4j.util.TreeEvent>?): Map<String, String> {
    val results = linkedMapOf<String, String>()
    for (event in events.orEmpty()) {
        if (event.isError) break
        event.variableBindings?.forEach { variableBinding ->
            val value = variableBinding.variable
            if (value !is Null) results[variableBinding.oid.toString()] = value.toString()
        }
    }
    return results
}
