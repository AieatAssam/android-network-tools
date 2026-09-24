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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newUdpSocket
import net.aieat.netswissknife.core.network.operation.OperationDeadline
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException

fun interface TopologyHostnameResolver {
    fun resolve(hostname: String): InetAddress
}

fun interface AuthoritativeEngineIdDiscoverer {
    fun discover(snmp: Snmp, address: UdpAddress, timeoutMillis: Long): ByteArray?
}

fun interface TopologyTransportFactory {
    fun create(params: TopologyParams, binder: NetworkBinder): DefaultUdpTransportMapping
}

fun interface TopologyTransportStarter {
    fun start(transport: DefaultUdpTransportMapping)
}

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
    private val operationDeadline: OperationDeadline? = null,
    private val hostnameResolver: TopologyHostnameResolver = TopologyHostnameResolver(InetAddress::getByName),
    private val engineIdDiscoverer: AuthoritativeEngineIdDiscoverer =
        AuthoritativeEngineIdDiscoverer { client, address, timeout ->
            client.discoverAuthoritativeEngineID(address, timeout)
        },
    private val transportFactory: TopologyTransportFactory = TopologyTransportFactory { params, networkBinder ->
        createTopologyTransport(params, networkBinder)
    },
    private val transportStarter: TopologyTransportStarter = TopologyTransportStarter { it.listen() },
    private val deferInitialization: Boolean = false,
) : SnmpClient {

    @Volatile private var transport: DefaultUdpTransportMapping? = null
    @Volatile private var snmp: Snmp? = null
    @Volatile private var initialized = false
    @Volatile private var initializationStarted = false
    @Volatile private var initializationAborted = false
    @Volatile private var initializationFailure: Throwable? = null
    private val targetCache = ConcurrentHashMap<String, Target<*>>()
    private val authoritativeEngineIdCache = ConcurrentHashMap<String, ByteArray>()
    private val targetAddressCache = ConcurrentHashMap<String, InetAddress>()
    private val targetAddressFailures = ConcurrentHashMap<String, SnmpRequestException>()
    private val authoritativeEngineIdDiscoveries = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    @Volatile private var closed = false
    private val initializationLock = Any()
    private val initializationMutex = Mutex()

    internal val pendingAsyncRequestCount: Int get() = snmp?.pendingAsyncRequestCount ?: 0

    init {
        if (!deferInitialization) initializeBlocking()
    }

    private fun initializeBlocking() {
        check(!closed) { "SNMP client is closed" }
        val createdTransport = transportFactory.create(sessionParams, binder)
        val createdSnmp: Snmp
        synchronized(initializationLock) {
            if (closed || initializationAborted) {
                runCatching { createdTransport.close() }
                throw CancellationException("SNMP client is closed")
            }
            transport = createdTransport
            createdSnmp = Snmp(createdTransport)
            snmp = createdSnmp
        }
        try {
            if (sessionParams.snmpVersion == SnmpVersion.V3) {
                ensureSecurityProtocols()
                ensureUsmSecurityModel()
                val spec = UsmUserSpecFactory.from(sessionParams)
                createdSnmp.getUSM().addUser(spec.toUsmUser())
            }
            // The repository registers this client before deferred initialization starts, so
            // cancellation can close the mapping while listen/setup is blocked.
            transportStarter.start(createdTransport)
            val canPublish = synchronized(initializationLock) {
                if (closed || initializationAborted) {
                    false
                } else {
                    initialized = true
                    true
                }
            }
            if (!canPublish) {
                // listen() can recreate DefaultUdpTransportMapping.socket after the owner closed
                // it while startup was blocked. Close the raw mapping again after this late call;
                // Snmp.close() is not sufficient because it can be idempotent across that reopen.
                runCatching { createdTransport.close() }
                throw CancellationException("SNMP client initialization was cancelled")
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private suspend fun ensureInitialized() {
        initializationFailure?.let { throw it }
        if (initialized) return
        initializationMutex.withLock {
            initializationFailure?.let { throw it }
            if (initialized) return
            if (closed) throw CancellationException("SNMP client is closed")
            check(!initializationStarted) { "SNMP client initialization is already in progress" }
            initializationStarted = true
            try {
                if (operationDeadline == null) {
                    withContext(Dispatchers.IO) { initializeBlocking() }
                } else {
                    TopologyBlockingCall.run(
                        deadline = operationDeadline,
                        requestTimeoutMillis = sessionParams.timeoutMs.toLong(),
                        timeoutMessage = "SNMP transport initialization timed out after ${sessionParams.timeoutMs} ms",
                    ) { initializeBlocking() }
                }
            } catch (failure: Throwable) {
                // The worker may ignore interruption and return later. Make this attempt terminal
                // before releasing the mutex, then close its currently owned transport. Any
                // transport allocated after close is rejected and closed by initializeBlocking.
                synchronized(initializationLock) {
                    initializationAborted = true
                    initializationFailure = failure
                }
                close()
                throw failure
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun get(target: SnmpTarget, oid: String): String? {
        ensureInitialized()
        val activeSnmp = checkNotNull(snmp)
        val (snmpTarget, pdu) = withContext(Dispatchers.IO) {
            check(!closed) { "SNMP client is closed" }
            val pdu = (if (sessionParams.snmpVersion == SnmpVersion.V3) ScopedPDU() else PDU()).apply {
                type = PDU.GET
                add(VariableBinding(OID(oid)))
            }
            val (snmpTarget, address) = try {
                targetFor(target)
            } catch (deadline: OperationDeadlineExceededException) {
                throw deadline
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: SnmpRequestException) {
                throw failure
            } catch (error: Exception) {
                throw SnmpRequestException.unresolved(target, error)
            }
            ensureAuthoritativeEngineId(target, address)
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
                    if (requestSubmitted) activeSnmp.cancel(pdu, listener)
                }
            }
            synchronized(requestLock) {
                if (!continuation.isActive) return@suspendCancellableCoroutine
                try {
                    activeSnmp.get(pdu, snmpTarget, null, listener)
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
        ensureInitialized()
        val activeSnmp = checkNotNull(snmp)
        val (snmpTarget, treeUtils) = withContext(Dispatchers.IO) {
            check(!closed) { "SNMP client is closed" }
            val treeUtils = TreeUtils(activeSnmp, DefaultPDUFactory())
            treeUtils.maxRepetitions = budget.maxRepetitions
            val (snmpTarget, address) = try {
                targetFor(target)
            } catch (deadline: OperationDeadlineExceededException) {
                throw deadline
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: SnmpRequestException) {
                throw failure
            } catch (error: Exception) {
                throw SnmpRequestException.unresolved(target, error)
            }
            ensureAuthoritativeEngineId(target, address)
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
        val (currentSnmp, currentTransport) = synchronized(initializationLock) {
            if (closed) return
            closed = true
            snmp to transport
        }
        if (currentSnmp != null) runCatching { currentSnmp.close() }
        else runCatching { currentTransport?.close() }
    }

    private suspend fun targetFor(target: SnmpTarget): Pair<Target<*>, UdpAddress> {
        val key = "${target.ip}:${target.port}"
        val cachedAddress = targetAddressCache[key]
        val cachedTarget = targetCache[key]
        if (cachedAddress != null && cachedTarget != null) return cachedTarget to UdpAddress(cachedAddress, target.port)
        targetAddressFailures[key]?.let { throw it }
        val address = targetAddressCache[key] ?: run {
            val resolved = try {
                resolveHostname(target.ip)
            } catch (deadline: OperationDeadlineExceededException) {
                throw deadline
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                val typedFailure = SnmpRequestException.unresolved(target, failure)
                throw targetAddressFailures.putIfAbsent(key, typedFailure) ?: typedFailure
            }
            targetAddressCache.putIfAbsent(key, resolved) ?: resolved
        }
        targetCache[key]?.let { return it to UdpAddress(address, target.port) }
        val udpAddress = UdpAddress(address, target.port)
        val candidate = buildTarget(target, address)
        return (targetCache.putIfAbsent(key, candidate) ?: candidate) to udpAddress
    }

    private suspend fun resolveHostname(hostname: String): InetAddress {
        val deadline = operationDeadline ?: return hostnameResolver.resolve(hostname)
        return TopologyBlockingCall.run(
            deadline = deadline,
            requestTimeoutMillis = sessionParams.timeoutMs.toLong(),
            timeoutMessage = "Hostname resolution timed out after ${sessionParams.timeoutMs} ms",
        ) { hostnameResolver.resolve(hostname) }
    }

    /**
     * USM auth/privacy keys are localized against the responder's authoritative
     * engine ID. Discover it before the first authenticated request and retain
     * the result for the lifetime of this client.
     */
    private suspend fun ensureAuthoritativeEngineId(target: SnmpTarget, address: UdpAddress) {
        if (sessionParams.snmpVersion != SnmpVersion.V3) return

        val cacheKey = "${target.ip}:${target.port}"
        if (authoritativeEngineIdCache.containsKey(cacheKey)) return

        val discovery = CompletableDeferred<Unit>()
        val existingDiscovery = authoritativeEngineIdDiscoveries.putIfAbsent(cacheKey, discovery)
        if (existingDiscovery != null) {
            existingDiscovery.await()
            return
        }

        try {
            // A missing agent can make SNMP4J return null. Keep the completed attempt cached so
            // every GET/WALK does not repeat the same engine-ID discovery timeout.
            val engineId = if (operationDeadline == null) {
                engineIdDiscoverer.discover(checkNotNull(snmp), address, sessionParams.timeoutMs.toLong())
            } else {
                val remainingMillis = operationDeadline.remainingTimeoutMillis()
                val requestTimeoutMillis = minOf(sessionParams.timeoutMs.toLong(), remainingMillis)
                TopologyBlockingCall.run(
                    deadline = operationDeadline,
                    requestTimeoutMillis = requestTimeoutMillis,
                    timeoutMessage = "SNMPv3 authoritative engine ID discovery timed out",
                ) {
                    engineIdDiscoverer.discover(checkNotNull(snmp), address, requestTimeoutMillis)
                }
            }
            if (engineId != null) authoritativeEngineIdCache[cacheKey] = engineId
            discovery.complete(Unit)
        } catch (failure: Throwable) {
            if (failure is OperationDeadlineExceededException) {
                discovery.completeExceptionally(failure)
            } else {
                // Preserve the previous behavior: one failed discovery attempt is remembered,
                // but does not make every later GET/WALK fail without trying the normal request.
                discovery.complete(Unit)
            }
            throw failure
        }
    }

    private fun buildTarget(target: SnmpTarget, address: InetAddress): Target<*> {
        val params = sessionParams
        val udpAddress = UdpAddress(address, target.port)
        return when (params.snmpVersion) {
            SnmpVersion.V1 -> CommunityTarget<UdpAddress>(udpAddress, OctetString(params.communityString)).apply {
                version = SnmpConstants.version1
                timeout = params.timeoutMs.toLong()
                retries = params.retries
            }
            SnmpVersion.V2C -> CommunityTarget<UdpAddress>(udpAddress, OctetString(params.communityString)).apply {
                version = SnmpConstants.version2c
                timeout = params.timeoutMs.toLong()
                retries = params.retries
            }
            SnmpVersion.V3 -> UserTarget<UdpAddress>().apply {
                this.address = udpAddress
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

private fun createTopologyTransport(
    params: TopologyParams,
    networkBinder: NetworkBinder,
): DefaultUdpTransportMapping = try {
    if (networkBinder.shouldBind(params.targetIp)) {
        BoundUdpTransportMapping(networkBinder, params.targetIp)
    } else {
        DefaultUdpTransportMapping()
    }
} catch (error: SecurityException) {
    throw LocalNetworkPermissionDeniedException(error)
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
