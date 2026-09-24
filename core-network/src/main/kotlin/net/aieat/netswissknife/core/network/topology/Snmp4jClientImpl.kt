package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
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
import org.snmp4j.event.ResponseEvent
import org.snmp4j.event.ResponseListener
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.DefaultPDUFactory
import org.snmp4j.util.TreeUtils
import org.snmp4j.util.TreeEvent
import org.snmp4j.util.TreeListener
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newUdpSocket

/**
 * One SNMP4J transport/session for one topology discovery run.
 *
 * The repository creates this client once per flow and closes it in the flow's
 * use block. Keeping the transport here avoids opening a UDP socket for every
 * GET and WALK while still isolating credentials between discovery runs.
 */
class Snmp4jClientImpl(
    private val sessionParams: TopologyParams,
    private val binder: NetworkBinder = NoOpNetworkBinder,
) : SnmpClient {

    private val transport = createTransport()
    private val snmp = Snmp(transport)
    private val targetCache = ConcurrentHashMap<String, Target<*>>()
    private val authoritativeEngineIdCache = ConcurrentHashMap<String, ByteArray>()
    private val authoritativeEngineIdDiscoveryAttempts = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var closed = false

    internal val pendingAsyncRequestCount: Int get() = snmp.pendingAsyncRequestCount

    init {
        try {
            if (sessionParams.snmpVersion == SnmpVersion.V3) {
                ensureSecurityProtocols()
                ensureUsmSecurityModel()
                val spec = UsmUserSpecFactory.from(sessionParams)
                snmp.getUSM().addUser(spec.toUsmUser())
            }
            transport.listen()
        } catch (failure: Throwable) {
            closed = true
            try {
                snmp.close()
            } catch (closeFailure: Throwable) {
                if (closeFailure !== failure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    private fun createTransport(): DefaultUdpTransportMapping = try {
        if (binder.shouldBind(sessionParams.targetIp)) {
            BoundUdpTransportMapping(binder, sessionParams.targetIp)
        } else {
            DefaultUdpTransportMapping()
        }
    } catch (error: SecurityException) {
        throw LocalNetworkPermissionDeniedException(error)
    }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun get(target: SnmpTarget, oid: String): String? {
        val (snmpTarget, pdu) = withContext(Dispatchers.IO) {
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
            snmpTarget to pdu
        }

        return suspendCancellableCoroutine { continuation ->
            val requestLock = Any()
            var requestSubmitted = false
            val listener = object : ResponseListener {
                override fun <A : org.snmp4j.smi.Address> onResponse(event: ResponseEvent<A>) {
                    try {
                        val value = parseGetResponse(target, oid, event)
                        val token = continuation.tryResume(value) ?: return
                        continuation.completeResume(token)
                    } catch (error: Exception) {
                        val token = continuation.tryResumeWithException(error) ?: return
                        continuation.completeResume(token)
                    }
                }
            }

            continuation.invokeOnCancellation {
                synchronized(requestLock) {
                    if (requestSubmitted) snmp.cancel(pdu, listener)
                }
            }
            synchronized(requestLock) {
                if (!continuation.isActive) return@suspendCancellableCoroutine
                try {
                    snmp.get(pdu, snmpTarget, null, listener)
                    requestSubmitted = true
                } catch (error: Exception) {
                    val token = continuation.tryResumeWithException(error) ?: return@suspendCancellableCoroutine
                    continuation.completeResume(token)
                }
            }
        }
    }

    private fun parseGetResponse(
        target: SnmpTarget,
        oid: String,
        responseEvent: ResponseEvent<*>
    ): String? {
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
        val variable = response.getVariable(OID(oid)) ?: return null
        return if (variable is Null) null else variable.toString()
    }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun walk(
        target: SnmpTarget,
        oidPrefix: String,
        budget: SnmpWalkBudget
    ): SnmpWalkResult {
        val (snmpTarget, treeUtils) = withContext(Dispatchers.IO) {
            check(!closed) { "SNMP client is closed" }
            val treeUtils = TreeUtils(snmp, DefaultPDUFactory())
            treeUtils.maxRepetitions = budget.maxRepetitions
            val snmpTarget = try {
                targetFor(target)
            } catch (error: Exception) {
                throw SnmpRequestException.unresolved(target, error)
            }
            ensureAuthoritativeEngineId(target)
            snmpTarget to treeUtils
        }

        return suspendCancellableCoroutine { continuation ->
            val collector = BoundedSnmpWalkCollector(budget, oidPrefix)
            val listener = object : TreeListener {
                override fun next(event: TreeEvent): Boolean = collector.next(event)

                override fun finished(event: TreeEvent) {
                    collector.finished(event)
                    val token = continuation.tryResume(collector.result()) ?: return
                    continuation.completeResume(token)
                }

                override fun isFinished(): Boolean = collector.isFinished() || !continuation.isActive
            }

            try {
                treeUtils.getSubtree(snmpTarget, OID(oidPrefix), null, listener)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }
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
            // A missing agent can make SNMP4J return null. Remember the discovery attempt
            // too, or every GET/WALK repeats the same timeout for this target.
            if (!authoritativeEngineIdDiscoveryAttempts.add(cacheKey)) return

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

/** Uses an unbound replacement socket so the platform can select its network before bind(). */
private class BoundUdpTransportMapping(
    binder: NetworkBinder,
    destinationIp: String,
) : DefaultUdpTransportMapping() {
    init {
        socket.close()
        val networkSocket = binder.newUdpSocket(destinationIp, forceBind = true)
        try {
            networkSocket.bind(InetSocketAddress(0))
            socket = networkSocket
            udpAddress.port = networkSocket.localPort
        } catch (error: SecurityException) {
            networkSocket.close()
            throw LocalNetworkPermissionDeniedException(error)
        } catch (error: Exception) {
            networkSocket.close()
            throw error
        }
    }
}

internal class BoundedSnmpWalkCollector(
    private val budget: SnmpWalkBudget,
    private val rootOidPrefix: String? = null
) : TreeListener {
    private val results = linkedMapOf<String, String>()
    private val truncationReasons = mutableSetOf<TopologyTruncationReason>()
    private var walkBytes = 0
    private var responsePages = 0
    private var completed = false
    private var stoppedByLimit = false
    private var hadError = false

    override fun next(event: TreeEvent): Boolean = collectPage(event)

    private fun collectPage(event: TreeEvent): Boolean {
        if (event.variableBindings?.isNotEmpty() == true) {
            responsePages++
            if (responsePages > budget.maxPagesPerWalk) {
                truncationReasons += TopologyTruncationReason.WALK_PAGE_LIMIT
                stoppedByLimit = true
                completed = true
                return false
            }
        }
        return collect(event)
    }

    private fun collect(event: TreeEvent): Boolean {
        if (event.isError) {
            hadError = true
            return false
        }
        for (binding in event.variableBindings.orEmpty()) {
            if (binding.variable is Null) continue
            val oid = binding.oid.toString()
            if (!isWithinRoot(oid)) continue
            if (oid in results) continue
            val value = binding.variable.toString()
            when (val reservation = budget.tryReserve(oid, value, results.size, walkBytes)) {
                is SnmpWalkBudget.Reservation.Accepted -> {
                    results[oid] = value
                    walkBytes += reservation.bytes
                }
                is SnmpWalkBudget.Reservation.Rejected -> {
                    truncationReasons += reservation.reason
                    stoppedByLimit = true
                    completed = true
                    return false
                }
            }
        }
        return true
    }

    override fun finished(event: TreeEvent) {
        if (!stoppedByLimit) {
            if (event.variableBindings?.isNotEmpty() == true) {
                collectPage(event)
            } else {
                collect(event)
            }
        }
        completed = true
    }

    override fun isFinished(): Boolean = completed

    fun result(): SnmpWalkResult = SnmpWalkResult(
        entries = results.toMap(),
        truncationReasons = truncationReasons.toSet(),
        hadError = hadError
    )

    private fun isWithinRoot(oid: String): Boolean {
        val prefix = rootOidPrefix ?: return true
        return oid == prefix || oid.startsWith("$prefix.")
    }
}
